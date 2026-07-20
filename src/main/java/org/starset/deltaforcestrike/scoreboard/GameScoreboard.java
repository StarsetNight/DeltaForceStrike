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

/**
 * 每人一块侧边栏计分板；与 {@link NametagService} 共用该板子隐藏敌方铭牌。
 * 侧边栏行内容使用 § 时通过 setScore 字符串 API，避免 Adventure Component 混 § 警告。
 */
public final class GameScoreboard {

    private static final String OBJECTIVE_NAME = "dfs";

    private final DeltaForceStrike plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public GameScoreboard(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void create(Player player) {
        if (player == null) {
            return;
        }
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                Component.text("友谊之约", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);

        // 铭牌：每人板子都要挂上全员队伍关系
        Match match = plugin.getMatchManager().getMatch();
        if (plugin.getNametagService() != null && match != null) {
            plugin.getNametagService().applyAll(match);
        }

        update(player);
    }

    public void remove(Player player) {
        if (player == null) {
            return;
        }
        if (plugin.getNametagService() != null) {
            plugin.getNametagService().reset(player);
        }
        boards.remove(player.getUniqueId());
        var manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    public void removeAll() {
        var manager = Bukkit.getScoreboardManager();
        for (UUID id : boards.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                if (plugin.getNametagService() != null) {
                    plugin.getNametagService().reset(p);
                }
                if (manager != null) {
                    p.setScoreboard(manager.getMainScoreboard());
                }
            }
        }
        boards.clear();
    }

    public void updateAll(Match match) {
        if (match == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            update(p);
        }
        if (plugin.getNametagService() != null) {
            plugin.getNametagService().applyAll(match);
        }
    }

    public void update(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            create(player);
            board = boards.get(player.getUniqueId());
            if (board == null) {
                return;
            }
        }

        // 确保仍使用我们的板子
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }

        Objective obj = board.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(
                    OBJECTIVE_NAME,
                    Criteria.DUMMY,
                    Component.text("友谊之约", NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        clearSidebarScores(board);

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
                set(obj, line--, "§e购买 §f" + Math.max(0, left) + "s");
            } else if (rs == RoundState.COMBAT) {
                set(obj, line--, "§c进攻 §f" + fmt(left));
            } else if (rs == RoundState.BOMB_PLANTED) {
                int fuse = plugin.getBombManager() != null
                        ? plugin.getBombManager().getFuseLeft() : left;
                set(obj, line--, "§4炸弹 §c" + Math.max(0, fuse) + "s");
            }
        } else if (ms == MatchState.COUNTDOWN) {
            set(obj, line--, "§6倒计时中");
        } else if (ms == MatchState.WAITING) {
            set(obj, line--, "§e队列 §f" + match.size()
                    + "/" + plugin.getConfig().getInt("queue.max-players", 6));
        }

        set(obj, line--, "§e回合 §f" + match.getCurrentRound());
        set(obj, line--, "§cT §f" + match.getScoreT() + " §7- §b" + match.getScoreCT() + " CT");
        set(obj, line--, "§7--------------");
        set(obj, line--, "§e队伍 " + teamLabel(session));
        set(obj, line--, "§e金钱 §a$" + session.getMoney());
        set(obj, line--, "§eK/D §f" + session.getKills() + "§7/§f" + session.getDeaths());

        String op = session.getOperatorId();
        if (op != null && !op.isEmpty()) {
            set(obj, line--, "§d干员 §f" + op);
        }

        set(obj, line--, session.isAlive() ? "§a存活" : "§c阵亡");

        int half = plugin.getConfig().getInt("match.half-round", 4);
        int cr = match.getCurrentRound();
        if (ms == MatchState.IN_PROGRESS && cr > 0 && cr < half) {
            set(obj, line, "§8半场: R" + half);
        } else if (ms == MatchState.IN_PROGRESS && cr >= half) {
            set(obj, line, "§8下半场");
        }
    }

    public void tick() {
        Match match = plugin.getMatchManager().getMatch();
        if (match != null) {
            updateAll(match);
        }
    }

    // ------------------------------------------------------------------

    private String stateLabel(MatchState ms, RoundState rs) {
        return switch (ms) {
            case WAITING -> "等待中";
            case COUNTDOWN -> "倒计时";
            case AGENT_SELECT -> "选干员";
            case IN_PROGRESS -> switch (rs) {
                case BUY -> "购买阶段";
                case COMBAT -> "进攻阶段";
                case BOMB_PLANTED -> "炸弹已安放";
                case ROUND_END -> "结算";
                default -> "对局中";
            };
            case ENDING -> "结束中";
        };
    }

    private String teamLabel(PlayerSession s) {
        return switch (s.getTeam()) {
            case T -> "§cT";
            case CT -> "§bCT";
            default -> "§7-";
        };
    }

    private String fmt(int sec) {
        sec = Math.max(0, sec);
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    /**
     * 侧边栏分数行：用唯一不可见后缀，避免与玩家名队伍 entry 冲突。
     * Bukkit score entry 支持 § 颜色，不会走 Adventure Component 警告。
     */
    private void set(Objective obj, int score, String text) {
        if (obj == null) {
            return;
        }
        if (text == null) {
            text = "";
        }
        if (text.length() > 40) {
            text = text.substring(0, 40);
        }
        String entry = text + sidebarSuffix(score);
        obj.getScore(entry).setScore(score);
    }

    private String sidebarSuffix(int score) {
        // 唯一且尽量不可见
        return "§r§" + Integer.toHexString(Math.floorMod(score, 16)) + "§r";
    }

    private boolean isSidebarEntry(String entry) {
        // 我们的侧边栏 entry 以 §r 后缀结尾；玩家名通常不含
        return entry != null && entry.contains("§r");
    }

    private void clearSidebarScores(Scoreboard board) {
        Objective obj = board.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            return;
        }
        for (String entry : new java.util.HashSet<>(board.getEntries())) {
            if (isSidebarEntry(entry)) {
                board.resetScores(entry);
            }
        }
    }
}
