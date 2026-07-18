package org.starset.deltaforcestrike.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DFSCommand implements CommandExecutor, TabCompleter {

    private final DeltaForceStrike plugin;

    private static final List<String> ROOT = List.of(
            "join", "leave", "team", "agent", "info",
            "start", "stop", "give", "reload", "shop", "setspawn"
    );

    public DFSCommand(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> join(sender);
            case "leave" -> leave(sender);
            case "team" -> team(sender, args);
            case "agent" -> agent(sender, args);
            case "info" -> info(sender);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "reload" -> reload(sender);
            case "give" -> give(sender, args);
            case "shop" -> sender.sendMessage("§7商店 GUI 尚未实装。");
            case "setspawn" -> setSpawn(sender, args);
            case "help" -> help(sender);
            default -> sender.sendMessage("§c未知子命令。§7输入 /dfs help");
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6§l--- DeltaForceStrike ---");
        sender.sendMessage("§e/dfs join|leave §7队列");
        sender.sendMessage("§e/dfs team <t|ct> §7选队");
        sender.sendMessage("§e/dfs info §7状态/资金");
        sender.sendMessage("§e/dfs agent <id> §7干员(未开放)");
        if (sender.hasPermission("deltaforcestrike.admin")) {
            sender.sendMessage("§c/dfs start|stop|reload|give|setspawn");
        }
    }

    private void join(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家可用");
            return;
        }
        plugin.getMatchManager().tryJoin(p);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        if (!plugin.getMatchManager().isInMatch(p)) {
            p.sendMessage("§7你不在队列中。");
            return;
        }
        plugin.getMatchManager().leave(p);
        p.sendMessage("§e[DFS] 已离开。");
    }

    private void team(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (args.length < 2) {
            p.sendMessage("§c用法: /dfs team <t|ct>");
            return;
        }
        Team team = parseTeam(args[1]);
        if (team == Team.NONE) {
            p.sendMessage("§c队伍请使用 t 或 ct");
            return;
        }
        plugin.getMatchManager().trySelectTeam(p, team);
        plugin.getScoreboardService().update(p);
    }

    private void agent(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (args.length < 2) {
            p.sendMessage("§c用法: /dfs agent <id>");
            return;
        }
        plugin.getMatchManager().trySelectOperator(p, args[1]);
    }

    private void info(CommandSender sender) {
        sender.sendMessage("§6§l=== DFS 状态 ===");
        sender.sendMessage("§7竞技世界: §f" + Worlds.arenaName());
        sender.sendMessage("§7对局: §f" + plugin.getMatchManager().statusLine());

        if (!(sender instanceof Player p)) return;

        sender.sendMessage("§7你的世界: §f" + p.getWorld().getName()
                + (Worlds.isArena(p) ? " §a[竞技]" : " §c[非竞技]"));

        Match m = plugin.getMatchManager().getMatch();
        PlayerSession s = m == null ? null : m.getSession(p.getUniqueId());
        if (s == null) {
            sender.sendMessage("§7队列: §c未加入");
            return;
        }
        sender.sendMessage("§7队伍: §f" + s.getTeam()
                + " §7| 资金: §e$" + s.getMoney()
                + " §7| K/D: §f" + s.getKills() + "/" + s.getDeaths()
                + " §7| " + (s.isAlive() ? "§a存活" : "§c阵亡"));
        plugin.getScoreboardService().update(p);
    }

    private void start(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.getMatchManager().forceStartCountdown();
        sender.sendMessage("§a[DFS] 已强制进入倒计时（请确认 max-players / cancel-if-not-full）。");
    }

    private void stop(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.getMatchManager().forceEnd("管理员终止");
        sender.sendMessage("§c[DFS] 对局已终止。");
    }

    private void reload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadConfig();
        plugin.getItemManager().reload();
        plugin.getGameRulesService().applyToArenaWorld();
        sender.sendMessage("§a[DFS] 配置与物品已重载。物品数: "
                + plugin.getItemManager().getAll().size());
        Match m = plugin.getMatchManager().getMatch();
        if (m != null) {
            plugin.getScoreboardService().updateAll(m);
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (!admin(sender)) return;
        if (args.length < 2) {
            p.sendMessage("§c用法: /dfs give <itemId>");
            return;
        }
        boolean ok = plugin.getItemGiveService().give(p, args[1], true);
        p.sendMessage(ok ? "§a已发放: " + args[1] : "§c失败: " + args[1] + "（满槽/未知）");
        plugin.getScoreboardService().update(p);
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (!admin(sender)) return;
        if (args.length < 2) {
            p.sendMessage("§c用法: /dfs setspawn <queue|t|ct>");
            return;
        }
        String path = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "queue", "lobby", "wait" -> "locations.queue-spawn";
            case "t", "atk" -> "locations.team-t-spawn";
            case "ct", "def" -> "locations.team-ct-spawn";
            default -> null;
        };
        if (path == null) {
            p.sendMessage("§c/dfs setspawn <queue|t|ct>");
            return;
        }
        Location loc = p.getLocation();
        var cfg = plugin.getConfig();
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", (double) loc.getYaw());
        cfg.set(path + ".pitch", (double) loc.getPitch());
        plugin.saveConfig();
        p.sendMessage(String.format(
                "§a已保存 %s → %.2f %.2f %.2f @ %s",
                path, loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName()
        ));
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("deltaforcestrike.admin")) return true;
        sender.sendMessage("§c无权限");
        return false;
    }

    private Team parseTeam(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "t", "terrorist", "attack", "atk" -> Team.T;
            case "ct", "counter", "defend", "def" -> Team.CT;
            default -> Team.NONE;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            Stream<String> stream = ROOT.stream();
            if (!sender.hasPermission("deltaforcestrike.admin")) {
                stream = stream.filter(s -> !List.of(
                        "start", "stop", "reload", "give", "setspawn"
                ).contains(s));
            }
            return filter(stream.collect(Collectors.toList()), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "team" -> filter(List.of("t", "ct"), args[1]);
                case "setspawn" -> filter(List.of("queue", "t", "ct"), args[1]);
                case "give" -> filter(new ArrayList<>(plugin.getItemManager().getAll().keySet()), args[1]);
                default -> List.of();
            };
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
