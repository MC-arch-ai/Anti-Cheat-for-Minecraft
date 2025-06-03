package com.anticheat.listeners;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final AntiCheatPlugin plugin;
    
    // Хранилища данных для различных проверок
    private final Map<UUID, Long> lastBlockBreakTime = new HashMap<>();
    private final Map<UUID, Long> lastBlockPlaceTime = new HashMap<>();
    private final Map<UUID, Integer> blockPlaceCount = new HashMap<>();
    private final Map<UUID, Long> blockPlaceCountTime = new HashMap<>();
    private final Map<UUID, Block> lastBrokenBlock = new HashMap<>();
    
    public PlayerListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // При входе игрока, проверяем, нужно ли включать оповещения
        if (player.hasPermission("anticheat.alerts") && player.hasPermission("anticheat.admin")) {
            plugin.getAlertManager().setAlerts(player, true);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // При выходе игрока очищаем данные о нем
        lastBlockBreakTime.remove(uuid);
        lastBlockPlaceTime.remove(uuid);
        blockPlaceCount.remove(uuid);
        blockPlaceCountTime.remove(uuid);
        lastBrokenBlock.remove(uuid);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Block block = event.getBlock();
        
        if (plugin.isExempt(player) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        // Проверка на FastBreak (быстрое разрушение блоков)
        checkFastBreak(player, block);
        
        // Проверка на Nuker (разрушение нескольких блоков одновременно)
        checkNuker(player, block);
        
        // Сохраняем данные для следующих проверок
        lastBlockBreakTime.put(uuid, System.currentTimeMillis());
        lastBrokenBlock.put(uuid, block);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (plugin.isExempt(player) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        // Проверка на FastPlace (быстрая установка блоков)
        checkFastPlace(player);
        
        // Проверка на Scaffold (моментальная установка блоков под собой)
        checkScaffold(player, event.getBlock(), event.getBlockAgainst());
        
        // Сохраняем время последней установки блока
        lastBlockPlaceTime.put(uuid, System.currentTimeMillis());
    }
    
    /**
     * Проверка на FastBreak (быстрое разрушение блоков)
     */
    private void checkFastBreak(Player player, Block block) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastBreakTime = lastBlockBreakTime.getOrDefault(uuid, 0L);
        
        // Время, прошедшее с момента последнего разрушения блока
        long timeDifference = currentTime - lastBreakTime;
        
        // Базовое минимальное время разрушения блока (в мс)
        long baseBreakTime = 250; // Минимальное время для самых быстрых блоков (например, листья с ножницами)
        
        // Минимальное ожидаемое время для разрушения данного типа блока
        long expectedBreakTime = getExpectedBreakTime(player, block);
        
        // Если время разрушения слишком маленькое
        if (lastBreakTime > 0 && timeDifference < expectedBreakTime) {
            String details = String.format("Time: %dms (expected: %dms)", timeDifference, expectedBreakTime);
            int vlToAdd = (int) Math.ceil((expectedBreakTime - timeDifference) / 50.0); // 1 VL за каждые 50мс ниже ожидаемого
            
            plugin.getViolationManager().addViolation(player, "player.fastbreak", vlToAdd, details);
        }
    }
    
    /**
     * Получить ожидаемое время разрушения блока
     */
    private long getExpectedBreakTime(Player player, Block block) {
        Material blockType = block.getType();
        Material toolType = player.getInventory().getItemInMainHand().getType();
        
        // Базовое время разрушения различных типов блоков (в мс)
        long baseTime;
        
        // Здесь упрощенная логика, в реальном плагине нужно учитывать больше параметров
        if (blockType == Material.DIRT || blockType == Material.GRASS_BLOCK || blockType == Material.SAND || 
                blockType == Material.GRAVEL) {
            baseTime = 300; // Быстрые блоки
        } else if (blockType == Material.STONE || blockType == Material.COBBLESTONE) {
            baseTime = 1000; // Средние блоки
        } else if (blockType == Material.OBSIDIAN) {
            baseTime = 8000; // Медленные блоки
        } else {
            baseTime = 500; // Среднее значение для других блоков
        }
        
        // Учитываем инструмент
        double toolMultiplier = 1.0;
        
        // Проверяем соответствие инструмента блоку
        if (isEffectiveTool(toolType, blockType)) {
            toolMultiplier = 0.5; // В два раза быстрее с подходящим инструментом
            
            // Проверяем зачарования (упрощенно)
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DIG_SPEED) > 0) {
                int efficiencyLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DIG_SPEED);
                toolMultiplier -= efficiencyLevel * 0.1; // -10% за каждый уровень эффективности
            }
        }
        
        // Учитываем эффекты зелий
        if (player.hasPotionEffect(PotionEffectType.FAST_DIGGING)) {
            int hasteLevel = player.getPotionEffect(PotionEffectType.FAST_DIGGING).getAmplifier() + 1;
            toolMultiplier -= hasteLevel * 0.2; // -20% за каждый уровень спешки
        }
        
        if (player.hasPotionEffect(PotionEffectType.SLOW_DIGGING)) {
            int miningFatigueLevel = player.getPotionEffect(PotionEffectType.SLOW_DIGGING).getAmplifier() + 1;
            toolMultiplier += miningFatigueLevel * 0.3; // +30% за каждый уровень усталости
        }
        
        // Минимальный множитель
        toolMultiplier = Math.max(0.1, toolMultiplier);
        
        return (long) (baseTime * toolMultiplier);
    }
    
    /**
     * Проверить, является ли инструмент эффективным для данного блока
     */
    private boolean isEffectiveTool(Material tool, Material block) {
        // Упрощенная проверка, в реальном плагине нужно больше условий
        
        // Деревянные, каменные, железные, золотые, алмазные и незеритовые кирки
        if ((tool == Material.WOODEN_PICKAXE || tool == Material.STONE_PICKAXE || 
                tool == Material.IRON_PICKAXE || tool == Material.GOLDEN_PICKAXE || 
                tool == Material.DIAMOND_PICKAXE || tool == Material.NETHERITE_PICKAXE) && 
                (block == Material.STONE || block == Material.COBBLESTONE || block == Material.IRON_ORE || 
                block == Material.GOLD_ORE || block == Material.COAL_ORE || block == Material.DIAMOND_ORE || 
                block == Material.EMERALD_ORE || block == Material.REDSTONE_ORE || block == Material.LAPIS_ORE)) {
            return true;
        }
        
        // Деревянные, каменные, железные, золотые, алмазные и незеритовые лопаты
        if ((tool == Material.WOODEN_SHOVEL || tool == Material.STONE_SHOVEL || 
                tool == Material.IRON_SHOVEL || tool == Material.GOLDEN_SHOVEL || 
                tool == Material.DIAMOND_SHOVEL || tool == Material.NETHERITE_SHOVEL) && 
                (block == Material.DIRT || block == Material.GRASS_BLOCK || block == Material.SAND || 
                block == Material.GRAVEL || block == Material.CLAY || block == Material.SOUL_SAND)) {
            return true;
        }
        
        // Деревянные, каменные, железные, золотые, алмазные и незеритовые топоры
        if ((tool == Material.WOODEN_AXE || tool == Material.STONE_AXE || 
                tool == Material.IRON_AXE || tool == Material.GOLDEN_AXE || 
                tool == Material.DIAMOND_AXE || tool == Material.NETHERITE_AXE) && 
                (block == Material.OAK_LOG || block == Material.SPRUCE_LOG || block == Material.BIRCH_LOG || 
                block == Material.JUNGLE_LOG || block == Material.ACACIA_LOG || block == Material.DARK_OAK_LOG || 
                block == Material.OAK_PLANKS || block == Material.SPRUCE_PLANKS || block == Material.BIRCH_PLANKS || 
                block == Material.JUNGLE_PLANKS || block == Material.ACACIA_PLANKS || block == Material.DARK_OAK_PLANKS)) {
            return true;
        }
        
        // Ножницы
        if (tool == Material.SHEARS && 
                (block == Material.OAK_LEAVES || block == Material.SPRUCE_LEAVES || block == Material.BIRCH_LEAVES || 
                block == Material.JUNGLE_LEAVES || block == Material.ACACIA_LEAVES || block == Material.DARK_OAK_LEAVES || 
                block == Material.COBWEB || block == Material.VINE)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Проверка на Nuker (разрушение нескольких блоков одновременно)
     */
    private void checkNuker(Player player, Block block) {
        UUID uuid = player.getUniqueId();
        
        // Если есть предыдущий разрушенный блок
        if (lastBrokenBlock.containsKey(uuid)) {
            Block lastBlock = lastBrokenBlock.get(uuid);
            long currentTime = System.currentTimeMillis();
            long lastTime = lastBlockBreakTime.getOrDefault(uuid, 0L);
            
            // Если время между разрушениями слишком маленькое (менее 50 мс)
            if (lastTime > 0 && (currentTime - lastTime) < 50) {
                // И блоки не соседние (расстояние больше корня из 3)
                double distance = block.getLocation().distance(lastBlock.getLocation());
                
                if (distance > 1.8) { // Примерно расстояние для блоков по диагонали
                    String details = String.format("Distance: %.2f, Time: %dms", distance, (currentTime - lastTime));
                    plugin.getViolationManager().addViolation(player, "player.nuker", 3, details);
                }
            }
        }
    }
    
    /**
     * Проверка на FastPlace (быстрая установка блоков)
     */
    private void checkFastPlace(Player player) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Обновляем счетчик установленных блоков
        int placeCount = blockPlaceCount.getOrDefault(uuid, 0) + 1;
        long countStartTime = blockPlaceCountTime.getOrDefault(uuid, currentTime);
        
        // Если прошла секунда, проверяем количество установленных блоков
        if (currentTime - countStartTime >= 1000) {
            // Проверяем максимально допустимое количество блоков в секунду
            double maxBlocksPerSecond = plugin.getConfigManager().getCheckValue("player.fastplace", "max-blocks-per-second");
            if (maxBlocksPerSecond <= 0) maxBlocksPerSecond = 14; // Значение по умолчанию
            
            if (placeCount > maxBlocksPerSecond) {
                String details = "Blocks: " + placeCount + " (max: " + (int) maxBlocksPerSecond + ")";
                int vlToAdd = (int) Math.ceil((placeCount - maxBlocksPerSecond) / 2.0); // 1 VL за каждые 2 блока сверх лимита
                
                plugin.getViolationManager().addViolation(player, "player.fastplace", vlToAdd, details);
            }
            
            // Сбрасываем счетчик
            placeCount = 1;
            blockPlaceCountTime.put(uuid, currentTime);
        }
        
        blockPlaceCount.put(uuid, placeCount);
        
        // Проверка времени между установками блоков
        long lastTime = lastBlockPlaceTime.getOrDefault(uuid, 0L);
        
        if (lastTime > 0) {
            long timeDifference = currentTime - lastTime;
            
            // Минимальное время между установками блоков (примерно 100 мс)
            if (timeDifference < 100) {
                String details = "Time: " + timeDifference + "ms (min: 100ms)";
                plugin.getViolationManager().addViolation(player, "player.fastplace", 1, details);
            }
        }
    }
    
    /**
     * Проверка на Scaffold (быстрая установка блоков под ногами)
     */
    private void checkScaffold(Player player, Block placedBlock, Block againstBlock) {
        // Вектор направления взгляда игрока
        double pitch = Math.toRadians(player.getLocation().getPitch());
        
        // Проверяем, смотрит ли игрок вниз (pitch > 40 градусов)
        if (pitch < Math.toRadians(40)) {
            return;
        }
        
        // Проверяем, находится ли установленный блок прямо под игроком
        Location playerLoc = player.getLocation();
        Location blockLoc = placedBlock.getLocation();
        
        double xDiff = Math.abs(playerLoc.getX() - (blockLoc.getX() + 0.5));
        double zDiff = Math.abs(playerLoc.getZ() - (blockLoc.getZ() + 0.5));
        
        // Если блок примерно под игроком (в радиусе 0.6 блока)
        if (xDiff <= 0.6 && zDiff <= 0.6 && 
                blockLoc.getY() < playerLoc.getY() && 
                blockLoc.getY() >= playerLoc.getY() - 2) {
            
            // Проверяем, был ли блок размещен "в воздухе" (без примыкания к другим блокам по бокам)
            boolean hasAdjacentBlock = false;
            
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue; // Пропускаем текущий блок
                    
                    Block adjacent = placedBlock.getLocation().clone().add(x, 0, z).getBlock();
                    if (adjacent.getType().isSolid() && adjacent != againstBlock) {
                        hasAdjacentBlock = true;
                        break;
                    }
                }
                if (hasAdjacentBlock) break;
            }
            
            // Проверяем время с момента последней установки блока
            long currentTime = System.currentTimeMillis();
            long lastTime = lastBlockPlaceTime.getOrDefault(player.getUniqueId(), 0L);
            long timeDifference = currentTime - lastTime;
            
            // Если установка была быстрой и без прилегающих блоков (кроме блока, к которому прикрепили)
            if (timeDifference < 200 && !hasAdjacentBlock) {
                String details = "Fast bridge: " + timeDifference + "ms";
                plugin.getViolationManager().addViolation(player, "world.scaffold", 2, details);
            }
        }
    }
} 