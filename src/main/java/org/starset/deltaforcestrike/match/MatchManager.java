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
import org.bukkit.inventory.ItemStack;
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

    /** 结算窗口：比赛结束后标题持续展示，等待 /dfs overtime 或超时判结束 */
    private BukkitTask endWindowTask;
    private int endWindowLeft;
    private boolean endWindowActive;
    /** 最终结束流程是否已启动（防 forceEnd 重复执行） */
    private boolean endScheduled;

    private static final long END_RESET_DELAY_TICKS = 70L;

    public MatchManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
        resetMatchWaiting();
    }

    private void resetMatchWaiting() {
        if (match != null) {
            match.getRoundManager().shutdown();
        }
        endWindowActive = false;
        endScheduled = false;
        if (endWindowTask != null) {
            endWindowTask.cancel();
            endWindowTask = null;
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
        // 能进队列则进；否则（对局中/已满/结算）仍放到 queue 区，避免落在战场
        if (!tryJoin(player)) {
            parkAtQueue(player, true);
        }
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
            // 已在对局 session 中（含断线占位）→ 重连归队，不新建 session
            handleReconnect(player);
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
            player.sendMessage("§c[DFS] 当前对局进行中，已将你传送至队列区等待。");
            return false;
        }

        int max = ConfigKeys.maxPlayers();
        // 导播不占名额；占名额 = 参赛 + 旁观
        if (match.occupiedSlots() >= max) {
            player.sendMessage("§c[DFS] 房间已满（占名额 " + match.occupiedSlots() + "/" + max
                    + "），已将你传送至队列区。");
            return false;
        }

        int startMoney = plugin.getConfig().getInt("economy.start-money", 800);
        var session = new PlayerSession(player, startMoney);
        match.getSessions().put(player.getUniqueId(), session);

        prepareQueuePlayer(player);

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
                TeamSelectUI.giveBook(player);
                player.sendMessage("§e[DFS] 右键 §6选择队伍 §e书打开选边界面");
                if (plugin.getConfig().getBoolean("operator.select-enabled", false)) {
                    player.sendMessage("§d干员: §f/dfs agent §7或选人阶段打开界面");
                }
            }
        }, 10L);

        checkAutoStart();
        return true;
    }

    /**
     * 进入队列：清空物品/状态，冒险模式，满血满食。
     */
    private void prepareQueuePlayer(Player player) {
        parkAtQueue(player, false);
        // 仅真正入队时发选队书
        TeamSelectUI.giveBook(player);
        player.updateInventory();
    }

    /**
     * 放到 queue 出生点并清状态，但不加入 Match session。
     * 用于对局进行中/队列已满时新进维度的玩家。
     *
     * @param notify 是否已由调用方发过提示（避免重复）
     */
    public void parkAtQueue(Player player, boolean notify) {
        if (player == null || !player.isOnline() || !Worlds.isArena(player)) {
            return;
        }
        // 已在对局里的不要当旁观者清掉
        if (match != null && match.contains(player.getUniqueId())) {
            return;
        }
        try {
            if (plugin.getSpectatorLockService() != null) {
                plugin.getSpectatorLockService().clear(player);
            }
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
        player.getInventory().setHeldItemSlot(0);

        for (var pe : player.getActivePotionEffects()) {
            player.removePotionEffect(pe.getType());
        }
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setGliding(false);
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        try {
            player.setAbsorptionAmount(0);
        } catch (Throwable ignored) {
        }

        player.setGameMode(GameMode.ADVENTURE);
        // 未入队旁观等待：可受伤关闭，避免乱入战场被打
        player.setInvulnerable(true);
        teleportQueue(player);
        plugin.getGameRulesService().fillFood(player);
        plugin.getGameRulesService().fillHealth(player);
        player.updateInventory();

        if (notify) {
            // tryJoin 已发过具体原因时可不重复；此处作兜底
            MatchState st = match == null ? null : match.getState();
            if (st == MatchState.IN_PROGRESS || st == MatchState.AGENT_SELECT) {
                player.sendMessage("§7[DFS] 请在队列区等待本局结束。");
            }
        }
    }

    /**
     * 主动离开（/dfs leave、离开竞技世界）：从对局移除。
     */
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
        if (player.isOnline()) {
            player.setInvulnerable(false);
        }

        match.broadcast("§e[DFS] §f" + player.getName() + " §7已离开。 §8(" + match.size() + ")");

        if (state == MatchState.COUNTDOWN) {
            if (plugin.getConfig().getBoolean("queue.cancel-if-not-full", true)
                    && match.size() < ConfigKeys.maxPlayers()) {
                cancelCountdown("人数不足，倒计时取消。");
            }
        } else if (state == MatchState.IN_PROGRESS) {
            match.getRoundManager().checkWipe();
            if (match.size() == 0 || match.onlineCount() == 0) {
                forceEnd("所有玩家已离开。");
            }
        } else if (state == MatchState.AGENT_SELECT && match.size() == 0) {
            forceEnd("所有玩家已离开。");
        }

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        sendQueueActionBar();
    }

    /**
     * 断线：对局进行中保留 session（不踢出对局）；
     * 战斗/拆弹阶段立即判死；购买/结算阶段保留存活，重连可归队。
     * 队列阶段仍完整 leave。
     */
    public void handleDisconnect(Player player) {
        if (player == null || match == null || !match.contains(player.getUniqueId())) {
            return;
        }

        MatchState state = match.getState();
        if (state != MatchState.IN_PROGRESS) {
            leave(player);
            return;
        }

        PlayerSession session = match.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        session.setConnected(false);
        RoundState rs = match.getRoundManager().getState();

        if (rs == RoundState.COMBAT || rs == RoundState.BOMB_PLANTED) {
            if (session.isAlive()) {
                try {
                    DeathDrops.dropAndClearLoadout(player);
                } catch (Throwable ignored) {
                }
                if (session.markDeathCounted()) {
                    session.addDeath();
                }
                session.setAlive(false);
                match.broadcast("§e[DFS] §f" + player.getName()
                        + " §7断线，本回合视为阵亡。 §8(仍在对局中)");
            } else {
                match.broadcast("§e[DFS] §f" + player.getName()
                        + " §7断线（已阵亡）。 §8(仍在对局中)");
            }
        } else if (rs == RoundState.BUY || rs == RoundState.ROUND_END) {
            // 购买/结算：不断线判死，重连可回场
            match.broadcast("§e[DFS] §f" + player.getName()
                    + " §7断线 · 购买阶段可重连归队。");
        } else {
            match.broadcast("§e[DFS] §f" + player.getName() + " §7断线。");
        }

        safeScoreboardRemove(player);
        safeTabReset(player);
        safeSpectatorClear(player);

        match.getRoundManager().checkWipe();
        if (match.onlineCount() == 0) {
            forceEnd("所有玩家已断线。");
            return;
        }

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
    }

    /**
     * 重连归队：session 仍在对局中。
     * 购买阶段 → 恢复存活；战斗阶段 → 旁观（断线已判死，或购买中断线后进战斗亦判死）。
     */
    public void handleReconnect(Player player) {
        if (player == null || match == null || !match.contains(player.getUniqueId())) {
            return;
        }

        PlayerSession session = match.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        session.setConnected(true);
        MatchState state = match.getState();

        player.setInvulnerable(false);
        player.setFallDistance(0f);
        safeScoreboardCreate(player);
        safeTabUpdate(player);

        if (state != MatchState.IN_PROGRESS) {
            player.setGameMode(GameMode.ADVENTURE);
            teleportQueue(player);
            plugin.getGameRulesService().fillHealth(player);
            plugin.getGameRulesService().fillFood(player);
            player.sendMessage("§a[DFS] 已重连队列。");
            safeScoreboardUpdateAll();
            safeTabUpdateAll();
            return;
        }

        RoundState rs = match.getRoundManager().getState();

        if (rs == RoundState.BUY || rs == RoundState.ROUND_END) {
            // 购买阶段重连：不判死，回出生点
            session.setAlive(true);
            session.resetDeathCounted();
            try {
                if (plugin.getSpectatorLockService() != null) {
                    plugin.getSpectatorLockService().clear(player);
                }
                player.setSpectatorTarget(null);
            } catch (Throwable ignored) {
            }
            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
            plugin.getGameRulesService().fillHealth(player);
            plugin.getGameRulesService().fillFood(player);
            if (session.hasTeam()) {
                teleportTeamSpawn(player, session.getTeam());
            } else {
                teleportQueue(player);
            }
            player.sendMessage("§a[DFS] 已重连 · 购买阶段，已归队。§7 /dfs shop");
            match.broadcast("§a[DFS] §f" + player.getName() + " §7重连归队（购买阶段）。");
        } else {
            // 战斗 / 拆弹：必须旁观
            if (session.isAlive()) {
                // 购买阶段断线、战斗开始后才重连 → 本回合判死
                if (session.markDeathCounted()) {
                    session.addDeath();
                }
                session.setAlive(false);
                player.sendMessage("§c[DFS] 已重连 · 战斗已开始，本回合视为阵亡，进入观战。");
                match.broadcast("§e[DFS] §f" + player.getName()
                        + " §7重连过晚，本回合阵亡观战。");
            } else {
                player.sendMessage("§c[DFS] 已重连 · 本回合已阵亡，进入观战。");
                match.broadcast("§a[DFS] §f" + player.getName() + " §7重连观战。");
            }
            player.setGameMode(GameMode.SPECTATOR);
            plugin.getGameRulesService().fillFood(player);
            if (plugin.getSpectatorLockService() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && match != null
                            && match.contains(player.getUniqueId())
                            && !session.isAlive()) {
                        plugin.getSpectatorLockService().onEnterSpectator(player);
                    }
                });
            }
            match.getRoundManager().checkWipe();
        }

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
    }

    private void checkAutoStart() {
        if (match == null || match.getState() != MatchState.WAITING) {
            return;
        }
        // 仅看占名额的参赛选手是否达到上限
        if (match.playingCount() >= ConfigKeys.maxPlayers()) {
            startCountdown();
        }
    }

    /** 加入旁观者（占房间名额：spectator + T + CT = max）。仅对局中可用。 */
    public boolean joinAsSpectator(Player player) {
        if (player == null) return false;
        if (!Worlds.isArena(player)) {
            player.sendMessage("§c[DFS] 只能在竞技世界旁观。");
            return false;
        }
        if (match == null) {
            resetMatchWaiting();
        }
        if (match.contains(player.getUniqueId())) {
            return setRoleAndApply(player,
                    org.starset.deltaforcestrike.spectator.SpectatorRole.SPECTATOR);
        }
        int max = ConfigKeys.maxPlayers();
        if (match.occupiedSlots() >= max) {
            player.sendMessage("§c[DFS] 房间已满（占名额 " + max + "）。");
            parkAtQueue(player, true);
            return false;
        }
        int startMoney = 0;
        var session = new PlayerSession(player, startMoney);
        session.setRole(org.starset.deltaforcestrike.spectator.SpectatorRole.SPECTATOR);
        session.setAlive(false);
        match.getSessions().put(player.getUniqueId(), session);
        if (plugin.getSpectatorRoleManager() != null) {
            plugin.getSpectatorRoleManager().applyRole(player);
        }
        safeScoreboardCreate(player);
        safeTabUpdate(player);
        match.broadcast("§7[DFS] §f" + player.getName() + " §7以观战模式加入。"
                + " §8(占名额 " + match.occupiedSlots() + "/" + max + ")");
        return true;
    }

    /** 加入导播（不占任何名额）。仅对局中可用，管理员/导播权限。 */
    public boolean joinAsObserver(Player player) {
        if (player == null) return false;
        if (!Worlds.isArena(player)) {
            player.sendMessage("§c[DFS] 只能在竞技世界导播。");
            return false;
        }
        if (match == null) {
            resetMatchWaiting();
        }
        if (match.contains(player.getUniqueId())) {
            return setRoleAndApply(player,
                    org.starset.deltaforcestrike.spectator.SpectatorRole.OBSERVER);
        }
        var session = new PlayerSession(player, 0);
        session.setRole(org.starset.deltaforcestrike.spectator.SpectatorRole.OBSERVER);
        session.setAlive(false);
        match.getSessions().put(player.getUniqueId(), session);
        if (plugin.getSpectatorRoleManager() != null) {
            plugin.getSpectatorRoleManager().applyRole(player);
        }
        match.broadcast("§b[DFS] §f" + player.getName() + " §b以导播模式加入。§7(不占名额)");
        return true;
    }

    private boolean setRoleAndApply(Player player,
                                     org.starset.deltaforcestrike.spectator.SpectatorRole role) {
        if (plugin.getSpectatorRoleManager() == null) return false;
        return plugin.getSpectatorRoleManager().setRole(player, role);
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
                    && match.occupiedSlots() < ConfigKeys.maxPlayers()) {
                cancelCountdown("有人离开或人数不足，倒计时取消。");
                return;
            }
            if (match.occupiedSlots() < 1) {
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
            SpigotCompat.actionBar(p, Component.text(
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
            // 仅一行广播，不刷全员名单（避免挡点击）
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
        // 选择 T/CT 即视为参赛选手（从观战/导播切回）
        if (!self.isPlaying()) {
            self.setRole(org.starset.deltaforcestrike.spectator.SpectatorRole.PLAYING);
            self.setAlive(true);
            player.sendMessage("§a[DFS] 你已切换为参赛选手。");
            if (plugin.getSpectatorRoleManager() != null) {
                plugin.getSpectatorRoleManager().clear(player);
            }
        }
        if (self.getTeam() == team) {
            player.sendMessage("§7你已在该队伍。");
            TeamSelectUI.sendSelected(player, team);
            TeamSelectUI.refreshOpenGuis(match);
            return true;
        }
        if (match.countTeam(team) >= teamSize) {
            player.sendMessage("§c[DFS] 该队伍已满（" + teamSize + "）。");
            TeamSelectUI.refreshOpenGuis(match);
            return false;
        }

        self.setTeam(team);
        TeamSelectUI.sendSelected(player, team);
        match.broadcast("§7" + player.getName() + " → "
                + (team == Team.T ? "§cT" : "§bCT")
                + " §8(T " + match.countTeam(Team.T) + " / CT " + match.countTeam(Team.CT) + ")");

        TeamSelectUI.refreshOpenGuis(match);
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
        // 新对局：清掉暂停次数、快照、加时状态、半场换边标记
        if (plugin.getPauseService() != null) {
            plugin.getPauseService().resetForNewMatch();
        }
        if (plugin.getSnapshotService() != null) {
            plugin.getSnapshotService().clear();
        }
        match.getRoundManager().resetForNewMatch();
        match.setOvertime(false);
        match.setOvertimeCount(0);
        match.setOvertimeBaseScore(0);
        match.broadcast("§a§l[DFS] 对局开始！");
        match.broadcast("§e提示: T 包点下包 · CT 潜行拆包 · §a/dfs guide §e· §a/dfs shop");

        for (Player p : match.onlinePlayers()) {
            p.setInvulnerable(false);
            p.setFallDistance(0f);
            safeSpectatorClear(p);
            // 清掉选队书，避免占热键第 1 格导致近战剑放不进
            clearTeamSelectBook(p);
        }

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        match.getRoundManager().startNextRound();
    }

    /**
     * 比赛自然结束 → 进入结算窗口。
     * 标题持续显示 end-window-seconds 秒；若平局则等待 /dfs overtime 进入加时，
     * 超时未处理则判比赛结束（回队列）。
     */
    public void enterEndWindow(String reason) {
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
        if (plugin.getPauseService() != null) {
            plugin.getPauseService().cancelActive();
        }
        if (plugin.getSnapshotService() != null) {
            plugin.getSnapshotService().clear();
        }
        ArenaCleanup.clearDrops();

        final int finalT = match.getScoreT();
        final int finalCT = match.getScoreCT();
        final boolean isDraw = finalT == finalCT;
        final String reasonText = reason == null ? "" : reason;

        showMatchEndTitles(finalT, finalCT);
        match.broadcast("§c[DFS] 对局结束: §f" + reasonText);
        match.broadcast("§6最终比分 §cT " + finalT + " §7- §b" + finalCT + " CT");

        if (isDraw) {
            match.broadcast("§e[DFS] 平局！管理员可在结算窗口内输入 §a/dfs overtime §e进入加时赛。");
        }

        endWindowActive = true;
        endWindowLeft = Math.max(5, plugin.getConfig().getInt("match.end-window-seconds", 30));
        match.broadcast("§7结算窗口 §f" + endWindowLeft + "s §7后自动结束"
                + (isDraw ? "（超时判本场结束，不回放加时）" : ""));

        // 每 5 秒重发标题，保证始终显示
        endWindowTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!endWindowActive || match == null) {
                return;
            }
            showMatchEndTitles(match.getScoreT(), match.getScoreCT());
            endWindowLeft -= 5;
            if (endWindowLeft <= 0) {
                forceEnd(isDraw ? "平局结算窗口超时，本场结束" : reasonText);
            }
        }, 100L, 100L);
    }

    /** 管理员 /dfs overtime：平局结算窗口内进入加时赛（比分继承正赛） */
    public boolean enterOvertimeFromEndWindow(Player admin) {
        if (match == null || !endWindowActive || match.getState() != MatchState.ENDING) {
            if (admin != null) {
                admin.sendMessage("§c[DFS] 当前不在结算窗口内。");
            }
            return false;
        }
        if (match.getScoreT() != match.getScoreCT()) {
            if (admin != null) {
                admin.sendMessage("§c[DFS] 只有平局才能进入加时赛。");
            }
            return false;
        }
        // 取消结算窗口
        endWindowActive = false;
        if (endWindowTask != null) {
            endWindowTask.cancel();
            endWindowTask = null;
        }

        match.enterOvertime(); // 设置 overtime + PauseService 重置
        match.setOvertimeCount(match.getOvertimeCount() + 1);
        // 加时赛胜场目标 = 当前比分基准 + overtime.win-target（比分继承）
        match.setOvertimeBaseScore(match.getScoreT()); // 平局 T==CT，取任一即可

        // 比分继承，经济按配置重置（新开一把）
        boolean resetEco = plugin.getConfig().getBoolean("overtime.reset-economy", true);
        int otMoney = plugin.getConfig().getInt("overtime.start-money", 10000);
        for (PlayerSession s : match.getSessions().values()) {
            if (resetEco) {
                s.setMoney(otMoney);
                s.setConsecutiveLosses(0);
            }
            s.setAlive(true);
            s.resetDeathCounted();
        }
        if (plugin.getSnapshotService() != null) {
            plugin.getSnapshotService().clear();
        }
        if (plugin.getBombManager() != null) {
            plugin.getBombManager().reset();
        }
        if (plugin.getOperatorService() != null
                && plugin.getConfig().getBoolean("operator.enabled", true)) {
            plugin.getOperatorService().prepareMatch(match);
        }

        match.setCurrentRound(0);
        match.setState(MatchState.IN_PROGRESS);
        // 加时赛重新开始半场换边流程（前 overtime.half-round 回合为加时上半场）
        match.getRoundManager().resetHalfTimeForOvertime();
        int otWin = plugin.getConfig().getInt("overtime.win-target", 4);
        int otHalf = plugin.getConfig().getInt("overtime.half-round", 3);
        int otTarget = match.overtimeWinTarget();
        int otCount = match.getOvertimeCount();
        match.broadcast("§6§l[DFS] 加时赛 #" + otCount + " 开始！比分继承正赛，先到 §e"
                + otTarget + " §6胜获胜（加时再赢 §e" + otWin + " §6场，"
                + otHalf + " 回合换边）");
        match.broadcast("§7当前比分 §cT " + match.getScoreT()
                + " §7- §b" + match.getScoreCT() + " CT");

        safeScoreboardUpdateAll();
        safeTabUpdateAll();
        match.getRoundManager().startNextRound();
        return true;
    }

    /** 是否处于结算窗口（供命令限制用） */
    public boolean isInEndWindow() {
        return endWindowActive && match != null && match.getState() == MatchState.ENDING;
    }

    public void forceEnd(String reason) {
        if (match == null) {
            return;
        }
        if (endScheduled) {
            return;
        }
        endScheduled = true;
        cancelTasks();
        cancelEndWindow();

        // 若在下半场被 stop 结束：把比赛情况重置到上半场（比分对调回），
        // 避免最终比分展示为换边后口径、且下一场残留下半场标记
        if (match.getRoundManager().isHalfTimeSwapped() && !match.isOvertime()) {
            match.swapScores();
            if (reason == null || reason.isEmpty()) {
                reason = "比赛重置到上半场后结束";
            }
        }

        match.setState(MatchState.ENDING);
        match.getRoundManager().shutdown();
        if (plugin.getBombManager() != null) {
            plugin.getBombManager().reset();
        }
        if (plugin.getPauseService() != null) {
            plugin.getPauseService().cancelActive();
        }
        if (plugin.getSnapshotService() != null) {
            plugin.getSnapshotService().clear();
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

    private void cancelEndWindow() {
        endWindowActive = false;
        if (endWindowTask != null) {
            endWindowTask.cancel();
            endWindowTask = null;
        }
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
                SpigotCompat.title(p, Title.title(drawMain, scoreSub, times));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                continue;
            }
            if (s == null || !s.hasTeam()) {
                SpigotCompat.title(p, Title.title(endMain, scoreSub, times));
                continue;
            }
            if (s.getTeam() == winner) {
                SpigotCompat.title(p, Title.title(winMain, scoreSub, times));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
            } else {
                SpigotCompat.title(p, Title.title(loseMain, scoreSub, times));
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

    /** 移除选队书（对局开始 / 清栏时） */
    public void clearTeamSelectBook(Player player) {
        if (player == null) {
            return;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (TeamSelectUI.isTeamSelectBook(st)) {
                inv.setItem(i, null);
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (TeamSelectUI.isTeamSelectBook(off)) {
            inv.setItemInOffHand(null);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (TeamSelectUI.isTeamSelectBook(cursor)) {
            player.setItemOnCursor(null);
        }
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
        cancelEndWindow();
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
