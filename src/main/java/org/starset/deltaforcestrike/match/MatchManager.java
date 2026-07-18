package org.starset.deltaforcestrike.match;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.ArenaCleanup;
import org.starset.deltaforcestrike.util.ConfigKeys;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 全服唯一对局。
 * 仅处理竞技世界（config: world.arena，默认 delta_force_strike）。
 * <p>
 * WAITING → COUNTDOWN（选队）→ AGENT_SELECT（可关）→ IN_PROGRESS → ENDING → WAITING
 */
public class MatchManager {

    private final DeltaForceStrike plugin;
    private Match match;

    private BukkitTask countdownTask;
    private int countdownLeft;

    private BukkitTask agentTask;
    private int agentLeft;

    public MatchManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
        resetMatchWaiting();
    }

    // =====================================================================
    // 基础
    // =====================================================================

    private void resetMatchWaiting() {
        if (match != null) {
            match.getRoundManager().shutdown();
        }
        match = new Match(plugin);
        match.setState(MatchState.WAITING);
    }

    public Match getMatch() {
        return match;
    }

    public boolean isInMatch(Player player) {
        return player != null
                && match != null
                && match.contains(player.getUniqueId());
    }

    public boolean isInActiveGame(Player player) {
        if (!isInMatch(player) || match == null) {
            return false;
        }
        MatchState s = match.getState();
        return s == MatchState.IN_PROGRESS || s == MatchState.AGENT_SELECT;
    }

    public boolean isCombat(Player player) {
        return isInMatch(player)
                && match != null
                && match.getState() == MatchState.IN_PROGRESS
                && match.getRoundManager().getState() == RoundState.COMBAT;
    }

    /**
     * 是否禁止新人加入。
     */
    public boolean isJoinLocked() {
        if (match == null) {
            return false;
        }
        MatchState s = match.getState();
        if (s == MatchState.IN_PROGRESS
                || s == MatchState.AGENT_SELECT
                || s == MatchState.ENDING) {
            return true;
        }
        if (s == MatchState.COUNTDOWN
                && plugin.getConfig().getBoolean("queue.lock-during-countdown", true)) {
            return true;
        }
        return false;
    }

    // =====================================================================
    // 进竞技世界 / 入队
    // =====================================================================

    public void handleEnterArena(Player player) {
        if (!Worlds.isArena(player)) {
            return;
        }
        tryJoin(player);
    }

    public boolean tryJoin(Player player) {
        if (player == null) {
            return false;
        }

        if (!Worlds.isArena(player)) {
            player.sendMessage("§c[DFS] 只能在竞技世界 §e" + Worlds.arenaName() + " §c加入。");
            return false;
        }

        if (match != null && match.contains(player.getUniqueId())) {
            safeScoreboardCreate(player);
            safeTabUpdate(player);
            safeScoreboardUpdateAll();
            return true;
        }

        if (match == null || match.getState() == MatchState.ENDING) {
            resetMatchWaiting();
        }

        if (isJoinLocked()) {
            player.sendMessage("§c[DFS] 当前对局已锁定，无法加入。请等待本局结束。");
            return false;
        }

        int max = ConfigKeys.maxPlayers();
        if (match.isFull(max)) {
            player.sendMessage("§c[DFS] 队列已满（" + max + "）。");
            return false;
        }

        int startMoney = plugin.getConfig().getInt("economy.start-money", 800);
        PlayerSession session = new PlayerSession(player, startMoney);
        match.getSessions().put(player.getUniqueId(), session);

        player.setGameMode(GameMode.ADVENTURE);
        player.setFallDistance(0f);
        player.setInvulnerable(true);
        teleportQueue(player);
        plugin.getGameRulesService().fillFood(player);
        plugin.getGameRulesService().fillHealth(player);

        safeScoreboardCreate(player);
        safeTabUpdate(player);

        match.broadcast("§a[DFS] §f" + player.getName()
                + " §7加入队列 §8(§e" + match.size() + "§8/§e" + max + "§8)");

        sendQueueActionBar();
        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        checkAutoStart();
        return true;
    }

    public void leave(Player player) {
        if (player == null || match == null || !match.contains(player.getUniqueId())) {
            return;
        }

        MatchState state = match.getState();
        PlayerSession session = match.getSession(player.getUniqueId());
        if (session != null) {
            session.setConnected(false);
            session.setAlive(false);
        }

        match.getSessions().remove(player.getUniqueId());
        safeScoreboardRemove(player);
        safeTabReset(player);

        player.setInvulnerable(false);
        match.broadcast("§e[DFS] §f" + player.getName() + " §7已离开。 §8(" + match.size() + ")");

        if (state == MatchState.COUNTDOWN) {
            int max = ConfigKeys.maxPlayers();
            if (plugin.getConfig().getBoolean("queue.cancel-if-not-full", true)
                    && match.size() < max) {
                cancelCountdown("人数不足，倒计时取消。");
            }
        } else if (state == MatchState.IN_PROGRESS) {
            match.getRoundManager().checkWipe();
            if (match.size() == 0) {
                forceEnd("所有玩家已离开。");
            }
        } else if (state == MatchState.AGENT_SELECT) {
            if (match.size() == 0) {
                forceEnd("所有玩家已离开。");
            }
        }

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        sendQueueActionBar();
    }

    // =====================================================================
    // 满人 → 倒计时
    // =====================================================================

    private void checkAutoStart() {
        if (match == null || match.getState() != MatchState.WAITING) {
            return;
        }
        int max = ConfigKeys.maxPlayers();
        if (match.size() >= max) {
            startCountdown();
        }
    }

    /** 管理员强制进入倒计时 */
    public void forceStartCountdown() {
        if (match == null) {
            resetMatchWaiting();
        }
        if (match.getState() == MatchState.IN_PROGRESS
                || match.getState() == MatchState.AGENT_SELECT) {
            return;
        }
        if (match.size() < 1) {
            return;
        }
        startCountdown();
    }

    public void startCountdown() {
        if (match == null) {
            return;
        }
        if (match.getState() != MatchState.WAITING
                && match.getState() != MatchState.COUNTDOWN) {
            return;
        }

        cancelTasks();
        match.setState(MatchState.COUNTDOWN);
        countdownLeft = plugin.getConfig().getInt("queue.countdown-seconds", 30);

        match.broadcast("§6[DFS] §e准备开始！§f" + countdownLeft
                + " §e秒。使用 §a/dfs team t §e或 §b/dfs team ct §e选择队伍。");
        safeScoreboardUpdateAll();
        safeTabUpdateAll();

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (match == null || match.getState() != MatchState.COUNTDOWN) {
                cancelTasks();
                return;
            }

            int max = ConfigKeys.maxPlayers();
            boolean cancelIfNotFull = plugin.getConfig()
                    .getBoolean("queue.cancel-if-not-full", true);

            if (cancelIfNotFull && match.size() < max) {
                cancelCountdown("有人离开或人数不足，倒计时取消。");
                return;
            }

            if (match.size() < 1) {
                cancelCountdown("队列为空，倒计时取消。");
                return;
            }

            if (countdownLeft <= 0) {
                cancelTasks();
                beginAgentOrGame();
                return;
            }

            if (countdownLeft <= 5
                    || countdownLeft == 10
                    || countdownLeft == 20
                    || countdownLeft == 30
                    || countdownLeft % 15 == 0) {
                match.broadcast("§e[DFS] 游戏将在 §c" + countdownLeft + " §e秒后开始…");
            }

            sendQueueActionBar();
            safeScoreboardUpdateAll();
            countdownLeft--;
        }, 0L, 20L);
    }

    private void cancelCountdown(String reason) {
        cancelTasks();
        if (match != null) {
            match.setState(MatchState.WAITING);
            match.broadcast("§c[DFS] " + reason + " §7返回等待。");
            safeScoreboardUpdateAll();
            safeTabUpdateAll();
        }
    }

    private void sendQueueActionBar() {
        if (match == null) {
            return;
        }
        int max = ConfigKeys.maxPlayers();
        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            String team = (s == null || !s.hasTeam()) ? "未选队" : s.getTeam().name();
            String extra = match.getState() == MatchState.COUNTDOWN
                    ? (" | 倒计时 " + Math.max(0, countdownLeft) + "s")
                    : "";
            p.sendActionBar(Component.text(
                    "队列 " + match.size() + "/" + max
                            + " | " + team
                            + " | T" + match.countTeam(Team.T)
                            + " CT" + match.countTeam(Team.CT)
                            + extra,
                    NamedTextColor.GOLD
            ));
        }
    }

    // =====================================================================
    // 干员选择 / 开打
    // =====================================================================

    private void beginAgentOrGame() {
        balanceTeamsIfNeeded();

        boolean opEnabled = plugin.getConfig().getBoolean("operator.enabled", true);
        boolean selectEnabled = plugin.getConfig().getBoolean("operator.select-enabled", false);

        if (opEnabled && selectEnabled) {
            startAgentSelect();
        } else {
            match.broadcast("§7[DFS] 干员选择暂未开放，直接进入对局。");
            startGame();
        }
    }

    private void startAgentSelect() {
        if (match == null) {
            return;
        }
        match.setState(MatchState.AGENT_SELECT);
        agentLeft = plugin.getConfig().getInt("operator.select-seconds", 45);
        match.broadcast("§d[DFS] 干员选择 " + agentLeft + "s — /dfs agent <id>");
        safeScoreboardUpdateAll();
        safeTabUpdateAll();

        agentTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (match == null || match.getState() != MatchState.AGENT_SELECT) {
                cancelTasks();
                return;
            }
            if (agentLeft <= 0) {
                cancelTasks();
                startGame();
                return;
            }
            if (agentLeft <= 5 || agentLeft == 15) {
                match.broadcast("§d[DFS] 干员选择剩余 §f" + agentLeft + "s");
            }
            safeScoreboardUpdateAll();
            agentLeft--;
        }, 0L, 20L);
    }

    public boolean trySelectOperator(Player player, String operatorId) {
        if (player == null || operatorId == null) {
            return false;
        }
        if (!Worlds.isArena(player) || !isInMatch(player)) {
            player.sendMessage("§c[DFS] 你不在队列中。");
            return false;
        }
        if (match == null || match.getState() != MatchState.AGENT_SELECT) {
            player.sendMessage("§c[DFS] 当前不能选择干员。");
            return false;
        }
        if (!plugin.getConfig().getBoolean("operator.select-enabled", false)) {
            player.sendMessage("§c[DFS] 干员选择暂未开放。");
            return false;
        }

        PlayerSession s = match.getSession(player.getUniqueId());
        if (s == null) {
            return false;
        }
        s.setOperatorId(operatorId);
        player.sendMessage("§a[DFS] 已选择干员: " + operatorId);
        safeScoreboardUpdate(player);
        safeTabUpdate(player);
        return true;
    }

    public boolean trySelectTeam(Player player, Team team) {
        if (player == null) {
            return false;
        }
        if (!Worlds.isArena(player) || !isInMatch(player)) {
            player.sendMessage("§c[DFS] 你不在队列中。");
            return false;
        }
        if (match == null) {
            return false;
        }
        if (match.getState() != MatchState.WAITING
                && match.getState() != MatchState.COUNTDOWN) {
            player.sendMessage("§c[DFS] 只有等待/倒计时阶段可以选队。");
            return false;
        }
        if (team != Team.T && team != Team.CT) {
            return false;
        }

        int teamSize = ConfigKeys.teamSize();
        PlayerSession self = match.getSession(player.getUniqueId());
        if (self == null) {
            return false;
        }

        if (self.getTeam() == team) {
            player.sendMessage("§7你已在该队伍。");
            return true;
        }

        long count = match.countTeam(team);
        if (count >= teamSize) {
            player.sendMessage("§c[DFS] 该队伍已满（" + teamSize + "）。");
            return false;
        }

        self.setTeam(team);
        player.sendMessage(team == Team.T
                ? "§a[DFS] 已加入 §c进攻方 T"
                : "§a[DFS] 已加入 §b防守方 CT");
        match.broadcast("§7" + player.getName() + " → "
                + (team == Team.T ? "§cT" : "§bCT")
                + " §8(T " + match.countTeam(Team.T)
                + " / CT " + match.countTeam(Team.CT) + ")");

        sendQueueActionBar();
        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        return true;
    }

    private void balanceTeamsIfNeeded() {
        if (match == null) {
            return;
        }

        int teamSize = ConfigKeys.teamSize();

        for (PlayerSession s : match.getSessions().values()) {
            if (s.hasTeam()) {
                continue;
            }
            long t = match.countTeam(Team.T);
            long ct = match.countTeam(Team.CT);
            if (t <= ct && t < teamSize) {
                s.setTeam(Team.T);
            } else if (ct < teamSize) {
                s.setTeam(Team.CT);
            } else {
                s.setTeam(t <= ct ? Team.T : Team.CT);
            }
        }

        rebalanceOverflow(teamSize);
    }

    private void rebalanceOverflow(int teamSize) {
        if (match == null) {
            return;
        }

        List<PlayerSession> all = new ArrayList<>(match.getSessions().values());
        long t = match.countTeam(Team.T);
        long ct = match.countTeam(Team.CT);

        if (all.size() == teamSize * 2 && t == teamSize && ct == teamSize) {
            return;
        }

        Collections.shuffle(all);
        if (all.size() == teamSize * 2) {
            for (int i = 0; i < all.size(); i++) {
                all.get(i).setTeam(i < teamSize ? Team.T : Team.CT);
            }
            return;
        }

        for (int i = 0; i < all.size(); i++) {
            all.get(i).setTeam(i % 2 == 0 ? Team.T : Team.CT);
        }
    }

    // =====================================================================
    // 正式开打 / 结束
    // =====================================================================

    public void startGame() {
        if (match == null) {
            return;
        }
        cancelTasks();
        balanceTeamsIfNeeded();

        ArenaCleanup.clearDrops();
        if (plugin.getBombManager() != null) {
            plugin.getBombManager().reset();
        }

        match.setState(MatchState.IN_PROGRESS);
        match.setCurrentRound(0);
        match.broadcast("§a§l[DFS] 对局开始！");

        for (Player p : match.onlinePlayers()) {
            p.setInvulnerable(false);
            p.setFallDistance(0f);
        }

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        match.getRoundManager().startNextRound();
    }

    public void forceEnd(String reason) {
        cancelTasks();
        if (match == null) {
            return;
        }

        match.setState(MatchState.ENDING);
        match.getRoundManager().shutdown();
        if (plugin.getBombManager() != null) {
            plugin.getBombManager().reset();
        }
        ArenaCleanup.clearDrops();

        match.broadcast("§c[DFS] 对局结束: " + reason);

        List<Player> stillHere = new ArrayList<>(match.onlinePlayers());

        for (Player p : stillHere) {
            p.setInvulnerable(false);
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.setFallDistance(0f);
            teleportQueue(p);
            plugin.getGameRulesService().fillHealth(p);
            plugin.getGameRulesService().fillFood(p);
            safeScoreboardRemove(p);
            safeTabReset(p);
        }

        safeScoreboardRemoveAll();
        resetMatchWaiting();

        for (Player p : stillHere) {
            if (p.isOnline() && Worlds.isArena(p)) {
                tryJoin(p);
            }
        }
    }

    /**
     * 战斗阶段淘汰（致死旁观后由 GameRulesListener 调用）。
     */
    public void onPlayerEliminated(Player victim, Player killer) {
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return;
        }

        PlayerSession vs = match.getSession(victim.getUniqueId());
        if (vs != null) {
            vs.setAlive(false);
            vs.addDeath();
        }

        if (killer != null) {
            PlayerSession ks = match.getSession(killer.getUniqueId());
            if (ks != null && match.contains(killer.getUniqueId())) {
                if (vs == null || vs.getTeam() != ks.getTeam()) {
                    ks.addKill();
                    ks.addMoney(plugin.getConfig().getInt("economy.kill-reward", 300));
                }
            }
        }

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        match.getRoundManager().checkWipe();
    }

    // =====================================================================
    // 传送
    // =====================================================================

    public void teleportQueue(Player player) {
        if (player == null) {
            return;
        }
        Location loc = ConfigKeys.readLocation("locations.queue-spawn");
        if (loc == null) {
            player.sendMessage("§c[DFS] 队列出生点未配置！请用 §e/dfs setspawn queue");
            plugin.getLogger().severe("teleportQueue 失败: locations.queue-spawn 无效");
            World w = Worlds.arenaWorld();
            if (w != null) {
                Location spawn = w.getSpawnLocation().clone().add(0.5, 0, 0.5);
                player.setFallDistance(0f);
                player.teleport(spawn);
                player.setFallDistance(0f);
            }
            return;
        }
        player.setFallDistance(0f);
        player.teleport(loc);
        player.setFallDistance(0f);
    }

    public void teleportTeamSpawn(Player player, Team team) {
        if (player == null || team == null || team == Team.NONE) {
            return;
        }
        String node = team == Team.T
                ? "locations.team-t-spawn"
                : "locations.team-ct-spawn";
        Location loc = ConfigKeys.readLocation(node);
        if (loc == null) {
            player.sendMessage("§c[DFS] 队伍出生点未配置: " + node
                    + " §7请用 /dfs setspawn t 或 ct");
            plugin.getLogger().severe("teleportTeamSpawn 失败: " + node);
            teleportQueue(player);
            return;
        }
        player.setFallDistance(0f);
        player.teleport(loc);
        player.setFallDistance(0f);
    }

    // =====================================================================
    // 计分板 / Tab 安全调用
    // =====================================================================

    private void safeScoreboardCreate(Player player) {
        try {
            if (plugin.getScoreboardService() != null) {
                plugin.getScoreboardService().create(player);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Scoreboard create: " + t.getMessage());
        }
    }

    private void safeScoreboardRemove(Player player) {
        try {
            if (plugin.getScoreboardService() != null) {
                plugin.getScoreboardService().remove(player);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Scoreboard remove: " + t.getMessage());
        }
    }

    private void safeScoreboardRemoveAll() {
        try {
            if (plugin.getScoreboardService() != null) {
                plugin.getScoreboardService().removeAll();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Scoreboard removeAll: " + t.getMessage());
        }
    }

    private void safeScoreboardUpdate(Player player) {
        try {
            if (plugin.getScoreboardService() != null) {
                plugin.getScoreboardService().update(player);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Scoreboard update: " + t.getMessage());
        }
    }

    private void safeScoreboardUpdateAll() {
        try {
            if (plugin.getScoreboardService() != null && match != null) {
                plugin.getScoreboardService().updateAll(match);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Scoreboard updateAll: " + t.getMessage());
        }
    }

    private void safeTabUpdate(Player player) {
        try {
            if (plugin.getTabListService() != null) {
                plugin.getTabListService().update(player);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Tab update: " + t.getMessage());
        }
    }

    private void safeTabUpdateAll() {
        try {
            if (plugin.getTabListService() != null && match != null) {
                plugin.getTabListService().updateAll(match);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Tab updateAll: " + t.getMessage());
        }
    }

    private void safeTabReset(Player player) {
        try {
            if (plugin.getTabListService() != null) {
                plugin.getTabListService().reset(player);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Tab reset: " + t.getMessage());
        }
    }

    // =====================================================================
    // 任务 / 关闭 / 状态
    // =====================================================================

    private void cancelTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (agentTask != null) {
            agentTask.cancel();
            agentTask = null;
        }
    }

    public void shutdown() {
        cancelTasks();
        safeScoreboardRemoveAll();
        if (plugin.getBombManager() != null) {
            plugin.getBombManager().reset();
        }
        if (match != null) {
            match.getRoundManager().shutdown();
        }
        match = null;
    }

    public String statusLine() {
        if (match == null) {
            return "无对局";
        }
        return match.getState()
                + " | " + match.size() + "/" + ConfigKeys.maxPlayers() + "人"
                + " | T " + match.getScoreT() + " - " + match.getScoreCT() + " CT"
                + " | R" + match.getCurrentRound()
                + " | " + match.getRoundManager().getState();
    }
}
