package org.starset.deltaforcestrike.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;

/**
 * 隐藏敌方头顶铭牌。
 * 在每个观众自己的 Scoreboard 上分组：
 * NAME_TAG_VISIBILITY = FOR_OTHER_TEAMS → 敌方看不到己方铭牌。
 * <p>
 * 使用 Adventure API，避免已弃用的 ChatColor / setPrefix(String) / setColor(ChatColor)。
 */
public class NametagService {

    private static final String TEAM_T = "dfs_t";
    private static final String TEAM_CT = "dfs_ct";
    private static final String TEAM_SPEC = "dfs_spec";

    private final DeltaForceStrike plugin;
    /** 每个观众的分组签名：成员集合未变化时跳过全量重建（每秒 tick 不再刷包/闪烁） */
    private final java.util.Map<java.util.UUID, String> lastSignature = new java.util.HashMap<>();

    public NametagService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void applyForViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        Scoreboard board = viewer.getScoreboard();
        var manager = Bukkit.getScoreboardManager();
        if (manager == null || board.equals(manager.getMainScoreboard())) {
            return;
        }

        Match match = plugin.getMatchManager().getMatch();
        if (match == null || !match.contains(viewer.getUniqueId())) {
            if (lastSignature.remove(viewer.getUniqueId()) != null) {
                clearTeams(board);
            }
            return;
        }

        // 计算期望分组；与上次一致则跳过重建
        StringBuilder sig = new StringBuilder();
        java.util.TreeMap<String, String> desired = new java.util.TreeMap<>();
        for (Player p : match.onlinePlayers()) {
            PlayerSession session = match.getSession(p.getUniqueId());
            String entry = p.getName();
            String group;
            if (session == null || !session.hasTeam()
                    || !session.isAlive() || p.getGameMode() == GameMode.SPECTATOR) {
                group = TEAM_SPEC;
            } else {
                group = switch (session.getTeam()) {
                    case T -> TEAM_T;
                    case CT -> TEAM_CT;
                    default -> TEAM_SPEC;
                };
            }
            desired.put(entry, group);
            sig.append(entry).append('>').append(group).append(';');
        }
        String sigStr = sig.toString();
        if (sigStr.equals(lastSignature.get(viewer.getUniqueId()))) {
            return;
        }
        lastSignature.put(viewer.getUniqueId(), sigStr);

        Team tTeam = getOrCreate(board, TEAM_T);
        Team ctTeam = getOrCreate(board, TEAM_CT);
        Team specTeam = getOrCreate(board, TEAM_SPEC);

        configureTeam(tTeam, NamedTextColor.RED, Component.text("[T] ", NamedTextColor.RED), true);
        configureTeam(ctTeam, NamedTextColor.AQUA, Component.text("[CT] ", NamedTextColor.AQUA), true);
        configureTeam(specTeam, NamedTextColor.GRAY, Component.empty(), false);

        // 敌方隐藏铭牌；旁观对所有人隐藏
        tTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
        ctTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
        specTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        tTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        ctTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        specTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        clearEntries(tTeam);
        clearEntries(ctTeam);
        clearEntries(specTeam);

        for (java.util.Map.Entry<String, String> e : desired.entrySet()) {
            Team target = switch (e.getValue()) {
                case TEAM_T -> tTeam;
                case TEAM_CT -> ctTeam;
                default -> specTeam;
            };
            addEntry(target, e.getKey());
        }
    }

    public void applyAll(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            applyForViewer(p);
        }
    }

    public void reset(Player player) {
        if (player == null) {
            return;
        }
        lastSignature.remove(player.getUniqueId());
        Scoreboard board = player.getScoreboard();
        if (board != null && !board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            clearTeams(board);
        }
    }

    // ------------------------------------------------------------------

    private Team getOrCreate(Scoreboard board, String name) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        return team;
    }

    /**
     * Paper/Adventure：用 color(NamedTextColor) 与 prefix(Component)，
     * 避免 ChatColor / setPrefix(String) / setColor(ChatColor)。
     */
    private void configureTeam(Team team, NamedTextColor color, Component prefix, boolean friendlyInvis) {
        if (team == null) {
            return;
        }

        // Paper 1.13+ Adventure Team API
        try {
            team.color(color);
        } catch (Throwable t) {
            // 极旧桥接：忽略颜色
            plugin.getLogger().fine("[Nametag] team.color 不可用: " + t.getMessage());
        }

        try {
            team.prefix(prefix == null ? Component.empty() : prefix);
            team.suffix(Component.empty());
        } catch (Throwable t) {
            plugin.getLogger().fine("[Nametag] team.prefix 不可用: " + t.getMessage());
        }

        team.setCanSeeFriendlyInvisibles(friendlyInvis);
        // 与 match.friendly-fire 配置一致（默认关友伤）
        boolean ff = plugin.getConfig().getBoolean("match.friendly-fire", false);
        team.setAllowFriendlyFire(ff);
    }

    private void addEntry(Team team, String entry) {
        if (team == null || entry == null || entry.isEmpty()) {
            return;
        }
        try {
            Scoreboard board = team.getScoreboard();
            if (board != null) {
                Team old = board.getEntryTeam(entry);
                if (old != null && !old.equals(team)) {
                    old.removeEntry(entry);
                }
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("[Nametag] addEntry " + entry + ": " + t.getMessage());
        }
    }

    private void clearEntries(Team team) {
        if (team == null) {
            return;
        }
        for (String e : new java.util.HashSet<>(team.getEntries())) {
            try {
                team.removeEntry(e);
            } catch (Throwable ignored) {
            }
        }
    }

    private void clearTeams(Scoreboard board) {
        if (board == null) {
            return;
        }
        for (String name : new String[]{TEAM_T, TEAM_CT, TEAM_SPEC}) {
            Team t = board.getTeam(name);
            if (t != null) {
                clearEntries(t);
            }
        }
    }
}
