package org.starset.deltaforcestrike.pause;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.round.RoundState;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 暂停服务：战术暂停 + 技术暂停，仅作用于购买阶段。
 *
 * <ul>
 *   <li><b>战术暂停</b>：队伍任意成员可叫，每队常规局 {pause.tactical.per-match} 次，
 *       进入加时后重置为 {pause.tactical.overtime-reset} 次。叫停后 {pause.tactical.duration-seconds} 秒，
 *       期间购买阶段计时停滞、行动冻结。</li>
 *   <li><b>技术暂停</b>：管理员可用，购买阶段任意次数，无内置时长，
 *       需 /dfs resume 恢复（或再次执行 techpause 切换）。</li>
 * </ul>
 */
public final class PauseService {

    public enum PauseKind { TACTICAL, TECHNICAL }

    private final DeltaForceStrike plugin;
    /** 每队战术暂停剩余次数 */
    private final Map<Team, Integer> tacticalLeft = new EnumMap<>(Team.class);
    /** 当前生效的暂停；null 表示无暂停 */
    private ActivePause active;
    /** 暂停时回合剩余秒数（恢复后继续使用） */
    private int frozenBuySeconds = -1;

    public PauseService(DeltaForceStrike plugin) {
        this.plugin = plugin;
        resetTeamQuotas();
    }

    /** 新对局重置 */
    public void resetForNewMatch() {
        cancelActive();
        resetTeamQuotas();
    }

    /** 半场重置（不重置次数） */
    public void resetRound() {
        cancelActive();
    }

    /** 进入加时模式：将每队的战术暂停次数重置为 overtime-reset */
    public void enterOvertime() {
        int reset = plugin.getConfig().getInt("pause.tactical.overtime-reset", 1);
        tacticalLeft.put(Team.T, Math.max(0, reset));
        tacticalLeft.put(Team.CT, Math.max(0, reset));
        if (plugin.getMatchManager().getMatch() != null) {
            plugin.getMatchManager().getMatch().broadcast(
                    "§6[DFS] 进入加时，战术暂停次数重置为 §e" + reset + " §6次/队");
        }
    }

    private void resetTeamQuotas() {
        int per = plugin.getConfig().getInt("pause.tactical.per-match", 2);
        tacticalLeft.put(Team.T, per);
        tacticalLeft.put(Team.CT, per);
    }

    public int tacticalLeft(Team team) {
        return tacticalLeft.getOrDefault(team, 0);
    }

    public boolean isPaused() {
        return active != null;
    }

    public PauseKind currentKind() {
        return active == null ? null : active.kind;
    }

    public Team currentTacticalTeam() {
        return active != null && active.kind == PauseKind.TACTICAL ? active.team : null;
    }

    public int currentSecondsLeft() {
        return active == null ? 0 : active.secondsLeft;
    }

    /** 购买阶段已被冻结的剩余秒数（恢复后回合沿用，-1 表示无快照） */
    public int frozenBuySeconds() {
        return frozenBuySeconds;
    }

    public void setFrozenBuySeconds(int sec) {
        this.frozenBuySeconds = sec;
    }

    /** 请求战术暂停。callerTeam：发起方的队伍 */
    public boolean requestTactical(Player caller) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            caller.sendMessage("§c[DFS] 当前不在对局中。");
            return false;
        }
        if (match.getRoundManager().getState() != RoundState.BUY) {
            caller.sendMessage("§c[DFS] 战术暂停只能在购买阶段叫停。");
            return false;
        }
        PlayerSession s = match.getSession(caller.getUniqueId());
        if (s == null || !s.isPlaying() || !s.hasTeam()) {
            caller.sendMessage("§c[DFS] 只有参赛选手能叫战术暂停。");
            return false;
        }
        if (isPaused()) {
            caller.sendMessage("§c[DFS] 已在暂停中 (" + (active.kind == PauseKind.TACTICAL
                    ? "T 队" : "技术") + ")。");
            return false;
        }
        Team team = s.getTeam();
        int left = tacticalLeft(team);
        if (left <= 0) {
            caller.sendMessage("§c[DFS] 本队战术暂停次数已用完。§7(加时会重置)");
            return false;
        }
        tacticalLeft.put(team, left - 1);

        int duration = Math.max(1, plugin.getConfig().getInt("pause.tactical.duration-seconds", 30));
        beginPause(PauseKind.TACTICAL, team, duration, caller.getName());
        return true;
    }

    /** 管理员技术暂停：开启/恢复。admin 可为 null（console 调用） */
    public boolean requestTechnical(Player admin) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            if (admin != null) admin.sendMessage("§c[DFS] 当前不在对局中。");
            return false;
        }
        if (match.getRoundManager().getState() != RoundState.BUY) {
            if (admin != null) admin.sendMessage("§c[DFS] 技术暂停只能在购买阶段使用。");
            return false;
        }
        if (isPaused()) {
            if (admin != null) admin.sendMessage("§c[DFS] 已在暂停中。§7/dfs resume");
            return false;
        }
        beginPause(PauseKind.TECHNICAL, Team.NONE, 0, admin == null ? "console" : admin.getName());
        return true;
    }

    private void beginPause(PauseKind kind, Team team, int duration, String by) {
        active = new ActivePause(kind, team, duration);
        Match match = plugin.getMatchManager().getMatch();

        String tag = (kind == PauseKind.TACTICAL ? "§6战术暂停" : "§c技术暂停");
        String label = kind == PauseKind.TACTICAL
                ? tag + " §7(" + (team == Team.T ? "§cT" : "§bCT") + " §7队)"
                : tag;
        match.broadcast("§e[DFS] " + label + " §7发起: §f" + by);
        if (kind == PauseKind.TACTICAL) {
            match.broadcast("§7剩余 §f" + tacticalLeft(team) + " §7次");
        } else {
            match.broadcast("§7请管理员 §f/dfs resume §7恢复");
        }
        showTitle(match, label, "比赛暂停");

        if (kind == PauseKind.TACTICAL && duration > 0) {
            active.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (active == null) return;
                if (active.kind != kind) {
                    cancelActive();
                    return;
                }
                if (active.secondsLeft <= 0) {
                    resume("战术暂停结束");
                    return;
                }
                if (active.secondsLeft <= 5 || active.secondsLeft == 10 || active.secondsLeft == 20) {
                    match.broadcast("§6[暂停] §f" + active.secondsLeft + "s 后恢复");
                }
                active.secondsLeft--;
            }, 20L, 20L);
        }
    }

    /** 管理员/自动恢复 */
    public void resume(String reason) {
        if (!isPaused()) return;
        cancelActive();
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) return;
        match.broadcast("§a[DFS] 恢复比赛 §7" + (reason == null ? "" : reason));
        showTitle(match, "恢复比赛", reason == null ? "" : reason);
        if (match.getRoundManager().getState() == RoundState.BUY) {
            match.getRoundManager().resumeBuy();
        }
    }

    public void cancelActive() {
        if (active != null && active.task != null) {
            active.task.cancel();
        }
        active = null;
    }

    public String statusLine() {
        if (!isPaused()) return "无暂停";
        if (active.kind == PauseKind.TACTICAL) {
            return "战术暂停(" + (active.team == Team.T ? "T" : "CT") + ") "
                    + active.secondsLeft + "s";
        }
        return "技术暂停";
    }

    private void showTitle(Match match, String main, String sub) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(400));
        Component mainC = net.kyori.adventure.text.serializer.legacy
                .LegacyComponentSerializer.legacySection().deserialize(main);
        if (mainC.color() == null) {
            mainC = mainC.color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
        }
        Component subC = net.kyori.adventure.text.serializer.legacy
                .LegacyComponentSerializer.legacySection().deserialize(sub == null ? "" : sub);
        for (Player p : match.onlinePlayers()) {
            p.showTitle(Title.title(mainC, subC, times));
        }
    }

    private static final class ActivePause {
        final PauseKind kind;
        final Team team;
        int secondsLeft;
        BukkitTask task;

        ActivePause(PauseKind kind, Team team, int secondsLeft) {
            this.kind = kind;
            this.team = team;
            this.secondsLeft = secondsLeft;
        }
    }

    /** 用于把当前暂停发起人记入导播：非持久字段 */
    public String activeLabel() {
        if (active == null) return "";
        return active.kind == PauseKind.TACTICAL
                ? "TACTICAL:" + (active.team == Team.T ? "T" : "CT")
                : "TECHNICAL";
    }

    /** 全员带走 UUID 列表（仅用于安全 */
    @SuppressWarnings("unused")
    private UUID[] emptyIds() {
        return new UUID[0];
    }
}
