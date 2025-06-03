package com.anticheat.listeners;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatListener implements Listener {

    private final AntiCheatPlugin plugin;
    
    // Хранилища данных для различных проверок
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();
    private final Map<UUID, Integer> clicksPerSecond = new HashMap<>();
    private final Map<UUID, Long> lastClickUpdate = new HashMap<>();
    private final Map<UUID, Location> lastPlayerLocation = new HashMap<>();
    private final Map<UUID, Float> lastYaw = new HashMap<>();
    private final Map<UUID, Float> lastPitch = new HashMap<>();
    
    public CombatListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.isCancelled() || event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        
        Player player = event.getPlayer();
        
        if (plugin.isExempt(player)) {
            return;
        }
        
        // Проверка на высокий CPS (клики в секунду)
        checkCPS(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        Entity target = event.getEntity();
        
        if (plugin.isExempt(player)) {
            return;
        }
        
        // Проверка на KillAura - удары без поворота головы
        checkKillAura(player, target);
        
        // Проверка на превышение дистанции удара
        checkReach(player, target, event.getDamage());
    }
    
    /**
     * Проверка на высокий CPS (клики в секунду)
     */
    private void checkCPS(Player player) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Инициализируем, если первый клик
        if (!lastClickUpdate.containsKey(uuid)) {
            lastClickUpdate.put(uuid, currentTime);
            clicksPerSecond.put(uuid, 1);
            return;
        }
        
        // Обновляем счетчик кликов
        int clicks = clicksPerSecond.getOrDefault(uuid, 0) + 1;
        clicksPerSecond.put(uuid, clicks);
        
        // Если прошла секунда, проверяем CPS
        long lastUpdate = lastClickUpdate.getOrDefault(uuid, 0L);
        if (currentTime - lastUpdate >= 1000) {
            // Проверяем на превышение лимита CPS
            double maxCPS = plugin.getConfigManager().getCheckValue("combat.killaura", "max-cps");
            if (maxCPS <= 0) maxCPS = 22; // Значение по умолчанию
            
            if (clicks > maxCPS) {
                // Нарушение - высокий CPS
                String details = "CPS: " + clicks + " (max: " + maxCPS + ")";
                plugin.getViolationManager().addViolation(player, "combat.killaura", 1, details);
            }
            
            // Сбрасываем счетчик
            clicksPerSecond.put(uuid, 0);
            lastClickUpdate.put(uuid, currentTime);
        }
    }
    
    /**
     * Проверка на KillAura - удары без поворота головы
     */
    private void checkKillAura(Player player, Entity target) {
        UUID uuid = player.getUniqueId();
        Location playerLoc = player.getLocation();
        
        // Сохраняем предыдущую локацию для следующей проверки
        if (lastPlayerLocation.containsKey(uuid) && lastYaw.containsKey(uuid) && lastPitch.containsKey(uuid)) {
            Location lastLoc = lastPlayerLocation.get(uuid);
            float lastPlayerYaw = lastYaw.get(uuid);
            float lastPlayerPitch = lastPitch.get(uuid);
            
            // Рассчитываем вектор направления на цель
            Vector toTarget = target.getLocation().toVector().subtract(playerLoc.toVector());
            
            // Рассчитываем угол между направлением игрока и направлением на цель
            double angle = calculateAngle(player.getLocation().getDirection(), toTarget);
            
            // Проверяем на слишком большой угол (невозможность видеть цель)
            double maxAngle = plugin.getConfigManager().getCheckValue("combat.killaura", "max-angle");
            if (maxAngle <= 0) maxAngle = 120; // Значение по умолчанию
            
            if (angle > maxAngle) {
                // Нарушение - удар без прямой видимости цели
                String details = "Angle: " + String.format("%.1f", angle) + "° (max: " + maxAngle + "°)";
                plugin.getViolationManager().addViolation(player, "combat.killaura", 2, details);
            }
            
            // Проверка на удар без изменения направления взгляда
            float yawChange = Math.abs(playerLoc.getYaw() - lastPlayerYaw);
            float pitchChange = Math.abs(playerLoc.getPitch() - lastPlayerPitch);
            
            if (yawChange < 0.1 && pitchChange < 0.1 && angle > 45) {
                // Нарушение - удар без поворота головы
                plugin.getViolationManager().addViolation(player, "combat.killaura", 2, "No head movement");
            }
        }
        
        // Обновляем последнюю локацию и время атаки
        lastPlayerLocation.put(uuid, playerLoc.clone());
        lastYaw.put(uuid, playerLoc.getYaw());
        lastPitch.put(uuid, playerLoc.getPitch());
        lastAttackTime.put(uuid, System.currentTimeMillis());
    }
    
    /**
     * Проверка на превышение дистанции удара
     */
    private void checkReach(Player player, Entity target, double damage) {
        // Расчет расстояния между игроком и целью
        double distance = player.getLocation().distance(target.getLocation());
        
        // Учитываем размер хитбокса цели (примерно)
        double hitboxSize = 0.4; // базовый размер
        
        // Если цель игрок, то стандартный хитбокс примерно 0.6 шириной
        if (target instanceof Player) {
            hitboxSize = 0.6;
        } else {
            // Для других сущностей можно вычислять по типу, но упрощенно
            hitboxSize = 0.8; // для больших мобов
        }
        
        // Пинг игрока (в тиках, умножаем на 50 мс для перевода в миллисекунды)
        // Это приблизительная оценка, в реальном плагине лучше использовать более точный способ
        double pingCompensation = 0.3; // Примерно 150 мс (3 тика) компенсация пинга
        
        // Максимальная разрешенная дистанция удара (обычно 3.0 блока + компенсация пинга + хитбокс)
        double maxReach = plugin.getConfigManager().getCheckValue("combat.reach", "max-reach");
        if (maxReach <= 0) maxReach = 3.2; // Значение по умолчанию
        
        // Расчет фактической допустимой дистанции с учетом всех факторов
        double allowedDistance = maxReach + hitboxSize / 2 + pingCompensation;
        
        // Если расстояние превышает допустимое
        if (distance > allowedDistance) {
            String details = String.format("Distance: %.2f (allowed: %.2f)", distance, allowedDistance);
            int vlToAdd = (int) Math.ceil((distance - allowedDistance) * 2); // Больше нарушение - больше VL
            
            plugin.getViolationManager().addViolation(player, "combat.reach", vlToAdd, details);
        }
    }
    
    /**
     * Рассчитать угол между двумя векторами (в градусах)
     */
    private double calculateAngle(Vector v1, Vector v2) {
        double dot = v1.dot(v2) / (v1.length() * v2.length());
        // Корректируем dot, чтобы избежать ошибок из-за неточности
        dot = Math.min(1.0, Math.max(-1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }
} 