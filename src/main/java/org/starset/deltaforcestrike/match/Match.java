package org.starset.deltaforcestrike.match;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.round.RoundManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Match {

    private final UUID matchId = UUID.randomUUID();
    private MatchState state = MatchState.WAITING;
    private final Map<UUID, PlayerSession> sessions = new LinkedHashMap<>();
    private final RoundManager roundManager;

    private int scoreT;
    private int scoreCT;
    private int currentRound;
    /** 上一回合胜方（T/CT），供 ClientUI 音乐等使用 */
    private Team lastRoundWinner = Team.NONE;
    /** 加时模式（切换后通知 PauseService 重置战术暂停次数） */
    private boolean overtime;
    /** 进入加时时双方的比分基准（平局 T==CT）。加时赛胜场目标 = 基准 + overtime.win-target */
    private int overtimeBaseScore;
    /** 第几次加时（1、2、3…），用于计分板 / 导播显示 OT1/OT2 */
    private int overtimeCount;
    private final DeltaForceStrike plugin;

    public Match(DeltaForceStrike plugin) {
        this.plugin = plugin;
        this.roundManager = new RoundManager(plugin, this);
    }

    public UUID getMatchId() { return matchId; }
    public MatchState getState() { return state; }
    public void setState(MatchState state) { this.state = state; }
    public Map<UUID, PlayerSession> getSessions() { return sessions; }
    public PlayerSession getSession(UUID uuid) { return sessions.get(uuid); }
    public RoundManager getRoundManager() { return roundManager; }
    public int getScoreT() { return scoreT; }
    public int getScoreCT() { return scoreCT; }
    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
    public Team getLastRoundWinner() { return lastRoundWinner; }
    public void setLastRoundWinner(Team lastRoundWinner) {
        this.lastRoundWinner = lastRoundWinner == null ? Team.NONE : lastRoundWinner;
    }

    /** @see org.starset.deltaforcestrike.match.Match#isOvertime */
    public boolean isOvertime() { return overtime; }

    /**
     * @see #isOvertime()
     */
    public void setOvertime(boolean overtime) { this.overtime = overtime; }

    public int getOvertimeBaseScore() { return overtimeBaseScore; }

    public void setOvertimeBaseScore(int v) { this.overtimeBaseScore = Math.max(0, v); }

    public int getOvertimeCount() { return overtimeCount; }

    public void setOvertimeCount(int n) { this.overtimeCount = Math.max(0, n); }

    /** 加时赛胜场目标：进入加时基准 + overtime.win-target（加时采用累计比分） */
    public int overtimeWinTarget() {
        int base = getOvertimeBaseScore();
        int ot = plugin.getConfig().getInt("overtime.win-target", 4);
        return base + Math.max(1, ot);
    }

    /**
     * 进入加时模式：标记 overtime + 通知 PauseService 重置战术暂停次数。
     * 实际开赛流程由 MatchManager.enterOvertimeFromEndWindow 处理。
     */
    public void enterOvertime() {
        setOvertime(true);
        var ps = plugin.getPauseService();
        if (ps != null) ps.enterOvertime();
    }

    public void addScore(Team team) {
        if (team == Team.T) scoreT++;
        else if (team == Team.CT) scoreCT++;
        if (team == Team.T || team == Team.CT) {
            lastRoundWinner = team;
        }
    }

    public void setScoreT(int v) { scoreT = Math.max(0, v); }
    public void setScoreCT(int v) { scoreCT = Math.max(0, v); }

    public void swapScores() {
        int tmp = scoreT;
        scoreT = scoreCT;
        scoreCT = tmp;
    }

    public int size() { return sessions.size(); }
    public boolean isFull(int max) { return sessions.size() >= max; }
    public boolean contains(UUID uuid) { return sessions.containsKey(uuid); }

    public List<Player> onlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    public long countTeam(Team team) {
        return sessions.values().stream().filter(s -> s.getTeam() == team).count();
    }

    public void broadcast(String msg) {
        for (Player p : onlinePlayers()) p.sendMessage(msg);
    }

    public void broadcast(Component component) {
        for (Player p : onlinePlayers()) p.sendMessage(component);
    }

    public void broadcastActionBar(String legacy) {
        if (legacy == null) return;
        Component c = net.kyori.adventure.text.serializer.legacy
                .LegacyComponentSerializer.legacySection().deserialize(legacy);
        for (Player p : onlinePlayers()) {
            p.sendActionBar(c);
        }
    }

    /**
     * 全灭判定：断线玩家仍计入该队，且视为已阵亡（不能阻止回合结束）。
     */
    public boolean allDead(Team team) {
        boolean any = false;
        for (PlayerSession s : sessions.values()) {
            if (s.getTeam() != team) {
                continue;
            }
            any = true;
            // 仅在线且存活才算“还活着”
            if (s.isConnected() && s.isAlive()) {
                return false;
            }
        }
        return any;
    }

    /** 仍在对局中的在线人数（不含断线占位） */
    public int onlineCount() {
        return onlinePlayers().size();
    }

    /** 参赛选手总数（T + CT） */
    public int playingCount() {
        int n = 0;
        for (PlayerSession s : sessions.values()) {
            if (s.isPlaying()) n++;
        }
        return n;
    }

    /** 占用房间总名额的人数：参赛 + 旁观（不含导播） */
    public int occupiedSlots() {
        int n = 0;
        for (PlayerSession s : sessions.values()) {
            if (s.getRole() == org.starset.deltaforcestrike.spectator.SpectatorRole.PLAYING
                    || s.getRole() == org.starset.deltaforcestrike.spectator.SpectatorRole.SPECTATOR) {
                n++;
            }
        }
        return n;
    }

    /** 全程不占名额的导播数（仅统计） */
    public int observerCount() {
        int n = 0;
        for (PlayerSession s : sessions.values()) {
            if (s.getRole() == org.starset.deltaforcestrike.spectator.SpectatorRole.OBSERVER) n++;
        }
        return n;
    }

    public long countSpectatorSlots() {
        return sessions.values().stream()
                .filter(s -> s.getRole() == org.starset.deltaforcestrike.spectator.SpectatorRole.SPECTATOR)
                .count();
    }
}
