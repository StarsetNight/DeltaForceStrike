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
import org.starset.deltaforcestrike.operator.OperatorDefinition;
import org.starset.deltaforcestrike.shop.ShopGUI;
import org.starset.deltaforcestrike.util.GameGuide;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DFSCommand implements CommandExecutor, TabCompleter {

    private final DeltaForceStrike plugin;

    private static final List<String> ROOT = List.of(
            "join", "leave", "team", "agent", "info", "shop", "guide",
            "start", "stop", "give", "reload", "config", "setspawn", "setsite", "help"
    );

    /** 可热改的对局/队列参数（path → 类型） */
    private static final List<String> CONFIG_KEYS = List.of(
            "team-size",
            "max-players",
            "cancel-if-not-full",
            "half-round",
            "win-target",
            "max-rounds",
            "friendly-fire"
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
            case "guide" -> guide(sender);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "reload" -> reload(sender);
            case "config", "cfg", "set" -> config(sender, args);
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
        sender.sendMessage("§e/dfs join|leave|team|shop|guide|info|agent");
        if (sender.hasPermission("deltaforcestrike.admin")) {
            sender.sendMessage("§c/dfs start|stop|reload|config|give|setspawn|setsite");
            sender.sendMessage("§7/dfs config [key] [value]  §8改队列/赛制并保存");
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
        if (!(sender instanceof Player p)) {
            return;
        }
        if (!plugin.getMatchManager().isInMatch(p)) {
            p.sendMessage("§7不在队列中");
            return;
        }
        plugin.getMatchManager().leave(p);
        p.sendMessage("§e[DFS] 已离开");
    }

    private void team(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
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
        if (!(sender instanceof Player p)) {
            return;
        }
        if (args.length < 2) {
            // 打开干员选择 GUI
            org.starset.deltaforcestrike.util.OperatorSelectUI.open(p);
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

    private void guide(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家");
            return;
        }
        GameGuide.send(p);
    }

    private void info(CommandSender sender) {
        sender.sendMessage("§6=== DFS ===");
        sender.sendMessage("§7" + plugin.getMatchManager().statusLine());
        if (sender instanceof Player p) {
            Match m = plugin.getMatchManager().getMatch();
            PlayerSession s = m == null ? null : m.getSession(p.getUniqueId());
            if (s != null) {
                sender.sendMessage("§7队伍:" + s.getTeam()
                        + " 资金:$" + s.getMoney()
                        + " K/D:" + s.getKills() + "/" + s.getDeaths()
                        + " 干员:" + (s.getOperatorId() == null ? "无" : s.getOperatorId()));
            }
        }
    }

    private void start(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        plugin.getMatchManager().forceStartCountdown();
        sender.sendMessage("§a已开始倒计时");
    }

    private void stop(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        plugin.getMatchManager().forceEnd("管理员终止");
    }

    private void reload(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        plugin.reloadConfig();
        plugin.getItemManager().reload();
        plugin.getGameRulesService().applyToArenaWorld();
        if (plugin.getOperatorService() != null) {
            plugin.getOperatorService().reload();
        }
        if (plugin.getBombSiteMarkerService() != null) {
            plugin.getBombSiteMarkerService().respawnAll();
        }
        sender.sendMessage("§a已重载配置/物品/干员。物品:"
                + plugin.getItemManager().getAll().size()
                + " 干员:"
                + (plugin.getOperatorService() == null ? 0
                : plugin.getOperatorService().getRegistry().allUnique().size())
                + " §7友伤:" + plugin.getConfig().getBoolean("match.friendly-fire", false));
    }

    /**
     * /dfs config                     查看全部
     * /dfs config &lt;key&gt;               查看单项
     * /dfs config &lt;key&gt; &lt;value&gt;       修改并 saveConfig
     */
    private void config(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length == 1) {
            showAllConfig(sender);
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT).replace('_', '-');
        String path = configPathOf(key);
        if (path == null) {
            sender.sendMessage("§c未知键。可用: §e" + String.join("§7, §e", CONFIG_KEYS));
            return;
        }
        if (args.length == 2) {
            sender.sendMessage("§6[DFS] §e" + key + " §7= §f" + formatConfigValue(path, key)
                    + " §8(" + path + ")");
            return;
        }

        String valueRaw = args[2];
        if (!applyConfigValue(sender, key, path, valueRaw)) {
            return;
        }
        plugin.saveConfig();
        sender.sendMessage("§a[DFS] 已设置 §e" + key + " §7= §f" + formatConfigValue(path, key)
                + " §8并写入 config.yml");
        sender.sendMessage("§7当前队列/赛制: §fteam-size="
                + plugin.getConfig().getInt("queue.team-size")
                + " max-players=" + plugin.getConfig().getInt("queue.max-players")
                + " half=" + plugin.getConfig().getInt("match.half-round")
                + " win=" + plugin.getConfig().getInt("match.win-target")
                + " maxR=" + plugin.getConfig().getInt("match.max-rounds"));
    }

    private void showAllConfig(CommandSender sender) {
        sender.sendMessage("§6§l[DFS] 可改参数 §7(/dfs config <key> <value>)");
        for (String key : CONFIG_KEYS) {
            String path = configPathOf(key);
            if (path == null) {
                continue;
            }
            sender.sendMessage("§e" + key + " §7= §f" + formatConfigValue(path, key)
                    + " §8→ " + path);
        }
        sender.sendMessage("§7例: §f/dfs config max-players 10");
        sender.sendMessage("§7例: §f/dfs config cancel-if-not-full false");
        sender.sendMessage("§7例: §f/dfs config friendly-fire false");
    }

    private static String configPathOf(String key) {
        return switch (key) {
            case "team-size", "teamsize" -> "queue.team-size";
            case "max-players", "maxplayers", "max-player" -> "queue.max-players";
            case "cancel-if-not-full", "cancelifnotfull", "cancel-full" -> "queue.cancel-if-not-full";
            case "half-round", "halfround", "half" -> "match.half-round";
            case "win-target", "wintarget", "win" -> "match.win-target";
            case "max-rounds", "maxrounds", "rounds" -> "match.max-rounds";
            case "friendly-fire", "friendlyfire", "ff" -> "match.friendly-fire";
            default -> null;
        };
    }

    private String formatConfigValue(String path, String key) {
        var cfg = plugin.getConfig();
        if (key.contains("cancel") || key.contains("friendly") || path.contains("friendly-fire")
                || path.endsWith("cancel-if-not-full")) {
            return String.valueOf(cfg.getBoolean(path));
        }
        return String.valueOf(cfg.getInt(path));
    }

    private boolean applyConfigValue(CommandSender sender, String key, String path, String raw) {
        var cfg = plugin.getConfig();
        if (path.equals("queue.cancel-if-not-full") || path.equals("match.friendly-fire")) {
            Boolean b = parseBoolean(raw);
            if (b == null) {
                sender.sendMessage("§c取值须为 true/false（或 on/off、1/0）");
                return false;
            }
            cfg.set(path, b);
            if (path.equals("match.friendly-fire")) {
                sender.sendMessage(b ? "§e友方伤害已 §a开启" : "§e友方伤害已 §c关闭");
            }
            return true;
        }

        int n;
        try {
            n = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            sender.sendMessage("§c请输入整数");
            return false;
        }

        switch (key) {
            case "team-size", "teamsize" -> {
                if (n < 1 || n > 32) {
                    sender.sendMessage("§cteam-size 范围 1–32");
                    return false;
                }
                cfg.set(path, n);
                // 总人数未显式够用时提示
                int max = cfg.getInt("queue.max-players", n * 2);
                if (max < n * 2) {
                    sender.sendMessage("§e提示: max-players=" + max + " < team-size×2="
                            + (n * 2) + "，建议同步调高");
                }
            }
            case "max-players", "maxplayers", "max-player" -> {
                if (n < 2 || n > 64) {
                    sender.sendMessage("§cmax-players 范围 2–64");
                    return false;
                }
                cfg.set(path, n);
            }
            case "half-round", "halfround", "half" -> {
                if (n < 1 || n > 30) {
                    sender.sendMessage("§chalf-round 范围 1–30");
                    return false;
                }
                cfg.set(path, n);
            }
            case "win-target", "wintarget", "win" -> {
                if (n < 1 || n > 30) {
                    sender.sendMessage("§cwin-target 范围 1–30");
                    return false;
                }
                cfg.set(path, n);
            }
            case "max-rounds", "maxrounds", "rounds" -> {
                if (n < 1 || n > 60) {
                    sender.sendMessage("§cmax-rounds 范围 1–60");
                    return false;
                }
                cfg.set(path, n);
            }
            default -> {
                sender.sendMessage("§c未知键");
                return false;
            }
        }
        return true;
    }

    private static Boolean parseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1", "y" -> true;
            case "false", "no", "off", "0", "n" -> false;
            default -> null;
        };
    }

    private void give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            p.sendMessage("§c/dfs give <itemId>");
            return;
        }
        boolean ok = plugin.getItemGiveService().give(p, args[1], true);
        p.sendMessage(ok ? "§a已发放 " + args[1] : "§c失败 " + args[1]);
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (!admin(sender)) {
            return;
        }
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
        if (!(sender instanceof Player p)) {
            return;
        }
        if (!admin(sender)) {
            return;
        }
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
        if (plugin.getBombSiteMarkerService() != null) {
            plugin.getBombSiteMarkerService().respawnAll();
        }
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
        if (sender.hasPermission("deltaforcestrike.admin")) {
            return true;
        }
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
                        "start", "stop", "reload", "config", "give", "setspawn", "setsite"
                ).contains(s));
            }
            return filter(stream.collect(Collectors.toList()), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "team" -> filter(List.of("t", "ct"), args[1]);
                case "agent" -> filter(List.of(
                        "niko", "bruo", "aier", "wulong", "妮可", "布若", "艾尔", "骛龙"
                ), args[1]);
                case "setspawn" -> filter(List.of("queue", "t", "ct"), args[1]);
                case "setsite" -> filter(List.of("a", "b"), args[1]);
                case "config", "cfg", "set" -> filter(CONFIG_KEYS, args[1]);
                case "give" -> filter(new ArrayList<>(plugin.getItemManager().getAll().keySet()), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3
                && List.of("config", "cfg", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
            String k = args[1].toLowerCase(Locale.ROOT).replace('_', '-');
            if (k.contains("cancel") || k.contains("friendly") || k.equals("ff")) {
                return filter(List.of("true", "false"), args[2]);
            }
            return filter(List.of("1", "2", "3", "4", "5", "6", "8", "10", "12", "16"), args[2]);
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
