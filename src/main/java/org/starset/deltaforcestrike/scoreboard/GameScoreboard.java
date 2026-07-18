package org.starset.deltaforcestrike.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.round.RoundState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每人一块侧边栏计分板（避免互相抢同一 Scoreboard）。
 */
public final class GameScoreboard {

    private final DeltaForceStrike plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public GameScoreboard(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void create(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(
                "dfs",
                Criteria.DUMMY,
                Component.text("友谊之约", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        update(player);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void removeAll() {
        for (UUID id : boards.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    public void updateAll(Match match) {
        if (match == null) return;
        for (Player p : match.onlinePlayers()) {
            update(p);
        }
    }

    public void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            create(player);
            board = boards.get(player.getUniqueId());
        }
        if (board == null) return;

        Objective obj = board.getObjective("dfs");
        if (obj == null) {
            obj = board.registerNewObjective(
                    "dfs",
                    Criteria.DUMMY,
                    Component.text("友谊之约", NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // 清空旧行
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        Match match = plugin.getMatchManager().getMatch();
        PlayerSession session = match == null ? null : match.getSession(player.getUniqueId());

        // 行号：分数越高越靠上，用 15→1
        int line = 15;
        setLine(obj, line--, "§7---------------");

        if (match == null || session == null) {
            setLine(obj, line--, "§7状态: §f无");
            setLine(obj, line, "§7等待入队…");
            return;
        }

        MatchState ms = match.getState();
        RoundState rs = match.getRoundManager().getState();

        setLine(obj, line--, "§e状态 §f" + stateLabel(ms, rs));
        setLine(obj, line--, "§e回合 §f" + match.getCurrentRound()
                + " §8/ §f" + plugin.getConfig().getInt("match.max-rounds", 8));
        setLine(obj, line--, "§cT §f" + match.getScoreT()
                + " §7- §b" + match.getScoreCT() + " §bCT");
        setLine(obj, line--, "§7---------------");

        String teamLabel = switch (session.getTeam()) {
            case T -> "§c进攻 T";
            case CT -> "§b防守 CT";
            default -> "§7未选队";
        };
        setLine(obj, line--, "§e队伍 " + teamLabel);
        setLine(obj, line--, "§e金钱 §a$" + session.getMoney());
        setLine(obj, line--, "§e战绩 §f" + session.getKills() + "§7/§f" + session.getDeaths());
        setLine(obj, line--, session.isAlive() ? "§a存活" : "§c阵亡·旁观");
        setLine(obj, line--, "§7---------------");

        // 半场提示
        int half = plugin.getConfig().getInt("match.half-round", 4);
        int cr = match.getCurrentRound();
        if (ms == MatchState.IN_PROGRESS && cr > 0 && cr < half) {
            setLine(obj, line, "§8半场换边: R" + half);
        } else if (ms == MatchState.IN_PROGRESS && cr >= half) {
            setLine(obj, line, "§8下半场");
        } else if (ms == MatchState.COUNTDOWN || ms == MatchState.WAITING) {
            setLine(obj, line, "§e队列 §f" + match.size()
                    + "/" + plugin.getConfig().getInt("queue.max-players", 6));
        }
    }

    private String stateLabel(MatchState ms, RoundState rs) {
        return switch (ms) {
            case WAITING -> "等待中";
            case COUNTDOWN -> "倒计时";
            case AGENT_SELECT -> "选干员";
            case IN_PROGRESS -> switch (rs) {
                case BUY -> "购买阶段";
                case COMBAT -> "战斗中";
                case ROUND_END -> "回合结算";
                default -> "对局中";
            };
            case ENDING -> "结束中";
        };
    }

    /**
     * Bukkit 侧边栏同一分数只能一行；用不可见后缀区分。
     */
    private void setLine(Objective obj, int score, String text) {
        // 截断过长
        if (text.length() > 40) {
            text = text.substring(0, 40);
        }
        // 保证 entry 唯一
        String entry = text + uniqueSuffix(score);
        obj.getScore(entry).setScore(score);
    }

    private String uniqueSuffix(int score) {
        // 用 §r 与颜色代码做不可见区分
        return "§" + Integer.toHexString(score % 16) + "§r";
    }

    /** 定时刷新入口 */
    public void tick() {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) return;
        updateAll(match);
    }
}
