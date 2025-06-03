package com.anticheat.commands;

import com.anticheat.AntiCheatPlugin;
import com.anticheat.gui.CheckModeGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private final AntiCheatPlugin plugin;
    private final List<String> mainCommands = Arrays.asList("reload", "alerts", "check", "info", "reset", "exempt", "unexempt", "help", "gui", "modes");
    
    public AntiCheatCommand(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Проверка на доступ к команде
        if (!sender.hasPermission("anticheat.admin")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " У вас нет доступа к этой команде.");
            return true;
        }
        
        // Если аргументов нет, показываем общую информацию
        if (args.length == 0) {
            showInfo(sender);
            return true;
        }
        
        // Обработка подкоманды
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
                
            case "alerts":
                handleAlerts(sender);
                break;
                
            case "check":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Использование: /" + label + " check <игрок>");
                    return true;
                }
                handleCheck(sender, args[1]);
                break;
                
            case "info":
                showInfo(sender);
                break;
                
            case "reset":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Использование: /" + label + " reset <игрок> [проверка]");
                    return true;
                }
                handleReset(sender, args);
                break;
                
            case "exempt":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Использование: /" + label + " exempt <игрок>");
                    return true;
                }
                handleExempt(sender, args[1]);
                break;
                
            case "unexempt":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Использование: /" + label + " unexempt <игрок>");
                    return true;
                }
                handleUnexempt(sender, args[1]);
                break;
                
            case "help":
                showHelp(sender);
                break;
                
            case "gui":
            case "modes":
                handleGUI(sender, args);
                break;
                
            default:
                sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Неизвестная команда. Используйте /" + label + " help для списка команд.");
                break;
        }
        
        return true;
    }
    
    /**
     * Показать общую информацию о плагине
     */
    private void showInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "----------------------------------------");
        sender.sendMessage(ChatColor.RED + "AntiCheat v" + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Используйте /anticheat help для списка команд.");
        sender.sendMessage(ChatColor.GREEN + "----------------------------------------");
    }
    
    /**
     * Показать справку по командам
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "---------- AntiCheat Помощь ----------");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat reload " + ChatColor.WHITE + "- Перезагрузить плагин");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat alerts " + ChatColor.WHITE + "- Включить/выключить оповещения");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat check <игрок> " + ChatColor.WHITE + "- Проверить нарушения игрока");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat reset <игрок> [проверка] " + ChatColor.WHITE + "- Сбросить нарушения");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat exempt <игрок> " + ChatColor.WHITE + "- Исключить игрока из проверок");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat unexempt <игрок> " + ChatColor.WHITE + "- Вернуть игрока в проверки");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat gui " + ChatColor.WHITE + "- Открыть GUI для управления режимами проверки");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat modes [игрок] " + ChatColor.WHITE + "- Открыть GUI режимов проверки для игрока");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat info " + ChatColor.WHITE + "- Показать информацию о плагине");
        sender.sendMessage(ChatColor.YELLOW + "/anticheat help " + ChatColor.WHITE + "- Показать эту справку");
        sender.sendMessage(ChatColor.GREEN + "--------------------------------------");
    }
    
    /**
     * Обработка команды перезагрузки
     */
    private void handleReload(CommandSender sender) {
        plugin.reload();
        sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.GREEN + " Плагин перезагружен!");
    }
    
    /**
     * Обработка включения/выключения оповещений
     */
    private void handleAlerts(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Эта команда доступна только для игроков.");
            return;
        }
        
        Player player = (Player) sender;
        boolean enabled = plugin.getAlertManager().toggleAlerts(player);
        
        if (enabled) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.GREEN + " Оповещения включены!");
        } else {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.YELLOW + " Оповещения выключены!");
        }
    }
    
    /**
     * Обработка проверки игрока
     */
    private void handleCheck(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Игрок не найден.");
            return;
        }
        
        sender.sendMessage(ChatColor.GREEN + "-------- Нарушения игрока " + target.getName() + " --------");
        
        // Примеры проверок, которые могут быть
        String[] checkTypes = {
            "combat.killaura", "combat.reach", "combat.criticals", "combat.antikb",
            "movement.speed", "movement.fly", "movement.nofall", "movement.jesus", "movement.phase",
            "player.fastbreak", "player.fastplace", "player.nuker",
            "world.xray", "world.scaffold"
        };
        
        boolean hasViolations = false;
        
        for (String checkType : checkTypes) {
            int vl = plugin.getViolationManager().getViolations(target, checkType);
            if (vl > 0) {
                hasViolations = true;
                sender.sendMessage(ChatColor.RED + checkType + ": " + ChatColor.YELLOW + vl);
            }
        }
        
        if (!hasViolations) {
            sender.sendMessage(ChatColor.GREEN + "У игрока нет нарушений!");
        }
        
        // Вывод информации о режиме проверки
        CheckModeGUI.CheckMode mode = plugin.getCheckModeGUI().getPlayerMode(target);
        sender.sendMessage(ChatColor.YELLOW + "Текущий режим проверки: " + mode.getDisplayName());
        
        if (plugin.isExempt(target)) {
            sender.sendMessage(ChatColor.YELLOW + "Игрок освобожден от проверок!");
        }
        
        sender.sendMessage(ChatColor.GREEN + "----------------------------------------");
    }
    
    /**
     * Обработка сброса нарушений
     */
    private void handleReset(CommandSender sender, String[] args) {
        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Игрок не найден.");
            return;
        }
        
        if (args.length > 2) {
            String checkType = args[2];
            plugin.getViolationManager().resetViolations(target, checkType);
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.GREEN + " Нарушения для проверки " + checkType + " игрока " + target.getName() + " сброшены!");
        } else {
            plugin.getViolationManager().resetViolations(target);
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.GREEN + " Все нарушения игрока " + target.getName() + " сброшены!");
        }
    }
    
    /**
     * Обработка освобождения от проверок
     */
    private void handleExempt(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Игрок не найден.");
            return;
        }
        
        plugin.exemptPlayer(target);
        sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.GREEN + " Игрок " + target.getName() + " освобожден от проверок!");
    }
    
    /**
     * Обработка возврата в проверки
     */
    private void handleUnexempt(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Игрок не найден.");
            return;
        }
        
        plugin.unexemptPlayer(target);
        sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.GREEN + " Игрок " + target.getName() + " больше не освобожден от проверок!");
    }
    
    /**
     * Обработка открытия GUI
     */
    private void handleGUI(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Эта команда доступна только для игроков.");
            return;
        }
        
        Player player = (Player) sender;
        
        // Если указан конкретный игрок для изменения режима
        if (args.length > 1 && args[0].equalsIgnoreCase("modes")) {
            String targetName = args[1];
            Player target = Bukkit.getPlayer(targetName);
            
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + " Игрок не найден.");
                return;
            }
            
            // Открываем GUI режимов для конкретного игрока
            plugin.getCheckModeGUI().openPlayerModeGUI(player, target);
        } else {
            // Открываем главное GUI со списком игроков
            plugin.getCheckModeGUI().openMainGUI(player);
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("anticheat.admin")) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            return filterCompletions(mainCommands, args[0]);
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            
            if (Arrays.asList("check", "reset", "exempt", "unexempt", "modes").contains(subcommand)) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return filterCompletions(playerNames, args[1]);
            }
        } else if (args.length == 3) {
            if ("reset".equalsIgnoreCase(args[0])) {
                List<String> checkTypes = Arrays.asList(
                    "combat.killaura", "combat.reach", "combat.criticals", "combat.antikb",
                    "movement.speed", "movement.fly", "movement.nofall", "movement.jesus", "movement.phase",
                    "player.fastbreak", "player.fastplace", "player.nuker",
                    "world.xray", "world.scaffold"
                );
                return filterCompletions(checkTypes, args[2]);
            }
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Фильтр для автодополнения
     */
    private List<String> filterCompletions(List<String> completions, String partial) {
        List<String> filtered = new ArrayList<>();
        String lowercasePartial = partial.toLowerCase();
        
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(lowercasePartial)) {
                filtered.add(completion);
            }
        }
        
        return filtered;
    }
} 