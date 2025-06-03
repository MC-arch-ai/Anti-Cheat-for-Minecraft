package com.anticheat.utils;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class AlertManager {

    private final AntiCheatPlugin plugin;
    private final Set<UUID> alertToggled;
    private final SimpleDateFormat dateFormat;
    private File logFile;
    
    public AlertManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.alertToggled = new HashSet<>();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        // Инициализация лог-файла
        if (plugin.getConfigManager().getConfig().getBoolean("alerts.log-to-file")) {
            initLogFile();
        }
    }
    
    /**
     * Перезагрузить настройки оповещений
     */
    public void reload() {
        if (plugin.getConfigManager().getConfig().getBoolean("alerts.log-to-file")) {
            initLogFile();
        }
    }
    
    /**
     * Инициализировать лог файл
     */
    private void initLogFile() {
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            plugin.getLogger().severe("Could not create logs directory!");
            return;
        }
        
        String fileName = "violations-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
        logFile = new File(logsDir, fileName);
        
        try {
            if (!logFile.exists() && !logFile.createNewFile()) {
                plugin.getLogger().severe("Could not create log file!");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating log file", e);
        }
    }
    
    /**
     * Отправить оповещение о нарушении
     */
    public void alert(Player player, String checkName, int violations, String details) {
        if (!plugin.getConfigManager().getConfig().getBoolean("alerts.enabled")) {
            return;
        }
        
        String prefix = plugin.getConfigManager().getPrefix();
        String message = prefix + " §f" + player.getName() + " §7failed §c" + checkName 
                + " §7check. §8[§cVL: " + violations + "§8]";
        
        if (plugin.getConfigManager().getConfig().getBoolean("alerts.show-details") 
                && details != null && !details.isEmpty()) {
            message += " §8(§7" + details + "§8)";
        }
        
        // Отправка оповещения всем игрокам с правами и активными оповещениями
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (hasAlertsEnabled(online) && online.hasPermission("anticheat.alerts")) {
                online.sendMessage(message);
            }
        }
        
        // Отправка в консоль
        plugin.getLogger().info(ChatColor.stripColor(message));
        
        // Логирование, если включено
        if (plugin.getConfigManager().getConfig().getBoolean("alerts.log-to-file")) {
            logViolation(player, checkName, violations, details);
        }
        
        // Отправка webhook, если включено
        if (plugin.getConfigManager().getConfig().getBoolean("alerts.webhook.enabled")) {
            String webhookUrl = plugin.getConfigManager().getConfig().getString("alerts.webhook.url");
            if (webhookUrl != null && !webhookUrl.isEmpty()) {
                sendWebhook(player, checkName, violations, details, webhookUrl);
            }
        }
    }
    
    /**
     * Логировать нарушение в файл
     */
    private void logViolation(Player player, String checkName, int violations, String details) {
        if (logFile == null) {
            return;
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            String timestamp = dateFormat.format(new Date());
            String log = String.format("[%s] %s (%s) failed %s check with %d violations. Details: %s", 
                    timestamp, player.getName(), player.getUniqueId().toString(), 
                    checkName, violations, details != null ? details : "none");
            
            writer.println(log);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Error writing to log file", e);
        }
    }
    
    /**
     * Отправить webhook (Discord, Slack и т.д.)
     */
    private void sendWebhook(Player player, String checkName, int violations, String details, String webhookUrl) {
        // Здесь можно реализовать отправку оповещений через webhook
        // Например, с использованием библиотеки для работы с Discord/Slack API
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Would send webhook to: " + webhookUrl);
        }
    }
    
    /**
     * Проверить, включены ли оповещения для игрока
     */
    public boolean hasAlertsEnabled(Player player) {
        return alertToggled.contains(player.getUniqueId());
    }
    
    /**
     * Переключить оповещения для игрока
     */
    public boolean toggleAlerts(Player player) {
        UUID uuid = player.getUniqueId();
        if (alertToggled.contains(uuid)) {
            alertToggled.remove(uuid);
            return false;
        } else {
            alertToggled.add(uuid);
            return true;
        }
    }
    
    /**
     * Установить значение оповещений для игрока
     */
    public void setAlerts(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        if (enabled) {
            alertToggled.add(uuid);
        } else {
            alertToggled.remove(uuid);
        }
    }
} 