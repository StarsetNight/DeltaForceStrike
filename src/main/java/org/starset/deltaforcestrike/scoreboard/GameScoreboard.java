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
import org.starset.deltaforcestrike.round.RoundState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
    }

    public void updateAll(Match match) {
        if (match == null) return;
        for (Player p : match.onlinePlayers()) update(p);
    }

    public void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            create(player);
            board = boards.get(player.getUniqueId());
            if (board == null) return;
        }

        Objective obj = board.getObjective("dfs");
        if (obj == null) {
            obj = board.registerNewObjective("dfs", Criteria.DUMMY,
                    Component.text("友谊之约", NamedTextColor.GOLD, TextDecoration.BOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        Match match = plugin.getMatchManager().getMatch();
        PlayerSession session = match == null ? null : match.getSession(player.getUniqueId());
        int line = 15;
        set(obj, line--, "§7--------------");

        if (match == null || session == null) {
            set(obj, line, "§7等待入队");
            return;
        }

        MatchState ms = match.getState();
        RoundState rs = match.getRoundManager().getState();
        int left = match.getRoundManager().getSecondsLeft();

        set(obj, line--, "§e" + stateLabel(ms, rs));
        if (ms == MatchState.IN_PROGRESS) {
            if (rs == RoundState.BUY) {
                set(obj, line--, "§e购买 §f" + left + "s");
            } else if (rs == RoundState.COMBAT) {
                set(obj, line--, "§c进攻 §f" + fmt(left));
            } else if (rs == RoundState.BOMB_PLANTED) {
                int fuse = plugin.getBombManager().getFuseLeft();
                set(obj, line--, "§4炸弹 §c" + fuse + "s");
            }
        }
        set(obj, line--, "§e回合 §f" + match.getCurrentRound());
        set(obj, line--, "§cT §f" + match.getScoreT() + " §7- §b" + match.getScoreCT() + " CT");
        set(obj, line--, "§7--------------");
        set(obj, line--, "§e队伍 " + team(session));
        set(obj, line--, "§e金钱 §a$" + session.getMoney());
        set(obj, line--, "§eK/D §f" + session.getKills() + "§7/§f" + session.getDeaths());
        set(obj, line, session.isAlive() ? "§a存活" : "§c阵亡");
    }

    private String team(PlayerSession s) {
        return switch (s.getTeam()) {
            case T -> "§cT";
            case CT -> "§bCT";
            default -> "§7-";
        };
    }

    private String stateLabel(MatchState ms, RoundState rs) {
        return switch (ms) {
            case WAITING -> "等待中";
            case COUNTDOWN -> "倒计时";
            case AGENT_SELECT -> "选干员";
            case IN_PROGRESS -> switch (rs) {
                case BUY -> "购买阶段";
                case COMBAT -> "战斗中";
                case BOMB_PLANTED -> "拆弹中";
                case ROUND_END -> "结算";
                default -> "对局中";
            };
            case ENDING -> "结束";
        };
    }

    private String fmt(int sec) {
        sec = Math.max(0, sec);
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    private void set(Objective obj, int score, String text) {
        if (text.length() > 40) text = text.substring(0, 40);
        obj.getScore(text + "§" + Integer.toHexString(score % 16) + "§r").setScore(score);
    }

    public void tick() {
        Match match = plugin.getMatchManager().getMatch();
        if (match != null) updateAll(match);
    }
}
