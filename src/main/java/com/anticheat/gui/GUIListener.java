package com.anticheat.gui;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Слушатель событий для GUI интерфейса
 */
public class GUIListener implements Listener {

    private final AntiCheatPlugin plugin;
    private final CheckModeGUI checkModeGUI;
    
    public GUIListener(AntiCheatPlugin plugin, CheckModeGUI checkModeGUI) {
        this.plugin = plugin;
        this.checkModeGUI = checkModeGUI;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Проверяем, что это клик игрока в инвентаре
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();
        
        // Получаем название инвентаря для проверки
        String inventoryTitle = event.getView().getTitle();
        
        // Проверяем, является ли это нашим GUI
        if (inventoryTitle.contains("AntiCheat")) {
            // Отменяем клик для предотвращения перемещения предметов
            event.setCancelled(true);
            
            // Обрабатываем клик в GUI
            if (clickedItem != null) {
                checkModeGUI.handleClick(player, clickedItem, inventoryTitle);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Дополнительная логика при закрытии инвентаря, если потребуется
    }
} 