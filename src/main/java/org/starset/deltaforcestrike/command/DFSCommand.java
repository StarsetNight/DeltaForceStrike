package org.starset.deltaforcestrike.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.GameItem;
import org.starset.deltaforcestrike.item.ItemManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DFSCommand implements CommandExecutor, TabCompleter {

    private final DeltaForceStrike plugin;

    public DFSCommand(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/dfs join|leave|info|reload|give <itemId>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("仅玩家可用");
                    return true;
                }
                plugin.getGameManager().getMatchManager().join(p);
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) return true;
                plugin.getGameManager().getMatchManager().leave(p);
            }
            case "info" -> {
                ItemManager im = plugin.getItemManager();
                sender.sendMessage("§6已加载物品: §f" + im.getAll().size());
                sender.sendMessage("§7初始资金: §f" + plugin.getConfig().getInt("economy.start-money", 800));
                sender.sendMessage("§7购买阶段: §f" + plugin.getConfig().getInt("round.prepare-time", 15) + "s");
            }
            case "reload" -> {
                if (!sender.hasPermission("deltaforcestrike.admin")) {
                    sender.sendMessage("§c无权限");
                    return true;
                }
                plugin.reloadConfig();
                plugin.getItemManager().reload();
                sender.sendMessage("§a配置与物品已重载。物品数: " + plugin.getItemManager().getAll().size());
            }
            case "give" -> {
                if (!(sender instanceof Player p)) return true;
                if (!sender.hasPermission("deltaforcestrike.admin")) {
                    sender.sendMessage("§c无权限");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /dfs give <itemId>");
                    return true;
                }
                String id = args[1];
                GameItem gi = plugin.getItemManager().getGameItem(id);
                if (gi == null) {
                    p.sendMessage("§c未知物品: " + id);
                    return true;
                }
                if (gi.isArmorSet()) {
                    plugin.getItemManager().giveArmorSet(p, id);
                    p.sendMessage("§a已发放护甲套装: " + id);
                } else {
                    var stack = plugin.getItemManager().createItem(id);
                    if (stack != null) {
                        p.getInventory().addItem(stack);
                        p.sendMessage("§a已给予: " + id);
                    }
                }
            }
            default -> sender.sendMessage("§c未知子命令");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("join", "leave", "info", "reload", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(new ArrayList<>(plugin.getItemManager().getAll().keySet()), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> src, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return src.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
