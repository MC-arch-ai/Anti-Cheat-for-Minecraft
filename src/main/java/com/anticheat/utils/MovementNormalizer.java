package com.anticheat.utils;

import com.anticheat.AntiCheatPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Класс для нормализации движения игрока и исправления экстремальных значений скорости
 */
public class MovementNormalizer {

    private final AntiCheatPlugin plugin;
    
    // Максимальные значения скорости по умолчанию
    private static final double DEFAULT_MAX_VELOCITY_X = 4.0;
    private static final double DEFAULT_MAX_VELOCITY_Y = 4.0;
    private static final double DEFAULT_MAX_VELOCITY_Z = 4.0;
    
    // Значение для нормализации скорости по умолчанию
    private static final double DEFAULT_VELOCITY_MULTIPLIER = 0.98;
    
    public MovementNormalizer(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Нормализовать скорость игрока, если она превышает допустимые значения
     * @param player Игрок
     * @return true если скорость была нормализована
     */
    public boolean normalizeVelocity(Player player) {
        if (!isNormalizeEnabled() || player == null || !player.isOnline()) {
            return false;
        }
        
        Vector velocity = player.getVelocity();
        
        // Получаем максимальные значения скорости из конфига
        double maxX = plugin.getConfigManager().getConfig().getDouble("advanced.max-velocity.x", DEFAULT_MAX_VELOCITY_X);
        double maxY = plugin.getConfigManager().getConfig().getDouble("advanced.max-velocity.y", DEFAULT_MAX_VELOCITY_Y);
        double maxZ = plugin.getConfigManager().getConfig().getDouble("advanced.max-velocity.z", DEFAULT_MAX_VELOCITY_Z);
        
        // Получаем множитель для нормализации
        double multiplier = plugin.getConfigManager().getConfig()
                .getDouble("checks.movement.speed.velocity-multiplier", DEFAULT_VELOCITY_MULTIPLIER);
        
        boolean wasNormalized = false;
        
        // Проверяем и нормализуем значения скорости по осям
        if (Math.abs(velocity.getX()) > maxX) {
            velocity.setX(maxX * Math.signum(velocity.getX()));
            wasNormalized = true;
        }
        
        if (Math.abs(velocity.getY()) > maxY) {
            velocity.setY(maxY * Math.signum(velocity.getY()));
            wasNormalized = true;
        }
        
        if (Math.abs(velocity.getZ()) > maxZ) {
            velocity.setZ(maxZ * Math.signum(velocity.getZ()));
            wasNormalized = true;
        }
        
        // Если включена нормализация движения, применяем множитель
        if (plugin.getConfigManager().getConfig().getBoolean("checks.movement.speed.normalize-velocity", false)) {
            // Применяем множитель только к горизонтальному движению
            velocity.setX(velocity.getX() * multiplier);
            velocity.setZ(velocity.getZ() * multiplier);
            wasNormalized = true;
        }
        
        // Если были внесены изменения, устанавливаем новую скорость
        if (wasNormalized) {
            player.setVelocity(velocity);
            
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Normalized velocity for " + player.getName() + ": " + velocity);
            }
        }
        
        return wasNormalized;
    }
    
    /**
     * Исправить экстремальные значения координат игрока
     * @param player Игрок
     * @param location Текущая локация
     * @param lastLocation Предыдущая локация
     * @return Исправленная локация или null, если локация не была изменена
     */
    public Location fixExtremeCoordinates(Player player, Location location, Location lastLocation) {
        if (!isFixVelocityEnabled() || lastLocation == null) {
            return null;
        }
        
        // Вычисляем изменение позиции
        double dx = location.getX() - lastLocation.getX();
        double dy = location.getY() - lastLocation.getY();
        double dz = location.getZ() - lastLocation.getZ();
        
        // Получаем максимальные значения скорости из конфига
        double maxX = plugin.getConfigManager().getConfig().getDouble("advanced.max-velocity.x", DEFAULT_MAX_VELOCITY_X);
        double maxY = plugin.getConfigManager().getConfig().getDouble("advanced.max-velocity.y", DEFAULT_MAX_VELOCITY_Y);
        double maxZ = plugin.getConfigManager().getConfig().getDouble("advanced.max-velocity.z", DEFAULT_MAX_VELOCITY_Z);
        
        boolean needsFix = false;
        
        // Проверяем на экстремальные значения перемещения
        if (Math.abs(dx) > maxX) {
            dx = maxX * Math.signum(dx);
            needsFix = true;
        }
        
        if (Math.abs(dy) > maxY) {
            dy = maxY * Math.signum(dy);
            needsFix = true;
        }
        
        if (Math.abs(dz) > maxZ) {
            dz = maxZ * Math.signum(dz);
            needsFix = true;
        }
        
        // Если нужно исправить координаты, создаем новую локацию
        if (needsFix) {
            Location fixedLocation = lastLocation.clone();
            fixedLocation.add(dx, dy, dz);
            fixedLocation.setYaw(location.getYaw());
            fixedLocation.setPitch(location.getPitch());
            
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Fixed extreme coordinates for " + player.getName() + 
                        ": " + location + " -> " + fixedLocation);
            }
            
            return fixedLocation;
        }
        
        return null;
    }
    
    /**
     * Проверить, включена ли нормализация движения
     */
    private boolean isNormalizeEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("advanced.normalize-movement", false);
    }
    
    /**
     * Проверить, включено ли исправление экстремальных значений скорости
     */
    private boolean isFixVelocityEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("advanced.fix-extreme-velocity", false);
    }
} 