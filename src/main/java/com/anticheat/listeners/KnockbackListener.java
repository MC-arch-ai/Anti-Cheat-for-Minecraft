package com.anticheat.listeners;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KnockbackListener implements Listener {

    private final AntiCheatPlugin plugin;
    
    // Хранилища данных для проверок анти-отбрасывания
    private final Map<UUID, Long> lastKnockbackTime = new HashMap<>();
    private final Map<UUID, Vector> expectedVelocity = new HashMap<>();
    private final Map<UUID, Integer> knockbackIgnoreCount = new HashMap<>();
    private final Map<UUID, Location> lastPlayerLocation = new HashMap<>();
    
    // Допустимая погрешность для скорости (в процентах)
    private static final double VELOCITY_THRESHOLD = 0.8; // 80% от ожидаемой скорости
    
    public KnockbackListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        UUID uuid = player.getUniqueId();
        
        if (plugin.isExempt(player)) {
            return;
        }
        
        // Сохраняем местоположение перед отбрасыванием
        lastPlayerLocation.put(uuid, player.getLocation().clone());
        
        // Запоминаем время отбрасывания
        lastKnockbackTime.put(uuid, System.currentTimeMillis());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (plugin.isExempt(player)) {
            return;
        }
        
        // Проверяем, было ли недавно нанесено повреждение (отбрасывание)
        Long lastDamageTime = lastKnockbackTime.get(uuid);
        if (lastDamageTime == null || System.currentTimeMillis() - lastDamageTime > 200) {
            return; // Не было недавнего отбрасывания
        }
        
        // Сохраняем ожидаемую скорость
        Vector velocity = event.getVelocity();
        expectedVelocity.put(uuid, velocity.clone());
        
        // Запускаем задачу для проверки, было ли отбрасывание применено
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkKnockback(player, velocity);
        }, 5L); // Проверяем через 5 тиков (0.25 секунды)
    }
    
    /**
     * Проверить, применилось ли отбрасывание к игроку
     */
    private void checkKnockback(Player player, Vector expectedVelocity) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        
        // Если игрок на земле или в воде, пропускаем проверку
        if (player.isOnGround() || player.isSwimming() || player.isInsideVehicle()) {
            return;
        }
        
        // Получаем сохраненную позицию до отбрасывания
        Location lastLoc = lastPlayerLocation.get(uuid);
        if (lastLoc == null) {
            return;
        }
        
        // Получаем текущую позицию
        Location currentLoc = player.getLocation();
        
        // Вычисляем фактическое перемещение
        Vector actualMovement = currentLoc.toVector().subtract(lastLoc.toVector());
        
        // Проверяем горизонтальное перемещение (X и Z)
        double expectedHorizontal = Math.sqrt(expectedVelocity.getX() * expectedVelocity.getX() + 
                                            expectedVelocity.getZ() * expectedVelocity.getZ());
        double actualHorizontal = Math.sqrt(actualMovement.getX() * actualMovement.getX() + 
                                          actualMovement.getZ() * actualMovement.getZ());
        
        // Если игрок должен был получить значительное горизонтальное отбрасывание
        if (expectedHorizontal > 0.2) {
            // Рассчитываем процент от ожидаемого отбрасывания
            double percentage = actualHorizontal / expectedHorizontal;
            
            // Если фактическое перемещение меньше порогового значения
            if (percentage < VELOCITY_THRESHOLD) {
                // Увеличиваем счетчик игнорирований отбрасывания
                int ignoreCount = knockbackIgnoreCount.getOrDefault(uuid, 0) + 1;
                knockbackIgnoreCount.put(uuid, ignoreCount);
                
                // Если игрок игнорирует отбрасывание несколько раз подряд
                if (ignoreCount >= 2) {
                    String details = String.format("Expected: %.2f, Actual: %.2f (%.0f%%)", 
                            expectedHorizontal, actualHorizontal, percentage * 100);
                    
                    // Нарушение - игнорирование отбрасывания
                    plugin.getViolationManager().addViolation(player, "combat.antikb", 
                            Math.min(ignoreCount, 5), details);
                }
                
                // Запускаем дополнительную проверку через некоторое время
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    checkAntiKBPhase2(player, expectedVelocity);
                }, 5L); // Через дополнительные 5 тиков
            } else {
                // Сбрасываем счетчик, если отбрасывание применилось нормально
                knockbackIgnoreCount.put(uuid, 0);
            }
        }
        
        // Обновляем позицию для следующей проверки
        lastPlayerLocation.put(uuid, currentLoc);
    }
    
    /**
     * Вторая фаза проверки анти-KB (проверка на быстрое возвращение)
     */
    private void checkAntiKBPhase2(Player player, Vector expectedVelocity) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        
        // Получаем сохраненную позицию
        Location lastLoc = lastPlayerLocation.get(uuid);
        if (lastLoc == null) {
            return;
        }
        
        // Получаем текущую позицию
        Location currentLoc = player.getLocation();
        
        // Проверяем, не вернулся ли игрок резко назад (что может указывать на телепорт обратно)
        Vector movement = currentLoc.toVector().subtract(lastLoc.toVector());
        
        // Если игрок движется против ожидаемого направления
        if (movement.dot(expectedVelocity) < 0 && movement.length() > 0.3) {
            String details = "Reverse movement after knockback";
            
            // Сильное нарушение - возврат после отбрасывания
            plugin.getViolationManager().addViolation(player, "combat.antikb", 3, details);
        }
    }
} 