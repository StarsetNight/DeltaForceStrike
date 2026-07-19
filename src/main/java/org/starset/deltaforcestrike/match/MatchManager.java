package org.starset.deltaforcestrike.match;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatchManager {

    private final DeltaForceStrike plugin;
    private Match match;

    private BukkitTask countdownTask;
    private int countdownLeft;
    private BukkitTask agentTask;
    private int agentLeft;

    private static final long END_RESET_DELAY_TICKS = 70L;

    public MatchManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
        resetMatchWaiting();
    }

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
        return player != null && match != null && match.contains(player.getUniqueId());
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

    public boolean isJoinLocked() {
        if (match == null) {
            return false;
        }
        MatchState s = match.getState();
        if (s == MatchState.IN_PROGRESS || s == MatchState.AGENT_SELECT || s == MatchState.ENDING) {
            return true;
        }
        return s == MatchState.COUNTDOWN
                && plugin.getConfig().getBoolean("queue.lock-during-countdown", true);
    }

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

        if (match == null) {
            resetMatchWaiting();
        }
        if (match.getState() == MatchState.ENDING) {
            player.sendMessage("§c[DFS] 对局正在结算，请稍候…");
            return false;
        }

        if (isJoinLocked()) {
            player.sendMessage("§c[DFS] 当前对局已锁定，无法加入。");
            return false;
        }

        int max = ConfigKeys.maxPlayers();
        if (match.isFull(max)) {
            player.sendMessage("§c[DFS] 队列已满（" + max + "）。");
            return false;
        }

        int startMoney = plugin.getConfig().getInt("economy.start-money", 800);
        match.getSessions().put(player.getUniqueId(), new PlayerSession(player, startMoney));

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

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isInMatch(player) || match == null) {
                return;
            }
            MatchState st = match.getState();
            if (st == MatchState.WAITING || st == MatchState.COUNTDOWN) {
                GameGuide.sendOnJoin(player);
                TeamSelectUI.send(player);
                if (plugin.getConfig().getBoolean("operator.select-enabled", false)) {
                    player.sendMessage("§d干员: §f/dfs agent <niko|bruo|aier|wulong>");
                }
            }
        }, 10L);

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
        if (plugin.getOperatorService() != null) {
            plugin.getOperatorService().clearPlayer(player.getUniqueId());
        }

        safeScoreboardRemove(player);
        safeTabReset(player);
        safeSpectatorClear(player);
        player.setInvulnerable(false);

        match.broadcast("§e[DFS] §f" + player.getName() + " §7已离开。 §8(" + match.size() + ")");

        if (state == MatchState.COUNTDOWN) {
            if (plugin.getConfig().getBoolean("queue.cancel-if-not-full", true)
                    && match.size() < ConfigKeys.maxPlayers()) {
                cancelCountdown("人数不足，倒计时取消。");
            }
        } else if (state == MatchState.IN_PROGRESS) {
            match.getRoundManager().checkWipe();
            if (match.size() == 0) {
                forceEnd("所有玩家已离开。");
            }
        } else if (state == MatchState.AGENT_SELECT && match.size() == 0) {
            forceEnd("所有玩家已离开。");
        }

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        sendQueueActionBar();
    }

    private void checkAutoStart() {
        if (match == null || match.getState() != MatchState.WAITING) {
            return;
        }
        if (match.size() >= ConfigKeys.maxPlayers()) {
            startCountdown();
        }
    }

    public void forceStartCountdown() {
        if (match == null) {
            resetMatchWaiting();
        }
        if (match.getState() == MatchState.IN_PROGRESS
                || match.getState() == MatchState.AGENT_SELECT
                || match.getState() == MatchState.ENDING) {
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
        if (match.getState() != MatchState.WAITING && match.getState() != MatchState.COUNTDOWN) {
            return;
        }

        cancelTasks();
        match.setState(MatchState.COUNTDOWN);
        countdownLeft = plugin.getConfig().getInt("queue.countdown-seconds", 30);

        match.broadcast("§6[DFS] §e准备开始！§f" + countdownLeft
                + " §e秒。点击聊天选边，或 §a/dfs team t§e / §bct");
        if (plugin.getConfig().getBoolean("operator.select-enabled", false)) {
            match.broadcast("§d干员选择: §f/dfs agent <niko|bruo|aier|wulong>");
        }

        TeamSelectUI.broadcast(match);
        safeScoreboardUpdateAll();
        safeTabUpdateAll();

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (match == null || match.getState() != MatchState.COUNTDOWN) {
                cancelTasks();
                return;
            }
            if (plugin.getConfig().getBoolean("queue.cancel-if-not-full", true)
                    && match.size() < ConfigKeys.maxPlayers()) {
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
            if (countdownLeft <= 5 || countdownLeft == 10 || countdownLeft == 20
                    || countdownLeft == 30 || countdownLeft % 15 == 0) {
                match.broadcast("§e[DFS] 游戏将在 §c" + countdownLeft + " §e秒后开始…");
            }
            List<Integer> reminds = plugin.getConfig().getIntegerList("queue.team-click-remind-seconds");
            if (reminds.isEmpty()) {
                if (countdownLeft == 15 || countdownLeft == 10) {
                    TeamSelectUI.broadcast(match);
                }
            } else if (reminds.contains(countdownLeft)) {
                TeamSelectUI.broadcast(match);
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
                    ? (" | 倒计时 " + Math.max(0, countdownLeft) + "s") : "";
            String op = "";
            if (s != null && s.getOperatorId() != null) {
                op = " | " + s.getOperatorId();
            }
            p.sendActionBar(Component.text(
                    "队列 " + match.size() + "/" + max + " | " + team
                            + " | T" + match.countTeam(Team.T) + " CT" + match.countTeam(Team.CT)
                            + extra + op,
                    NamedTextColor.GOLD
            ));
        }
    }

    private void beginAgentOrGame() {
        balanceTeamsIfNeeded();

        boolean opEnabled = plugin.getConfig().getBoolean("operator.enabled", true);
        boolean selectEnabled = plugin.getConfig().getBoolean("operator.select-enabled", true);

        if (opEnabled && selectEnabled) {
            startAgentSelect();
            // 若进阶段时已经全选完，立刻开打
            if (allOperatorsSelected()) {
                cancelTasks();
                match.broadcast("§a[DFS] 全员已选择干员，立即开始！");
                startGame();
            }
        } else {
            match.broadcast("§7[DFS] 干员选择跳过，自动分配。");
            startGame();
        }
    }

    private void startAgentSelect() {
        if (match == null) {
            return;
        }
        match.setState(MatchState.AGENT_SELECT);
        agentLeft = plugin.getConfig().getInt("operator.select-seconds", 45);

        match.broadcast("§d[DFS] 请选择干员！§7点击聊天栏按钮，或 §f/dfs agent <id>");
        match.broadcast("§7全员选完将 §a立即开始§7，无需等满 "
                + agentLeft + " 秒。");

        // 可点击干员 + 当前队伍选择一览
        OperatorSelectUI.broadcast(match);

        safeScoreboardUpdateAll();
        safeTabUpdateAll();

        // 兜底读秒：仅当有人一直不选时才等到时自动开
        agentTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (match == null || match.getState() != MatchState.AGENT_SELECT) {
                cancelTasks();
                return;
            }

            // 全员选完 → 立刻开始
            if (allOperatorsSelected()) {
                cancelTasks();
                match.broadcast("§a[DFS] 全员已选择干员，比赛开始！");
                startGame();
                return;
            }

            if (agentLeft <= 0) {
                cancelTasks();
                match.broadcast("§e[DFS] 干员选择时间结束，未选者将随机分配。");
                startGame();
                return;
            }

            if (agentLeft <= 5 || agentLeft == 15 || agentLeft == 30) {
                match.broadcast("§d[DFS] 干员选择剩余 §f" + agentLeft
                        + "s §7· 已选 " + countOperatorsSelected() + "/" + match.size());
                // 提醒未选的人
                for (Player p : match.onlinePlayers()) {
                    PlayerSession s = match.getSession(p.getUniqueId());
                    if (s != null && (s.getOperatorId() == null || s.getOperatorId().isEmpty())) {
                        OperatorSelectUI.send(p);
                    }
                }
            }
            safeScoreboardUpdateAll();
            agentLeft--;
        }, 20L, 20L);
    }

    /** 是否所有在线参赛者都已选定干员 */
    private boolean allOperatorsSelected() {
        if (match == null || match.size() == 0) {
            return false;
        }
        for (PlayerSession s : match.getSessions().values()) {
            if (!s.isConnected()) {
                continue;
            }
            if (s.getOperatorId() == null || s.getOperatorId().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private int countOperatorsSelected() {
        if (match == null) {
            return 0;
        }
        int n = 0;
        for (PlayerSession s : match.getSessions().values()) {
            if (s.isConnected() && s.getOperatorId() != null && !s.getOperatorId().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public boolean trySelectOperator(Player player, String operatorId) {
        if (player == null || operatorId == null) {
            return false;
        }
        if (!Worlds.isArena(player) || !isInMatch(player)) {
            player.sendMessage("§c[DFS] 你不在队列中。");
            return false;
        }
        if (match == null) {
            return false;
        }

        MatchState st = match.getState();
        // 倒计时阶段也可提前选；正式 AGENT_SELECT 必选
        if (st != MatchState.WAITING
                && st != MatchState.COUNTDOWN
                && st != MatchState.AGENT_SELECT) {
            player.sendMessage("§c[DFS] 当前不能选择干员。");
            return false;
        }
        if (!plugin.getConfig().getBoolean("operator.enabled", true)) {
            player.sendMessage("§c[DFS] 干员系统未启用。");
            return false;
        }
        if (plugin.getOperatorService() == null) {
            player.sendMessage("§c干员服务未加载");
            return false;
        }

        boolean ok = plugin.getOperatorService().selectOperator(player, operatorId);
        if (!ok) {
            return false;
        }

        var def = plugin.getOperatorService().getRegistry().get(operatorId);
        if (def != null) {
            OperatorSelectUI.sendSelected(player, def);
            // 全队可见：谁选了什么
            OperatorSelectUI.broadcastPick(match, player, def);
        }

        safeScoreboardUpdate(player);
        safeTabUpdate(player);
        sendQueueActionBar();

        // 若已在 AGENT_SELECT 且全员选完 → 立刻开打
        if (match.getState() == MatchState.AGENT_SELECT && allOperatorsSelected()) {
            cancelTasks();
            match.broadcast("§a§l[DFS] 全员已锁定干员，立即开始！");
            startGame();
        }
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
        if (match.getState() != MatchState.WAITING && match.getState() != MatchState.COUNTDOWN) {
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
            TeamSelectUI.sendSelected(player, team);
            return true;
        }
        if (match.countTeam(team) >= teamSize) {
            player.sendMessage("§c[DFS] 该队伍已满（" + teamSize + "）。");
            TeamSelectUI.send(player);
            return false;
        }

        self.setTeam(team);
        TeamSelectUI.sendSelected(player, team);
        match.broadcast("§7" + player.getName() + " → "
                + (team == Team.T ? "§cT" : "§bCT")
                + " §8(T " + match.countTeam(Team.T) + " / CT " + match.countTeam(Team.CT) + ")");

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s != null && !s.hasTeam()) {
                TeamSelectUI.send(p);
            }
        }
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

        // 干员：未选则随机分配
        if (plugin.getOperatorService() != null
                && plugin.getConfig().getBoolean("operator.enabled", true)) {
            plugin.getOperatorService().prepareMatch(match);
        }

        match.setState(MatchState.IN_PROGRESS);
        match.setCurrentRound(0);
        match.broadcast("§a§l[DFS] 对局开始！");
        match.broadcast("§e提示: T 包点下包 · CT 潜行拆包 · §a/dfs guide §e· §a/dfs shop");

        for (Player p : match.onlinePlayers()) {
            p.setInvulnerable(false);
            p.setFallDistance(0f);
            safeSpectatorClear(p);
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
        if (match.getState() == MatchState.ENDING) {
            return;
        }

        match.setState(MatchState.ENDING);
        match.getRoundManager().shutdown();
        if (plugin.getBombManager() != null) {
            plugin.getBombManager().reset();
        }
        ArenaCleanup.clearDrops();

        final int finalT = match.getScoreT();
        final int finalCT = match.getScoreCT();
        final String reasonText = reason == null ? "" : reason;

        showMatchEndTitles(finalT, finalCT);

        match.broadcast("§c[DFS] 对局结束: §f" + reasonText);
        match.broadcast("§6最终比分 §cT " + finalT + " §7- §b" + finalCT + " CT");

        List<Player> stillHere = new ArrayList<>(match.onlinePlayers());
        long delay = plugin.getConfig().getLong("match.end-title-delay-ticks", END_RESET_DELAY_TICKS);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : stillHere) {
                if (!p.isOnline()) {
                    continue;
                }
                p.setInvulnerable(false);
                safeSpectatorClear(p);
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                p.getInventory().setHelmet(null);
                p.getInventory().setChestplate(null);
                p.getInventory().setLeggings(null);
                p.getInventory().setBoots(null);
                p.getInventory().setItemInOffHand(null);
                p.setFallDistance(0f);
                if (plugin.getOperatorService() != null) {
                    plugin.getOperatorService().clearPlayer(p.getUniqueId());
                }
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
        }, Math.max(20L, delay));
    }

    private void showMatchEndTitles(int scoreT, int scoreCT) {
        if (match == null) {
            return;
        }
        Team winner;
        if (scoreT > scoreCT) {
            winner = Team.T;
        } else if (scoreCT > scoreT) {
            winner = Team.CT;
        } else {
            winner = Team.NONE;
        }

        Title.Times times = Title.Times.times(
                Duration.ofMillis(200),
                Duration.ofSeconds(4),
                Duration.ofMillis(800)
        );
        Component scoreSub = Component.text("比分 ", NamedTextColor.GRAY)
                .append(Component.text("T " + scoreT, NamedTextColor.RED))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(scoreCT + " CT", NamedTextColor.AQUA));
        Component winMain = Component.text("胜利", NamedTextColor.GREEN, TextDecoration.BOLD);
        Component loseMain = Component.text("战败", NamedTextColor.RED, TextDecoration.BOLD);
        Component drawMain = Component.text("平局", NamedTextColor.YELLOW, TextDecoration.BOLD);
        Component endMain = Component.text("对局结束", NamedTextColor.GOLD, TextDecoration.BOLD);

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (winner == Team.NONE) {
                p.showTitle(Title.title(drawMain, scoreSub, times));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                continue;
            }
            if (s == null || !s.hasTeam()) {
                p.showTitle(Title.title(endMain, scoreSub, times));
                continue;
            }
            if (s.getTeam() == winner) {
                p.showTitle(Title.title(winMain, scoreSub, times));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
            } else {
                p.showTitle(Title.title(loseMain, scoreSub, times));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9f, 0.8f);
            }
        }
    }

    public void onPlayerEliminated(Player victim, Player killer) {
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return;
        }
        PlayerSession vs = match.getSession(victim.getUniqueId());
        if (vs == null) {
            return;
        }

        if (vs.markDeathCounted()) {
            vs.addDeath();
            vs.setAlive(false);
            if (killer != null) {
                PlayerSession ks = match.getSession(killer.getUniqueId());
                if (ks != null && match.contains(killer.getUniqueId()) && vs.getTeam() != ks.getTeam()) {
                    ks.addKill();
                    ks.addMoney(plugin.getConfig().getInt("economy.kill-reward", 300));
                    if (plugin.getOperatorService() != null) {
                        plugin.getOperatorService().onKill(killer);
                    }
                }
            }
        }

        if (plugin.getSpectatorLockService() != null) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getSpectatorLockService().onEnterSpectator(victim));
        }
        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        match.getRoundManager().checkWipe();
    }

    public void teleportQueue(Player player) {
        if (player == null) {
            return;
        }
        Location loc = ConfigKeys.readLocation("locations.queue-spawn");
        if (loc == null) {
            player.sendMessage("§c[DFS] 队列出生点未配置！§e/dfs setspawn queue");
            World w = Worlds.arenaWorld();
            if (w != null) {
                player.setFallDistance(0f);
                player.teleport(w.getSpawnLocation().clone().add(0.5, 0, 0.5));
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
        String node = team == Team.T ? "locations.team-t-spawn" : "locations.team-ct-spawn";
        Location loc = ConfigKeys.readLocation(node);
        if (loc == null) {
            player.sendMessage("§c[DFS] 队伍出生点未配置: " + node);
            teleportQueue(player);
            return;
        }
        player.setFallDistance(0f);
        player.teleport(loc);
        player.setFallDistance(0f);
    }

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

    private void safeSpectatorClear(Player player) {
        try {
            if (plugin.getSpectatorLockService() != null) {
                plugin.getSpectatorLockService().clear(player);
            }
        } catch (Throwable ignored) {
        }
        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
    }

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
