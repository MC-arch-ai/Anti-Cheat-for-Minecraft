package com.anticheat;

import com.anticheat.commands.AntiCheatCommand;
import com.anticheat.gui.CheckModeGUI;
import com.anticheat.gui.GUIListener;
import com.anticheat.listeners.CombatListener;
import com.anticheat.listeners.KnockbackListener;
import com.anticheat.listeners.MovementListener;
import com.anticheat.listeners.PlayerListener;
import com.anticheat.utils.AlertManager;
import com.anticheat.utils.CheckModifier;
import com.anticheat.utils.ConfigManager;
import com.anticheat.utils.MovementNormalizer;
import com.anticheat.utils.ViolationManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatPlugin extends JavaPlugin {
    
    private static AntiCheatPlugin instance;
    private ConfigManager configManager;
    private AlertManager alertManager;
    private ViolationManager violationManager;
    private MovementNormalizer movementNormalizer;
    private CheckModeGUI checkModeGUI;
    private CheckModifier checkModifier;
    
    // Хранилище для игроков, освобожденных от проверок
    private final ConcurrentHashMap<UUID, Boolean> exemptPlayers = new ConcurrentHashMap<>();
    
    // Идентификаторы задач для отмены при выключении плагина
    private int playerScanTaskId = -1;
    private int normalizeTaskId = -1;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.alertManager = new AlertManager(this);
        this.violationManager = new ViolationManager(this);
        this.movementNormalizer = new MovementNormalizer(this);
        this.checkModeGUI = new CheckModeGUI(this);
        this.checkModifier = new CheckModifier(this);
        
        // Register commands
        getCommand("anticheat").setExecutor(new AntiCheatCommand(this));
        
        // Register listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new MovementListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new KnockbackListener(this), this);
        pm.registerEvents(new GUIListener(this, checkModeGUI), this);
        
        // Send startup message
        ConsoleCommandSender console = getServer().getConsoleSender();
        console.sendMessage(ChatColor.GREEN + "---------------------------------------");
        console.sendMessage(ChatColor.RED + "AntiCheat " + getDescription().getVersion() + " has been enabled!");
        console.sendMessage(ChatColor.RED + "AntiCheat now monitoring for cheats...");
        console.sendMessage(ChatColor.GREEN + "---------------------------------------");
        
        // Проверка на обновления, если включена
        if (configManager.getConfig().getBoolean("settings.update-checker", false)) {
            getLogger().info("Checking for updates...");
            // Здесь можно реализовать проверку обновлений
        }
        
        // Запускаем задачу периодической проверки всех игроков
        startPlayerScanTask();
        
        // Запускаем задачу для нормализации скорости игроков
        if (configManager.getConfig().getBoolean("advanced.normalize-movement", false)) {
            startNormalizeTask();
        }
    }

    @Override
    public void onDisable() {
        // Отменяем запущенные задачи
        if (playerScanTaskId != -1) {
            getServer().getScheduler().cancelTask(playerScanTaskId);
        }
        
        if (normalizeTaskId != -1) {
            getServer().getScheduler().cancelTask(normalizeTaskId);
        }
        
        // Сохраняем данные, если необходимо
        saveData();
        
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "AntiCheat has been disabled!");
        
        // Clear references
        instance = null;
    }
    
    /**
     * Запускает периодическую задачу сканирования игроков для выявления более сложных читов
     */
    private void startPlayerScanTask() {
        playerScanTaskId = getServer().getScheduler().runTaskTimer(this, () -> {
            // Проверяем всех онлайн-игроков
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isExempt(player)) {
                    continue;
                }
                
                // Проверка на подозрительные движения (для выявления сложных flight-читов)
                checkAdvancedMovement(player);
                
                // Дополнительные сложные проверки можно добавить здесь
            }
        }, 100L, 100L).getTaskId(); // Каждые 5 секунд (100 тиков)
    }
    
    /**
     * Запускает задачу нормализации скорости игроков
     */
    private void startNormalizeTask() {
        normalizeTaskId = getServer().getScheduler().runTaskTimer(this, () -> {
            // Нормализуем скорость всех игроков
            Bukkit.getOnlinePlayers().stream()
                .filter(player -> !isExempt(player))
                .forEach(player -> movementNormalizer.normalizeVelocity(player));
        }, 5L, 5L).getTaskId(); // Каждые 5 тиков (0.25 секунды)
    }
    
    /**
     * Продвинутая проверка движений игрока для выявления сложных читов
     */
    private void checkAdvancedMovement(Player player) {
        // Эта проверка может быть расширена в будущем для выявления более сложных паттернов читов
        
        // Пример: Если игрок двигается слишком плавно (признак движения с помощью чита)
        if (player.getVelocity().length() > 0.1 && !player.isOnGround() && !player.isGliding() && 
            !player.isSwimming() && !player.isInsideVehicle()) {
            
            // Здесь можно собирать данные для дальнейшего анализа
            // ...
        }
    }
    
    /**
     * Получить экземпляр плагина
     */
    public static AntiCheatPlugin getInstance() {
        return instance;
    }
    
    /**
     * Получить менеджер конфигурации
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Получить менеджер оповещений
     */
    public AlertManager getAlertManager() {
        return alertManager;
    }
    
    /**
     * Получить менеджер нарушений
     */
    public ViolationManager getViolationManager() {
        return violationManager;
    }
    
    /**
     * Получить нормализатор движения
     */
    public MovementNormalizer getMovementNormalizer() {
        return movementNormalizer;
    }
    
    /**
     * Получить GUI менеджер режимов проверки
     */
    public CheckModeGUI getCheckModeGUI() {
        return checkModeGUI;
    }
    
    /**
     * Получить модификатор проверок
     */
    public CheckModifier getCheckModifier() {
        return checkModifier;
    }
    
    /**
     * Проверить, освобожден ли игрок от проверок
     */
    public boolean isExempt(Player player) {
        // Игроки с правами администратора и специальным разрешением освобождены
        if (player.hasPermission("anticheat.bypass")) {
            return true;
        }
        
        // Проверяем режим игрока - если OFF, то игрок освобожден
        if (checkModeGUI != null && 
            checkModeGUI.getPlayerMode(player) == CheckModeGUI.CheckMode.OFF) {
            return true;
        }
        
        return exemptPlayers.getOrDefault(player.getUniqueId(), false);
    }
    
    /**
     * Проверить, включена ли конкретная проверка для игрока
     */
    public boolean isCheckEnabled(Player player, String checkType) {
        // Если игрок освобожден от проверок
        if (isExempt(player)) {
            return false;
        }
        
        // Используем модификатор проверок для определения активности проверки
        return checkModifier.isCheckEnabled(player, checkType);
    }
    
    /**
     * Получить модифицированное значение для проверки
     */
    public double getModifiedValue(Player player, String checkType, String option, boolean isThreshold) {
        // Получаем базовое значение из конфига
        double baseValue = configManager.getCheckValue(checkType, option);
        
        // Используем модификатор проверок для изменения значения в зависимости от режима
        return checkModifier.getModifiedValue(player, baseValue, isThreshold);
    }
    
    /**
     * Освободить игрока от проверок
     */
    public void exemptPlayer(Player player) {
        exemptPlayers.put(player.getUniqueId(), true);
    }
    
    /**
     * Убрать освобождение от проверок
     */
    public void unexemptPlayer(Player player) {
        exemptPlayers.put(player.getUniqueId(), false);
    }
    
    /**
     * Перезагрузить плагин
     */
    public void reload() {
        // Отменяем текущие задачи
        if (playerScanTaskId != -1) {
            getServer().getScheduler().cancelTask(playerScanTaskId);
            playerScanTaskId = -1;
        }
        
        if (normalizeTaskId != -1) {
            getServer().getScheduler().cancelTask(normalizeTaskId);
            normalizeTaskId = -1;
        }
        
        // Перезагружаем конфиги
        configManager.reloadConfig();
        alertManager.reload();
        violationManager.reload();
        
        // Перезапускаем задачи
        startPlayerScanTask();
        if (configManager.getConfig().getBoolean("advanced.normalize-movement", false)) {
            startNormalizeTask();
        }
        
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "AntiCheat has been reloaded!");
    }
    
    /**
     * Сохранить данные
     */
    private void saveData() {
        // Здесь можно реализовать сохранение данных, если необходимо
    }
} 