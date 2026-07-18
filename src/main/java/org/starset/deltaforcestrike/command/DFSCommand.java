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
import org.starset.deltaforcestrike.shop.ShopGUI;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DFSCommand implements CommandExecutor, TabCompleter {

    private final DeltaForceStrike plugin;

    private static final List<String> ROOT = List.of(
            "join", "leave", "team", "agent", "info", "shop",
            "start", "stop", "give", "reload", "setspawn", "setsite", "help"
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
            case "shop" -> shop(sender);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "reload" -> reload(sender);
            case "give" -> give(sender, args);
            case "setspawn" -> setSpawn(sender, args);
            case "setsite" -> setSite(sender, args);
            case "help" -> help(sender);
            default -> sender.sendMessage("§c未知子命令。§7 /dfs help");
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6§l--- DeltaForceStrike ---");
        sender.sendMessage("§e/dfs join|leave|team|shop|info");
        if (sender.hasPermission("deltaforcestrike.admin")) {
            sender.sendMessage("§c/dfs start|stop|reload|give|setspawn|setsite");
        }
    }

    private void join(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家");
            return;
        }
        plugin.getMatchManager().tryJoin(p);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        if (!plugin.getMatchManager().isInMatch(p)) {
            p.sendMessage("§7不在队列中");
            return;
        }
        plugin.getMatchManager().leave(p);
        p.sendMessage("§e[DFS] 已离开");
    }

    private void team(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (args.length < 2) {
            p.sendMessage("§c/dfs team <t|ct>");
            return;
        }
        Team team = parseTeam(args[1]);
        if (team == Team.NONE) {
            p.sendMessage("§c t 或 ct");
            return;
        }
        plugin.getMatchManager().trySelectTeam(p, team);
    }

    private void agent(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (args.length < 2) {
            p.sendMessage("§c/dfs agent <id>");
            return;
        }
        plugin.getMatchManager().trySelectOperator(p, args[1]);
    }

    private void shop(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家");
            return;
        }
        if (!Worlds.isArena(p)) {
            p.sendMessage("§c只能在竞技世界使用商店");
            return;
        }
        if (!plugin.getMatchManager().isInMatch(p)) {
            p.sendMessage("§c你不在对局中");
            return;
        }
        ShopGUI.open(p);
    }

    private void info(CommandSender sender) {
        sender.sendMessage("§6=== DFS ===");
        sender.sendMessage("§7" + plugin.getMatchManager().statusLine());
        if (sender instanceof Player p) {
            Match m = plugin.getMatchManager().getMatch();
            PlayerSession s = m == null ? null : m.getSession(p.getUniqueId());
            if (s != null) {
                sender.sendMessage("§7队伍:" + s.getTeam() + " 资金:$" + s.getMoney()
                        + " K/D:" + s.getKills() + "/" + s.getDeaths());
            }
        }
    }

    private void start(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.getMatchManager().forceStartCountdown();
        sender.sendMessage("§a已开始倒计时");
    }

    private void stop(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.getMatchManager().forceEnd("管理员终止");
    }

    private void reload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadConfig();
        plugin.getItemManager().reload();
        plugin.getGameRulesService().applyToArenaWorld();
        sender.sendMessage("§a已重载");
    }

    private void give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (!admin(sender)) return;
        if (args.length < 2) {
            p.sendMessage("§c/dfs give <itemId>");
            return;
        }
        boolean ok = plugin.getItemGiveService().give(p, args[1], true);
        p.sendMessage(ok ? "§a已发放 " + args[1] : "§c失败 " + args[1]);
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (!admin(sender)) return;
        if (args.length < 2) {
            p.sendMessage("§c/dfs setspawn <queue|t|ct>");
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
        writeLoc(p, path);
    }

    private void setSite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (!admin(sender)) return;
        if (args.length < 2) {
            p.sendMessage("§c/dfs setsite <a|b|名称> [radius]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        double radius = 4.0;
        if (args.length >= 3) {
            try {
                radius = Double.parseDouble(args[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        Location loc = p.getLocation();
        String path = "bomb.sites." + id;
        var cfg = plugin.getConfig();
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".radius", radius);
        plugin.saveConfig();
        p.sendMessage(String.format("§a包点 §e%s §ar=%.1f @ %.1f %.1f %.1f",
                id, radius, loc.getX(), loc.getY(), loc.getZ()));
    }

    private void writeLoc(Player p, String path) {
        Location loc = p.getLocation();
        var cfg = plugin.getConfig();
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", (double) loc.getYaw());
        cfg.set(path + ".pitch", (double) loc.getPitch());
        plugin.saveConfig();
        p.sendMessage(String.format("§a已保存 %s → %.2f %.2f %.2f",
                path, loc.getX(), loc.getY(), loc.getZ()));
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("deltaforcestrike.admin")) return true;
        sender.sendMessage("§c无权限");
        return false;
    }

    private Team parseTeam(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "t", "atk", "attack" -> Team.T;
            case "ct", "def", "defend" -> Team.CT;
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
                        "start", "stop", "reload", "give", "setspawn", "setsite"
                ).contains(s));
            }
            return filter(stream.collect(Collectors.toList()), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "team" -> filter(List.of("t", "ct"), args[1]);
                case "setspawn" -> filter(List.of("queue", "t", "ct"), args[1]);
                case "setsite" -> filter(List.of("a", "b"), args[1]);
                case "give" -> filter(new ArrayList<>(plugin.getItemManager().getAll().keySet()), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> filter(List<String> src, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return src.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
