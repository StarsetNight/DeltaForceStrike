package org.starset.deltaforcestrike.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/dfs join|leave|info|reload|give <itemId>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("仅玩家可用");
                    return true;
                }
                plugin.getGameManager().getMatchManager().join(player);
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                plugin.getGameManager().getMatchManager().leave(player);
            }
            case "info" -> {
                ItemManager im = plugin.getItemManager();
                sender.sendMessage("§6已加载物品: §f" + im.getAll().size());
                sender.sendMessage("§7初始资金: §f" + plugin.getConfig().getInt("economy.start-money", 800));
                sender.sendMessage("§7购买阶段: §f" + plugin.getConfig().getInt("round.prepare-time", 15) + "s");
                if (sender instanceof Player player) {
                    boolean in = plugin.getGameManager().getMatchManager().isInMatch(player);
                    sender.sendMessage("§7队列状态: §f" + (in ? "已加入" : "未加入"));
                }
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
                if (!(sender instanceof Player player)) {
                    return true;
                }
                if (!sender.hasPermission("deltaforcestrike.admin")) {
                    sender.sendMessage("§c无权限");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /dfs give <itemId>");
                    return true;
                }
                giveItem(player, args[1]);
            }
            default -> sender.sendMessage("§c未知子命令");
        }
        return true;
    }

    private void giveItem(Player player, String id) {
        boolean ok = plugin.getItemGiveService().give(player, id, true);
        if (!ok) {
            player.sendMessage("§c发放失败: " + id + "（槽位满或未知物品）");
            return;
        }
        GameItem gi = plugin.getItemManager().getGameItem(id);
        if (gi != null && "shield".equalsIgnoreCase(gi.getType())) {
            player.sendMessage("§a已装备盾牌到副手: " + id);
        } else if (gi != null && gi.isArmorSet()) {
            player.sendMessage("§a已穿戴护甲: " + id);
        } else {
            player.sendMessage("§a已按固定槽位发放: " + id + "（每格 1 个）");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
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
        return src.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toList());
    }
}
