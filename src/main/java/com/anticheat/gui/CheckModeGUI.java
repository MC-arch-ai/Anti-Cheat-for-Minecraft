package com.anticheat.gui;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Класс для GUI с режимами проверки игроков
 */
public class CheckModeGUI {

    private final AntiCheatPlugin plugin;
    private static final String INVENTORY_TITLE = ChatColor.RED + "AntiCheat " + ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + "Режимы проверки";
    private static final String PLAYER_MODE_INVENTORY_TITLE = ChatColor.RED + "AntiCheat " + ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + "Режим для: ";
    
    // Режимы проверки
    public enum CheckMode {
        HARD(ChatColor.RED + "HARD", Material.RED_CONCRETE, "Максимальная проверка", "Все проверки включены с", "высокой чувствительностью"),
        MEDIUM(ChatColor.GOLD + "MEDIUM", Material.ORANGE_CONCRETE, "Стандартная проверка", "Баланс между обнаружением", "и ложными срабатываниями"),
        LITE(ChatColor.YELLOW + "LITE", Material.YELLOW_CONCRETE, "Облегченная проверка", "Только основные проверки", "с пониженной чувствительностью"),
        OFF(ChatColor.GRAY + "OFF", Material.GRAY_CONCRETE, "Проверка отключена", "Игрок не будет проверяться", "на использование читов");
        
        private final String displayName;
        private final Material material;
        private final String[] description;
        
        CheckMode(String displayName, Material material, String... description) {
            this.displayName = displayName;
            this.material = material;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public Material getMaterial() {
            return material;
        }
        
        public String[] getDescription() {
            return description;
        }
    }
    
    // Хранение режимов проверки игроков - используем ConcurrentHashMap для безопасности в многопоточной среде
    private final Map<UUID, CheckMode> playerModes = new ConcurrentHashMap<>();
    
    public CheckModeGUI(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        // По умолчанию все игроки в режиме MEDIUM
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerModes.put(player.getUniqueId(), CheckMode.MEDIUM);
        }
    }
    
    /**
     * Открыть главное GUI с выбором игроков
     * @param player Игрок, который открывает GUI
     */
    public void openMainGUI(Player player) {
        int playerCount = Bukkit.getOnlinePlayers().size();
        int rows = Math.min(6, (playerCount + 8) / 9 + 1); // Максимум 6 рядов
        
        Inventory inventory = Bukkit.createInventory(null, rows * 9, INVENTORY_TITLE);
        
        // Добавляем головы всех онлайн игроков
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            ItemStack head = createPlayerHead(target);
            inventory.setItem(slot++, head);
        }
        
        player.openInventory(inventory);
    }
    
    /**
     * Открыть GUI с выбором режима для конкретного игрока
     * @param player Игрок, который открывает GUI
     * @param target Игрок, для которого выбирается режим
     */
    public void openPlayerModeGUI(Player player, Player target) {
        Inventory inventory = Bukkit.createInventory(null, 9, PLAYER_MODE_INVENTORY_TITLE + target.getName());
        
        // Добавляем все режимы проверки
        int slot = 2;
        for (CheckMode mode : CheckMode.values()) {
            ItemStack modeItem = createModeItem(mode);
            
            // Подсвечиваем текущий режим
            CheckMode currentMode = getPlayerMode(target);
            if (mode == currentMode) {
                ItemMeta meta = modeItem.getItemMeta();
                List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.GREEN + "✓ " + ChatColor.BOLD + "ТЕКУЩИЙ РЕЖИМ");
                meta.setLore(lore);
                modeItem.setItemMeta(meta);
            }
            
            inventory.setItem(slot++, modeItem);
        }
        
        // Добавляем голову игрока в середину
        ItemStack head = createPlayerHead(target);
        inventory.setItem(0, head);
        
        player.openInventory(inventory);
    }
    
    /**
     * Создать предмет-голову игрока для GUI
     * @param player Игрок
     * @return ItemStack с головой игрока
     */
    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.GREEN + player.getName());
        
        CheckMode mode = getPlayerMode(player);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Текущий режим: " + mode.getDisplayName());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Нажмите, чтобы изменить режим");
        meta.setLore(lore);
        
        head.setItemMeta(meta);
        return head;
    }
    
    /**
     * Создать предмет для выбора режима
     * @param mode Режим проверки
     * @return ItemStack с предметом режима
     */
    private ItemStack createModeItem(CheckMode mode) {
        ItemStack item = new ItemStack(mode.getMaterial());
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(mode.getDisplayName());
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        for (String desc : mode.getDescription()) {
            lore.add(ChatColor.GRAY + desc);
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Нажмите, чтобы выбрать этот режим");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Обработать клик по предмету в GUI
     * @param player Игрок, который кликнул
     * @param clickedItem Предмет, по которому кликнули
     * @param inventoryTitle Название инвентаря
     * @return true, если клик был обработан
     */
    public boolean handleClick(Player player, ItemStack clickedItem, String inventoryTitle) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return false;
        }
        
        // Обработка клика в главном меню (по голове игрока)
        if (inventoryTitle.equals(INVENTORY_TITLE)) {
            if (clickedItem.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
                OfflinePlayer target = meta.getOwningPlayer();
                
                if (target != null && target.isOnline()) {
                    openPlayerModeGUI(player, target.getPlayer());
                    return true;
                }
            }
        }
        // Обработка клика в меню выбора режима
        else if (inventoryTitle.startsWith(PLAYER_MODE_INVENTORY_TITLE)) {
            String playerName = inventoryTitle.substring(PLAYER_MODE_INVENTORY_TITLE.length());
            Player target = Bukkit.getPlayerExact(playerName);
            
            if (target != null) {
                // Определяем, какой режим был выбран
                for (CheckMode mode : CheckMode.values()) {
                    if (clickedItem.getType() == mode.getMaterial() && 
                        clickedItem.getItemMeta().getDisplayName().equals(mode.getDisplayName())) {
                        
                        setPlayerMode(target, mode);
                        player.closeInventory();
                        player.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.GREEN + " Режим проверки для " + 
                                          ChatColor.YELLOW + target.getName() + ChatColor.GREEN + " изменен на " + mode.getDisplayName());
                        
                        // Уведомляем игрока об изменении его режима
                        if (target != player) {
                            target.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.GREEN + " Ваш режим проверки изменен на " + 
                                              mode.getDisplayName() + ChatColor.GREEN + " администратором " + ChatColor.YELLOW + player.getName());
                        }
                        
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Получить текущий режим проверки игрока
     * @param player Игрок
     * @return Режим проверки
     */
    public CheckMode getPlayerMode(Player player) {
        return playerModes.getOrDefault(player.getUniqueId(), CheckMode.MEDIUM);
    }
    
    /**
     * Установить режим проверки для игрока
     * @param player Игрок
     * @param mode Новый режим проверки
     */
    public void setPlayerMode(Player player, CheckMode mode) {
        playerModes.put(player.getUniqueId(), mode);
        
        // Применяем соответствующие настройки в зависимости от режима
        applyCheckModeSettings(player, mode);
    }
    
    /**
     * Применить настройки в зависимости от режима проверки
     * @param player Игрок
     * @param mode Режим проверки
     */
    private void applyCheckModeSettings(Player player, CheckMode mode) {
        switch (mode) {
            case HARD:
                // Максимальная проверка - никаких исключений
                plugin.unexemptPlayer(player);
                break;
                
            case MEDIUM:
                // Стандартная проверка - базовые настройки
                plugin.unexemptPlayer(player);
                break;
                
            case LITE:
                // Легкая проверка - только основные проверки
                plugin.unexemptPlayer(player);
                break;
                
            case OFF:
                // Отключение проверок - полное исключение
                plugin.exemptPlayer(player);
                break;
        }
    }
} 