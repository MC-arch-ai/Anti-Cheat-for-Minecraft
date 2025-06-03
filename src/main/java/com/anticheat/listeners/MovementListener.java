package com.anticheat.listeners;

import com.anticheat.AntiCheatPlugin;
import com.anticheat.utils.MovementNormalizer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MovementListener implements Listener {

    private final AntiCheatPlugin plugin;
    private final MovementNormalizer movementNormalizer;
    
    // Хранилища данных для различных проверок
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private final Map<UUID, Integer> nofallViolations = new HashMap<>();
    
    // Новые хранилища для улучшенных проверок
    private final Map<UUID, Double> playerFallDistance = new HashMap<>();
    private final Map<UUID, Boolean> isFalling = new HashMap<>();
    private final Map<UUID, Boolean> expectingFallDamage = new HashMap<>();
    private final Map<UUID, Double> lastYVelocity = new HashMap<>();
    private final Map<UUID, Boolean> playerSuspectedNoFall = new HashMap<>();
    
    // Хранилища для проверки Phase (прохождение сквозь блоки)
    private final Map<UUID, Long> lastPhaseCheck = new HashMap<>();
    private final Map<UUID, Integer> phaseViolations = new HashMap<>();
    
    public MovementListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.movementNormalizer = new MovementNormalizer(plugin);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        // Сбрасываем счетчики для корректной работы проверок при включении/выключении полета
        UUID uuid = event.getPlayer().getUniqueId();
        airTicks.remove(uuid);
        wasOnGround.remove(uuid);
        nofallViolations.remove(uuid);
        playerFallDistance.remove(uuid);
        isFalling.remove(uuid);
        expectingFallDamage.remove(uuid);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Сбрасываем данные после телепортации, чтобы избежать ложных срабатываний
        UUID uuid = event.getPlayer().getUniqueId();
        
        // Обновляем последнюю локацию
        lastLocation.put(uuid, event.getTo().clone());
        
        // Сбрасываем счетчики проверок
        lastPhaseCheck.remove(uuid);
        phaseViolations.put(uuid, 0);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        // Обрабатываем событие получения урона от падения
        if (event.isCancelled() || !(event.getEntity() instanceof Player) || 
            event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        UUID uuid = player.getUniqueId();
        
        if (plugin.isExempt(player)) {
            return;
        }
        
        // Проверяем, ожидался ли урон от падения
        if (expectingFallDamage.getOrDefault(uuid, false)) {
            // Игрок получил ожидаемый урон, всё нормально
            expectingFallDamage.put(uuid, false);
            playerSuspectedNoFall.put(uuid, false);
        }
        
        // Сбрасываем счетчики
        playerFallDistance.put(uuid, 0.0);
        isFalling.put(uuid, false);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (plugin.isExempt(player)) {
            return;
        }
        
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to == null) return;
        
        // Проверяем, изменилась ли позиция игрока существенно
        if (from.getBlockX() == to.getBlockX() && 
            from.getBlockY() == to.getBlockY() && 
            from.getBlockZ() == to.getBlockZ()) {
            return; // Игрок только повернулся, но не двигался
        }
        
        // Нормализуем скорость игрока, если это включено в настройках
        if (plugin.getConfigManager().getConfig().getBoolean("advanced.normalize-movement", false)) {
            movementNormalizer.normalizeVelocity(player);
        }
        
        // Исправляем экстремальные координаты, если это включено
        if (plugin.getConfigManager().getConfig().getBoolean("advanced.fix-extreme-velocity", false) && 
            lastLocation.containsKey(uuid)) {
            
            Location fixedLocation = movementNormalizer.fixExtremeCoordinates(player, to, lastLocation.get(uuid));
            if (fixedLocation != null) {
                // Телепортируем игрока на исправленную позицию
                event.setTo(fixedLocation);
                to = fixedLocation; // Обновляем 'to' для последующих проверок
            }
        }
        
        // Если игрок в креативе или в режиме полета, пропускаем проверки движения
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR ||
                player.isFlying() || player.getAllowFlight()) {
            
            // Обновляем последнюю локацию даже для креативных игроков
            lastLocation.put(uuid, to.clone());
            return;
        }
        
        // Получаем текущее время для расчета дельты
        long currentTime = System.currentTimeMillis();
        long lastTime = lastMoveTime.getOrDefault(uuid, currentTime);
        double timeDelta = (currentTime - lastTime) / 1000.0; // в секундах
        
        // Обновляем время последнего перемещения
        lastMoveTime.put(uuid, currentTime);
        
        // Проверка на превышение скорости
        checkSpeed(player, from, to, timeDelta);
        
        // Проверка на полет
        checkFly(player, from, to);
        
        // Расширенная проверка на NoFall (отсутствие урона от падения)
        checkNoFallEnhanced(player, from, to);
        
        // Проверка на хождение по воде (Jesus)
        checkJesus(player, to);
        
        // Проверка на прохождение сквозь блоки (Phase)
        checkPhase(player, from, to);
        
        // Сохраняем локацию для следующей проверки
        lastLocation.put(uuid, to.clone());
        
        // Сохраняем Y-скорость для проверки падения
        double yVelocity = to.getY() - from.getY();
        lastYVelocity.put(uuid, yVelocity);
    }
    
    /**
     * Проверка на превышение скорости
     */
    private void checkSpeed(Player player, Location from, Location to, double timeDelta) {
        // Пропускаем проверку, если времени прошло слишком мало или слишком много
        if (timeDelta < 0.01 || timeDelta > 1.0) {
            return;
        }
        
        // Рассчитываем горизонтальную дистанцию (только X и Z)
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        // Скорость в блоках в секунду
        double speed = horizontalDistance / timeDelta;
        
        // Базовая максимальная скорость ходьбы в Minecraft примерно 4.3 блока/сек
        // Скорость бега примерно 5.6 блока/сек
        // Модификаторы скорости от зелий и других эффектов
        double speedModifier = 1.0;
        
        // Учитываем эффект скорости
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int level = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            speedModifier += level * 0.2; // +20% скорости на каждый уровень
        }
        
        // Учитываем блоки под игроком, которые могут увеличить скорость (лед, душевный песок и т.д.)
        Block blockUnder = player.getLocation().subtract(0, 0.1, 0).getBlock();
        Material blockType = blockUnder.getType();
        
        if (blockType == Material.ICE || blockType == Material.PACKED_ICE || blockType == Material.BLUE_ICE) {
            speedModifier += 0.3; // Увеличиваем допустимую скорость на льду
        } else if (blockType == Material.SOUL_SAND) {
            speedModifier -= 0.3; // Уменьшаем на душевном песке
        }
        
        // Дополнительные проверки на внешние факторы скорости
        if (player.isGliding()) { // Полет на элитрах
            speedModifier += 1.0;
        }
        
        if (player.hasPermission("customspeed.bypass")) { // Учитываем возможные права на скорость
            speedModifier += 0.3;
        }
        
        // Максимальная разрешенная скорость из конфига
        double maxSpeed = plugin.getConfigManager().getCheckValue("movement.speed", "max-speed");
        if (maxSpeed <= 0) maxSpeed = 0.8; // Значение по умолчанию
        
        // Фактическая максимальная разрешенная скорость с учетом модификаторов
        double allowedSpeed = 5.6 * speedModifier * maxSpeed;
        
        // Если скорость превышает допустимую
        if (speed > allowedSpeed) {
            // Проверяем, нет ли других объяснений скорости (например, баунс от слайма)
            boolean nearBouncyBlock = false;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 0; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block block = player.getLocation().clone().add(x, y, z).getBlock();
                        if (block.getType() == Material.SLIME_BLOCK) {
                            nearBouncyBlock = true;
                            break;
                        }
                    }
                    if (nearBouncyBlock) break;
                }
                if (nearBouncyBlock) break;
            }
            
            if (!nearBouncyBlock) {
                String details = String.format("Speed: %.2f (allowed: %.2f)", speed, allowedSpeed);
                int vlToAdd = (int) Math.ceil((speed - allowedSpeed) * 2); // Больше превышение - больше VL
                
                plugin.getViolationManager().addViolation(player, "movement.speed", vlToAdd, details);
            }
        }
    }
    
    /**
     * Проверка на полет
     */
    private void checkFly(Player player, Location from, Location to) {
        UUID uuid = player.getUniqueId();
        
        // Пропускаем проверку, если игрок в воде или лаве
        if (player.isSwimming() || to.getBlock().isLiquid() || from.getBlock().isLiquid()) {
            return;
        }
        
        // Считаем тики в воздухе
        boolean isOnGround = player.isOnGround();
        boolean wasOnGround = this.wasOnGround.getOrDefault(uuid, true);
        
        // Обновляем счетчик тиков в воздухе
        int currentAirTicks = airTicks.getOrDefault(uuid, 0);
        
        if (!isOnGround) {
            currentAirTicks++;
        } else {
            currentAirTicks = 0;
        }
        
        airTicks.put(uuid, currentAirTicks);
        this.wasOnGround.put(uuid, isOnGround);
        
        // Проверка на нестандартное поведение в воздухе
        if (currentAirTicks > 10) { // Если игрок в воздухе более 10 тиков
            double dy = to.getY() - from.getY();
            
            // Проверяем наличие эффектов замедления падения
            boolean hasSlowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
            
            // Если игрок двигается вверх в воздухе (не падает)
            if (dy > 0 && !player.hasPotionEffect(PotionEffectType.JUMP)) {
                String details = String.format("Flying up: %.2f blocks", dy);
                plugin.getViolationManager().addViolation(player, "movement.fly", 3, details);
            } 
            // Если игрок слишком долго в воздухе и не падает с нормальной скоростью
            else if (currentAirTicks > 40 && Math.abs(dy) < 0.1 && !hasSlowFalling) {
                String details = "Hovering in air: " + currentAirTicks + " ticks";
                plugin.getViolationManager().addViolation(player, "movement.fly", 2, details);
            }
            
            // Проверка на странные колебания в воздухе (признак fly чита)
            if (currentAirTicks > 20) {
                double lastVelocity = lastYVelocity.getOrDefault(uuid, 0.0);
                
                // Если направление движения по Y изменилось на противоположное несколько раз
                if (Math.signum(dy) != 0 && Math.signum(lastVelocity) != 0 && 
                    Math.signum(dy) != Math.signum(lastVelocity) && 
                    Math.abs(dy) > 0.03) {
                    
                    String details = String.format("Y-velocity change: %.2f to %.2f", lastVelocity, dy);
                    plugin.getViolationManager().addViolation(player, "movement.fly", 2, details);
                }
            }
        }
    }
    
    /**
     * Расширенная проверка на NoFall (отсутствие урона от падения)
     */
    private void checkNoFallEnhanced(Player player, Location from, Location to) {
        UUID uuid = player.getUniqueId();
        
        // Получаем предыдущее состояние "на земле"
        boolean wasOnGround = this.wasOnGround.getOrDefault(uuid, true);
        boolean isOnGround = player.isOnGround();
        
        // Рассчитываем изменение по вертикали
        double dy = to.getY() - from.getY();
        
        // Отслеживаем фактическое падение
        double fallDistance = playerFallDistance.getOrDefault(uuid, 0.0);
        boolean falling = isFalling.getOrDefault(uuid, false);
        
        // Если игрок падает (движение вниз)
        if (dy < 0) {
            if (!falling) {
                falling = true;
                isFalling.put(uuid, true);
            }
            
            // Накапливаем расстояние падения только если игрок действительно падает
            if (falling && !isOnGround) {
                fallDistance += Math.abs(dy);
                playerFallDistance.put(uuid, fallDistance);
            }
            
            // Если падение достаточно большое, ожидаем урон
            if (fallDistance > 3.0) {
                expectingFallDamage.put(uuid, true);
            }
        } else {
            // Игрок движется вверх, сбрасываем падение
            if (isOnGround) {
                fallDistance = 0.0;
                falling = false;
                playerFallDistance.put(uuid, fallDistance);
                isFalling.put(uuid, false);
            }
        }
        
        // Проверка на nofall (игрок падает и внезапно оказывается на земле без повреждений)
        if (player.getFallDistance() > 3.0 && !isOnGround && !wasOnGround) {
            // Запоминаем текущую высоту падения
            playerFallDistance.put(uuid, (double) player.getFallDistance());
            
            // Отмечаем, что игрок потенциально может применить NoFall
            playerSuspectedNoFall.put(uuid, true);
            expectingFallDamage.put(uuid, true);
            
            // Запускаем отложенную проверку на получение урона от падения
            final double finalFallDistance = player.getFallDistance();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Проверяем, получил ли игрок урон от падения
                if (playerSuspectedNoFall.getOrDefault(uuid, false) && 
                    expectingFallDamage.getOrDefault(uuid, false)) {
                    
                    // Проверяем, был ли игрок защищен от падения
                    boolean hasProtection = player.hasPotionEffect(PotionEffectType.SLOW_FALLING) || 
                                           player.hasPotionEffect(PotionEffectType.JUMP);
                    
                    if (!hasProtection) {
                        String details = "Fall distance: " + String.format("%.1f", finalFallDistance);
                        plugin.getViolationManager().addViolation(player, "movement.nofall", 2, details);
                        
                        // Увеличиваем счетчик нарушений NoFall
                        int violations = nofallViolations.getOrDefault(uuid, 0) + 1;
                        nofallViolations.put(uuid, violations);
                        
                        // Если много нарушений подряд, увеличиваем VL
                        if (violations > 3) {
                            plugin.getViolationManager().addViolation(player, "movement.nofall", 3, 
                                "Multiple NoFall detections: " + violations);
                        }
                    }
                    
                    // Сбрасываем ожидание
                    expectingFallDamage.put(uuid, false);
                    playerSuspectedNoFall.put(uuid, false);
                    playerFallDistance.put(uuid, 0.0);
                }
            }, 10L); // Проверяем через 10 тиков (0.5 секунды)
        }
        
        // Проверка на мгновенное обнуление fallDistance
        if (player.getFallDistance() < fallDistance - 1.0 && fallDistance > 3.0 && !isOnGround) {
            String details = "Client fall distance reset: " + String.format("%.1f -> %.1f", 
                    fallDistance, player.getFallDistance());
            plugin.getViolationManager().addViolation(player, "movement.nofall", 3, details);
        }
        
        // Проверка на неконсистентность с клиентским fallDistance
        if (Math.abs(player.getFallDistance() - fallDistance) > 2.0 && fallDistance > 3.0 && !isOnGround) {
            String details = "Fall distance mismatch: " + String.format("%.1f (server) vs %.1f (client)", 
                    fallDistance, player.getFallDistance());
            plugin.getViolationManager().addViolation(player, "movement.nofall", 1, details);
        }
    }
    
    /**
     * Проверка на хождение по воде (Jesus)
     */
    private void checkJesus(Player player, Location location) {
        // Проверяем, находится ли игрок на воде
        Block block = location.getBlock();
        Block blockBelow = location.subtract(0, 0.1, 0).getBlock();
        
        // Если игрок стоит на воде (не в воде), но не в лодке и не плавает
        if ((block.getType() == Material.WATER || blockBelow.getType() == Material.WATER) && 
                player.getVehicle() == null && !player.isSwimming()) {
            
            // Проверяем, действительно ли игрок на поверхности воды
            boolean onWaterSurface = true;
            
            // Проверяем блоки вокруг игрока
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue; // Пропускаем текущий блок
                    
                    Block adjacent = location.clone().add(x, 0, z).getBlock();
                    if (adjacent.getType().isSolid()) {
                        onWaterSurface = false;
                        break;
                    }
                }
                if (!onWaterSurface) break;
            }
            
            if (onWaterSurface) {
                // Проверяем, имеет ли игрок зелье хождения по воде или другой легитимный способ
                boolean hasWaterWalking = player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE) || 
                                          player.getInventory().getBoots() != null && 
                                          player.getInventory().getBoots().getItemMeta() != null && 
                                          player.getInventory().getBoots().getItemMeta().hasEnchant(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER);
                
                if (!hasWaterWalking) {
                    plugin.getViolationManager().addViolation(player, "movement.jesus", 1, "Walking on water");
                }
            }
        }
    }
    
    /**
     * Проверка на прохождение сквозь блоки (Phase)
     */
    private void checkPhase(Player player, Location from, Location to) {
        if (!plugin.getConfigManager().getConfig().getBoolean("checks.movement.phase.enabled", true)) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        
        // Проверяем не слишком часто, чтобы снизить нагрузку
        long currentTime = System.currentTimeMillis();
        long lastCheck = lastPhaseCheck.getOrDefault(uuid, 0L);
        
        if (currentTime - lastCheck < 500) { // Проверяем не чаще чем раз в 500 мс
            return;
        }
        
        lastPhaseCheck.put(uuid, currentTime);
        
        // Если игрок телепортировался или переместился между мирами, пропускаем проверку
        if (from.getWorld() != to.getWorld() || from.distance(to) > 10) {
            return;
        }
        
        // Строим линию между старой и новой позицией
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        
        // Если расстояние слишком маленькое, пропускаем проверку
        if (distance < 0.3) {
            return;
        }
        
        // Нормализуем вектор направления
        direction.normalize();
        
        // Проверяем блоки на пути движения
        boolean passedThroughBlock = false;
        double step = 0.2; // Размер шага для проверки
        
        for (double d = 0; d < distance; d += step) {
            // Вычисляем промежуточную точку
            Vector pointVector = from.toVector().add(direction.clone().multiply(d));
            Location point = new Location(from.getWorld(), pointVector.getX(), pointVector.getY(), pointVector.getZ());
            
            // Проверяем, является ли блок в этой точке твердым
            Block block = point.getBlock();
            
            // Если блок твердый и не является дверью/воротами/etc
            if (block.getType().isSolid() && !isPassableBlock(block.getType())) {
                // Проверяем, что игрок не находится внутри этого блока
                double playerHeight = 1.8; // Стандартная высота игрока
                boolean playerInside = false;
                
                // Проверяем хитбокс игрока
                for (double h = 0; h < playerHeight; h += 0.5) {
                    Location playerPoint = point.clone().add(0, h, 0);
                    if (playerPoint.getBlock() == block) {
                        playerInside = true;
                        break;
                    }
                }
                
                if (!playerInside) {
                    passedThroughBlock = true;
                    break;
                }
            }
        }
        
        // Если игрок прошел сквозь твердый блок
        if (passedThroughBlock) {
            int violations = phaseViolations.getOrDefault(uuid, 0) + 1;
            phaseViolations.put(uuid, violations);
            
            // Если несколько нарушений подряд, увеличиваем VL
            if (violations >= 2) {
                String details = String.format("Passed through block, distance: %.2f", distance);
                plugin.getViolationManager().addViolation(player, "movement.phase", 
                        Math.min(violations, 5), details);
                
                // Телепортируем игрока обратно, чтобы предотвратить использование чита
                player.teleport(from);
            }
        } else {
            // Постепенно уменьшаем количество нарушений
            int violations = phaseViolations.getOrDefault(uuid, 0);
            if (violations > 0) {
                phaseViolations.put(uuid, violations - 1);
            }
        }
    }
    
    /**
     * Проверка, является ли блок проходимым (двери, ворота, плиты и т.д.)
     */
    private boolean isPassableBlock(Material material) {
        return material.name().contains("DOOR") || 
               material.name().contains("GATE") || 
               material.name().contains("FENCE") || 
               material.name().contains("SIGN") || 
               material.name().contains("BUTTON") || 
               material.name().contains("PRESSURE") || 
               material.name().contains("SLAB") || 
               material.name().contains("STAIRS") || 
               material.name().contains("CARPET") || 
               material == Material.AIR || 
               material == Material.WATER || 
               material == Material.LAVA;
    }
} 