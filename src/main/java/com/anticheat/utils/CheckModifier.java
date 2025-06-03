package com.anticheat.utils;

import com.anticheat.AntiCheatPlugin;
import com.anticheat.gui.CheckModeGUI;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * Класс для настройки чувствительности проверок в зависимости от режима
 */
public class CheckModifier {

    private final AntiCheatPlugin plugin;
    
    // Множители для различных режимов проверки
    private static final double HARD_MULTIPLIER = 0.8;    // Более чувствительно (меньше порог для нарушения)
    private static final double MEDIUM_MULTIPLIER = 1.0;  // Стандартная чувствительность
    private static final double LITE_MULTIPLIER = 1.5;    // Менее чувствительно (выше порог для нарушения)
    private static final double OFF_MULTIPLIER = 999.0;   // Практически отключает проверку
    
    // Кэш множителей для порогов
    private static final Map<CheckModeGUI.CheckMode, Double> THRESHOLD_MULTIPLIERS = new EnumMap<>(CheckModeGUI.CheckMode.class);
    // Кэш множителей для нарушений
    private static final Map<CheckModeGUI.CheckMode, Double> VL_MULTIPLIERS = new EnumMap<>(CheckModeGUI.CheckMode.class);
    
    static {
        // Инициализация кэшей
        THRESHOLD_MULTIPLIERS.put(CheckModeGUI.CheckMode.HARD, HARD_MULTIPLIER);
        THRESHOLD_MULTIPLIERS.put(CheckModeGUI.CheckMode.MEDIUM, MEDIUM_MULTIPLIER);
        THRESHOLD_MULTIPLIERS.put(CheckModeGUI.CheckMode.LITE, LITE_MULTIPLIER);
        THRESHOLD_MULTIPLIERS.put(CheckModeGUI.CheckMode.OFF, OFF_MULTIPLIER);
        
        VL_MULTIPLIERS.put(CheckModeGUI.CheckMode.HARD, 1.5);   // Больше VL за нарушение
        VL_MULTIPLIERS.put(CheckModeGUI.CheckMode.MEDIUM, 1.0); // Стандартное VL
        VL_MULTIPLIERS.put(CheckModeGUI.CheckMode.LITE, 0.5);   // Меньше VL за нарушение
        VL_MULTIPLIERS.put(CheckModeGUI.CheckMode.OFF, 0.0);    // Не добавлять VL
    }
    
    public CheckModifier(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Получить модифицированное значение для проверки в зависимости от режима игрока
     * @param player Игрок
     * @param baseValue Базовое значение
     * @param isThreshold Является ли значение порогом нарушения
     * @return Модифицированное значение
     */
    public double getModifiedValue(Player player, double baseValue, boolean isThreshold) {
        CheckModeGUI.CheckMode mode = plugin.getCheckModeGUI().getPlayerMode(player);
        
        // Применяем множитель в зависимости от режима
        double multiplier = getMultiplierForMode(mode, isThreshold);
        return baseValue * multiplier;
    }
    
    /**
     * Получить множитель для заданного режима
     * @param mode Режим проверки
     * @param isThreshold Является ли значение порогом нарушения
     * @return Множитель
     */
    private double getMultiplierForMode(CheckModeGUI.CheckMode mode, boolean isThreshold) {
        double thresholdMultiplier = THRESHOLD_MULTIPLIERS.getOrDefault(mode, MEDIUM_MULTIPLIER);
        
        if (isThreshold) {
            return thresholdMultiplier;
        } else {
            // Для не-порогов инвертируем множитель (кроме стандартного режима)
            return (mode == CheckModeGUI.CheckMode.MEDIUM) ? MEDIUM_MULTIPLIER : (1.0 / thresholdMultiplier);
        }
    }
    
    /**
     * Получить множитель для VL (уровня нарушений) в зависимости от режима
     * @param player Игрок
     * @return Множитель для VL
     */
    public double getViolationMultiplier(Player player) {
        CheckModeGUI.CheckMode mode = plugin.getCheckModeGUI().getPlayerMode(player);
        return VL_MULTIPLIERS.getOrDefault(mode, MEDIUM_MULTIPLIER);
    }
    
    /**
     * Проверить, активна ли данная проверка для игрока в зависимости от режима
     * @param player Игрок
     * @param checkType Тип проверки
     * @return true, если проверка активна
     */
    public boolean isCheckEnabled(Player player, String checkType) {
        CheckModeGUI.CheckMode mode = plugin.getCheckModeGUI().getPlayerMode(player);
        
        // Проверки отключены в режиме OFF
        if (mode == CheckModeGUI.CheckMode.OFF) {
            return false;
        }
        
        // В режиме LITE отключаем некоторые менее важные проверки
        if (mode == CheckModeGUI.CheckMode.LITE) {
            // Отключаем более сложные проверки в LITE режиме
            switch (checkType) {
                case "combat.criticals":
                case "movement.phase":
                case "world.xray":
                case "player.nuker":
                    return false;
                default:
                    break;
            }
        }
        
        // Проверяем, включена ли проверка в конфиге
        return plugin.getConfigManager().isCheckEnabled(checkType);
    }
} 