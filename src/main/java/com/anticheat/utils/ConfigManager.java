package com.anticheat.utils;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ConfigManager {

    private final AntiCheatPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    
    public ConfigManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
        reloadConfig();
    }
    
    /**
     * Перезагрузить конфигурацию из файла
     */
    public void reloadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("Configuration reloaded");
    }
    
    /**
     * Получить конфигурацию
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }
    
    /**
     * Сохранить конфигурацию
     */
    public void saveConfig() {
        if (config == null || configFile == null) {
            return;
        }
        
        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, e);
        }
    }
    
    /**
     * Сохранить дефолтную конфигурацию, если её нет
     */
    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
    }
    
    /**
     * Получить значение из конфигурации для конкретной проверки
     */
    public boolean isCheckEnabled(String check) {
        String path = "checks." + check.toLowerCase() + ".enabled";
        return getConfig().getBoolean(path, true);
    }
    
    /**
     * Получить настройку из конфигурации для конкретной проверки
     */
    public double getCheckValue(String check, String option) {
        String path = "checks." + check.toLowerCase() + "." + option;
        return getConfig().getDouble(path, 0.0);
    }
    
    /**
     * Получить префикс плагина
     */
    public String getPrefix() {
        return getConfig().getString("settings.prefix", "&8[&c&lAntiCheat&8]").replace('&', '§');
    }
    
    /**
     * Включен ли режим отладки
     */
    public boolean isDebugMode() {
        return getConfig().getBoolean("settings.debug-mode", false);
    }
} 