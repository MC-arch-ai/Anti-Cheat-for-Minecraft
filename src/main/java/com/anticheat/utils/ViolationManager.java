package com.anticheat.utils;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.BanList.Type;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ViolationManager {

    private final AntiCheatPlugin plugin;
    
    // Хранилище нарушений для каждого игрока (UUID -> Check -> Violations)
    private final Map<UUID, Map<String, Integer>> violations = new ConcurrentHashMap<>();
    
    // Хранилище истечения времени для нарушений (UUID -> Check -> Timestamp)
    private final Map<UUID, Map<String, Long>> violationTimers = new ConcurrentHashMap<>();
    
    // Время в миллисекундах, когда уровень нарушений начнет снижаться
    private static final long VIOLATION_DECAY_TIME = 60000; // 1 минута
    
    public ViolationManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        
        // Запускаем задачу для сброса нарушений
        startViolationDecayTask();
    }
    
    /**
     * Перезагрузить настройки нарушений
     */
    public void reload() {
        // В будущем, можно добавить дополнительную логику при перезагрузке
    }
    
    /**
     * Запустить задачу для уменьшения нарушений с течением времени
     */
    private void startViolationDecayTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            
            for (UUID uuid : violations.keySet()) {
                Map<String, Integer> playerViolations = violations.get(uuid);
                Map<String, Long> playerTimers = violationTimers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
                
                // Проходим по всем проверкам для игрока
                for (String check : new HashMap<>(playerViolations).keySet()) {
                    long lastViolationTime = playerTimers.getOrDefault(check, 0L);
                    
                    // Если прошло достаточно времени с последнего нарушения
                    if (currentTime - lastViolationTime > VIOLATION_DECAY_TIME) {
                        int currentVL = playerViolations.get(check);
                        int newVL = Math.max(0, currentVL - 1);
                        
                        if (newVL > 0) {
                            playerViolations.put(check, newVL);
                        } else {
                            playerViolations.remove(check);
                        }
                        
                        // Обновляем время последнего снижения
                        playerTimers.put(check, currentTime);
                    }
                }
                
                // Если для игрока больше нет нарушений, удаляем его из списков
                if (playerViolations.isEmpty()) {
                    violations.remove(uuid);
                    violationTimers.remove(uuid);
                }
            }
        }, 20L, 20L); // Каждую секунду
    }
    
    /**
     * Добавить нарушение для игрока и проверить на наказание
     * 
     * @param player Игрок
     * @param checkName Название проверки
     * @param vlToAdd Количество нарушений для добавления
     * @param details Дополнительная информация о нарушении
     * @return Новое количество нарушений
     */
    public int addViolation(Player player, String checkName, int vlToAdd, String details) {
        if (plugin.isExempt(player)) {
            return 0;
        }
        
        // Проверяем, включена ли эта проверка для игрока
        if (!plugin.isCheckEnabled(player, checkName)) {
            return 0;
        }
        
        UUID uuid = player.getUniqueId();
        
        // Получаем или создаем мапу нарушений для игрока
        Map<String, Integer> playerViolations = violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        
        // Получаем или создаем мапу таймеров для игрока
        Map<String, Long> playerTimers = violationTimers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        
        // Применяем модификатор VL в зависимости от режима проверки
        double vlMultiplier = plugin.getCheckModifier().getViolationMultiplier(player);
        int modifiedVL = (int) Math.ceil(vlToAdd * vlMultiplier);
        
        // Обновляем количество нарушений
        int previousVL = playerViolations.getOrDefault(checkName, 0);
        int newVL = previousVL + modifiedVL;
        playerViolations.put(checkName, newVL);
        
        // Обновляем время последнего нарушения
        playerTimers.put(checkName, System.currentTimeMillis());
        
        // Отправляем оповещение о нарушении
        plugin.getAlertManager().alert(player, checkName, newVL, details);
        
        // Проверяем, нужно ли наказать игрока
        checkPunishment(player, checkName, newVL);
        
        return newVL;
    }
    
    /**
     * Проверить, нужно ли применить наказание к игроку
     */
    private void checkPunishment(Player player, String checkName, int violations) {
        // Проверяем порог для кика
        int kickThreshold = plugin.getConfigManager().getConfig().getInt("punishments.thresholds." + checkName.toLowerCase() + ".kick", 0);
        if (kickThreshold > 0 && violations >= kickThreshold) {
            if (plugin.getConfigManager().getConfig().getBoolean("punishments.actions.kick.enabled")) {
                applyKick(player, checkName);
                return; // Не продолжаем проверку для бана
            }
        }
        
        // Проверяем порог для бана
        int banThreshold = plugin.getConfigManager().getConfig().getInt("punishments.thresholds." + checkName.toLowerCase() + ".ban", 0);
        if (banThreshold > 0 && violations >= banThreshold) {
            if (plugin.getConfigManager().getConfig().getBoolean("punishments.actions.ban.enabled")) {
                applyBan(player, checkName);
                return;
            }
        }
        
        // Выполняем команды, если включено
        if (plugin.getConfigManager().getConfig().getBoolean("punishments.actions.command.enabled")) {
            for (String cmd : plugin.getConfigManager().getConfig().getStringList("punishments.actions.command.commands")) {
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()).replace("%check%", checkName)
                );
            }
        }
    }
    
    /**
     * Применить кик к игроку
     */
    private void applyKick(Player player, String checkName) {
        String kickMessage = plugin.getConfigManager().getConfig()
                .getString("punishments.actions.kick.message", "Kicked for cheating")
                .replace("%check%", checkName)
                .replace('&', '§');
        
        // Запускаем кик в основном потоке
        Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(kickMessage));
    }
    
    /**
     * Применить бан к игроку
     */
    private void applyBan(Player player, String checkName) {
        String banTime = plugin.getConfigManager().getConfig().getString("punishments.actions.ban.time", "7d");
        String banMessage = plugin.getConfigManager().getConfig()
                .getString("punishments.actions.ban.message", "Banned for cheating")
                .replace("%check%", checkName)
                .replace('&', '§');
        
        // Конвертируем время бана в миллисекунды
        long banDuration = parseBanTime(banTime);
        Date expiration = banDuration <= 0 ? null : new Date(System.currentTimeMillis() + banDuration);
        
        // Запускаем бан в основном потоке
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getBanList(Type.NAME).addBan(player.getName(), banMessage, expiration, "AntiCheat");
            player.kickPlayer(banMessage);
        });
    }
    
    /**
     * Парсинг времени бана из строки в миллисекунды
     */
    private long parseBanTime(String time) {
        if (time == null || time.equalsIgnoreCase("permanent")) {
            return -1;
        }
        
        try {
            char unit = time.charAt(time.length() - 1);
            long value = Long.parseLong(time.substring(0, time.length() - 1));
            
            switch (unit) {
                case 's': return TimeUnit.SECONDS.toMillis(value);
                case 'm': return TimeUnit.MINUTES.toMillis(value);
                case 'h': return TimeUnit.HOURS.toMillis(value);
                case 'd': return TimeUnit.DAYS.toMillis(value);
                default: return value; // Если нет единицы измерения, считаем что это секунды
            }
        } catch (Exception e) {
            return 604800000; // По умолчанию 7 дней (7 * 24 * 60 * 60 * 1000)
        }
    }
    
    /**
     * Получить количество нарушений для игрока и проверки
     */
    public int getViolations(Player player, String checkName) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> playerViolations = violations.get(uuid);
        
        if (playerViolations == null) {
            return 0;
        }
        
        return playerViolations.getOrDefault(checkName, 0);
    }
    
    /**
     * Сбросить нарушения для игрока
     */
    public void resetViolations(Player player) {
        UUID uuid = player.getUniqueId();
        violations.remove(uuid);
        violationTimers.remove(uuid);
    }
    
    /**
     * Сбросить нарушения для игрока и конкретной проверки
     */
    public void resetViolations(Player player, String checkName) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> playerViolations = violations.get(uuid);
        Map<String, Long> playerTimers = violationTimers.get(uuid);
        
        if (playerViolations != null) {
            playerViolations.remove(checkName);
            
            if (playerViolations.isEmpty()) {
                violations.remove(uuid);
            }
        }
        
        if (playerTimers != null) {
            playerTimers.remove(checkName);
            
            if (playerTimers.isEmpty()) {
                violationTimers.remove(uuid);
            }
        }
    }
} 