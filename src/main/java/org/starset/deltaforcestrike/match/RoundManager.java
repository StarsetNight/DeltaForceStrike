package org.starset.deltaforcestrike.match;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.config.ConfigKeys;
import org.starset.deltaforcestrike.item.ItemGiveService;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.item.InventorySlots;
import org.starset.deltaforcestrike.shop.ShopGUI;
import org.starset.deltaforcestrike.util.ArenaCleanup;
import org.starset.deltaforcestrike.util.PlayerState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RoundManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final DeltaForceStrike plugin;
    private final Match match;
    private RoundState state = RoundState.IDLE;
    private BukkitTask task;
    private int secondsLeft;
    private boolean halfTimeSwapped = false;
    /** 本购买阶段是否已播放局势 Title（剩 5 秒时一次） */
    private boolean buySituationTitleShown;

    public RoundManager(DeltaForceStrike plugin, Match match) {
        this.plugin = plugin;
        this.match = match;
    }

    public RoundState getState() {
        return state;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public boolean isHalfTimeSwapped() {
        return halfTimeSwapped;
    }

    public void startNextRound() {
        int winTarget = winTarget();
        int maxRounds = maxRounds();
        int halfRound = halfRound();

        if (match.getScoreT() >= winTarget
                || match.getScoreCT() >= winTarget
                || match.getCurrentRound() >= maxRounds) {
            endMatchByScore();
            return;
        }

        if (!halfTimeSwapped
                && match.getCurrentRound() >= halfRound
                && match.getCurrentRound() < maxRounds
                && match.getScoreT() < winTarget
                && match.getScoreCT() < winTarget) {
            doHalfTimeSwap(halfRound);
        }

        match.setCurrentRound(match.getCurrentRound() + 1);
        for (PlayerSession s : match.getSessions().values()) {
            // 新回合：在线与断线占位均重置存活，断线者重连后可回购买阶段
            s.setAlive(true);
            s.resetDeathCounted();
        }
        startBuyPhase();
    }

    /** 加时赛用 overtime.* 配置；普通比赛用 match.* */
    private int winTarget() {
        if (match.isOvertime()) {
            return match.overtimeWinTarget();
        }
        return plugin.getConfig().getInt("match.win-target", 13);
    }

    private int maxRounds() {
        return plugin.getConfig().getInt(match.isOvertime() ? "overtime.max-rounds" : "match.max-rounds",
                match.isOvertime() ? 6 : 24);
    }

    private int halfRound() {
        return plugin.getConfig().getInt(match.isOvertime() ? "overtime.half-round" : "match.half-round",
                match.isOvertime() ? 3 : 12);
    }

    /** 加时赛换边后结算显示用 */
    public String formatWinTarget() {
        return winTarget() + " 胜";
    }

    /** 进入加时赛：重置半场换边标记，让加时按 overtime.half-round 重新换边 */
    public void resetHalfTimeForOvertime() {
        halfTimeSwapped = false;
        buySituationTitleShown = false;
    }

    /** 新对局/结束后彻底重置阶段状态，避免残留到下一场（如上半场/下半场标记） */
    public void resetForNewMatch() {
        cancel();
        state = RoundState.IDLE;
        secondsLeft = 0;
        halfTimeSwapped = false;
        buySituationTitleShown = false;
    }

    private void doHalfTimeSwap(int halfRound) {
        halfTimeSwapped = true;
        if (plugin.getSnapshotService() != null) {
            plugin.getSnapshotService().clear();
        }
        // 加时赛换边经济重置为 overtime.start-money；普通比赛用 economy.start-money
        int startMoney = match.isOvertime()
                ? plugin.getConfig().getInt("overtime.start-money", 10000)
                : plugin.getConfig().getInt("economy.start-money", 800);

        for (PlayerSession s : match.getSessions().values()) {
            if (s.getTeam() == Team.T) {
                s.setTeam(Team.CT);
            } else if (s.getTeam() == Team.CT) {
                s.setTeam(Team.T);
            }
            s.setMoney(startMoney);
            s.setConsecutiveLosses(0);
        }
        match.swapScores();

        for (Player p : match.onlinePlayers()) {
            forceExitSpectator(p);
            PlayerState.clearInventory(p);
            PlayerState.clearPotionEffects(p);
            p.setGameMode(GameMode.ADVENTURE);
        }

        if (plugin.getOperatorService() != null) {
            plugin.getOperatorService().onHalfTime(match);
        }

        broadcastLegacy("§6§l[DFS] 半场结束（" + halfRound + " 回合）！交换攻防！");
        broadcastLegacy("§e金钱、装备与大招充能已重置。§7 比分 §cT " + match.getScoreT()
                + " §7- §b" + match.getScoreCT() + " CT");
        refreshUi();
    }

    private void startBuyPhase() {
        cancel();
        plugin.getBombManager().reset();
        ArenaCleanup.clearDrops();

        state = RoundState.BUY;
        secondsLeft = plugin.getConfig().getInt("round.prepare-time", 20);
        buySituationTitleShown = false;

        broadcastLegacy("§e[DFS] 第 §f" + match.getCurrentRound()
                + " §e回合 · 购买阶段 §f" + secondsLeft + "s"
                + " §8| §7/dfs pause §8战术暂停");

        ItemGiveService give = plugin.getItemGiveService();
        ItemManager items = plugin.getItemManager();

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || !s.hasTeam()) {
                continue;
            }

            s.setAlive(true);
            if (p.isOnline()) {
                s.setConnected(true);
            }
            forceExitSpectator(p);

            p.setInvulnerable(false);
            p.setFallDistance(0f);
            plugin.getGameRulesService().fillHealth(p);
            plugin.getGameRulesService().fillFood(p);
            plugin.getMatchManager().teleportTeamSpawn(p, s.getTeam());

            clearPlantBombOnly(p, items);
            // 清残留选队书
            plugin.getMatchManager().clearTeamSelectBook(p);
            ensureBaselineLoadoutNoBomb(p, s, give, items);
            p.sendMessage("§6资金: §e$" + s.getMoney() + " §7| §a/dfs shop");
        }

        assignBombCarrier(give, items);

        // 最后发技能：7 招牌回满 / 8 购买技能(未用则保留) / 9 大招
        if (plugin.getOperatorService() != null
                && plugin.getConfig().getBoolean("operator.enabled", true)) {
            plugin.getOperatorService().onRoundStart(match);
        }

        // 通知观战/导播角色本回合开始
        if (plugin.getSpectatorRoleManager() != null) {
            plugin.getSpectatorRoleManager().onRoundStart();
        }

        // 通知 PauseService 新回合开始：清掉之前的暂停状态
        if (plugin.getPauseService() != null) {
            plugin.getPauseService().resetRound();
        }

        // 拍快照（onRoundStart 之后，确保干员技能充能状态在快照中）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == RoundState.BUY && plugin.getSnapshotService() != null) {
                plugin.getSnapshotService().captureAtBuyStart();
            }
        }, 2L);

        refreshUi();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state != RoundState.BUY) {
                return;
            }
            for (Player p : match.onlinePlayers()) {
                PlayerSession s = match.getSession(p.getUniqueId());
                if (s == null || !s.hasTeam()) {
                    continue;
                }
                s.setAlive(true);
                forceExitSpectator(p);
                // 再刷一次技能栏，防止其它逻辑覆盖
                if (plugin.getOperatorService() != null) {
                    var load = plugin.getOperatorService().getLoadout(p.getUniqueId());
                    if (load != null) {
                        plugin.getOperatorService().giveSkillHotbarItems(p, load);
                    }
                }
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != RoundState.BUY) {
                return;
            }
            for (Player p : match.onlinePlayers()) {
                PlayerSession s = match.getSession(p.getUniqueId());
                if (s != null && s.hasTeam() && s.isAlive()) {
                    forceExitSpectator(p);
                    ShopGUI.open(p);
                }
            }
            // 聊天栏可点击再次打开商店
            ShopGUI.broadcastChatButtons(match);
        }, 5L);

        task = runBuyTimer();
    }

    /**
     * 购买阶段主计时器：暂停冻结、强制退出旁观、倒计时/局势 Title/UI 刷新。
     * startBuyPhase 与 restartBuyFromSnapshot 共用同一套逻辑。
     */
    private BukkitTask runBuyTimer() {
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != RoundState.BUY) {
                cancel();
                return;
            }
            // 暂停期间冻结计时
            if (plugin.getPauseService() != null && plugin.getPauseService().isPaused()) {
                match.broadcastActionBar("§6⏸ " + plugin.getPauseService().statusLine()
                        + " §7| §e购买阶段 §f" + secondsLeft + "s");
                return;
            }
            for (Player p : match.onlinePlayers()) {
                PlayerSession s = match.getSession(p.getUniqueId());
                if (s != null && s.isPlaying() && s.hasTeam() && s.isAlive()
                        && p.getGameMode() == GameMode.SPECTATOR) {
                    forceExitSpectator(p);
                }
            }
            actionBarLegacy("§e购买阶段 §f" + secondsLeft + "s §7| §a/dfs shop §7| §a/dfs pause §8战术暂停");
            if (secondsLeft <= 0) {
                startCombatPhase();
                return;
            }
            if (secondsLeft == 5 && !buySituationTitleShown) {
                buySituationTitleShown = true;
                showBuyPhaseSituationTitles();
            }
            if (secondsLeft <= 5) {
                broadcastLegacy("§e购买阶段剩余 §c" + secondsLeft + "s");
            }
            refreshUi();
            secondsLeft--;
        }, 0L, 20L);
    }

    /** 暂停恢复后继续购买阶段 */
    public void resumeBuy() {
        if (state != RoundState.BUY) {
            return;
        }
        broadcastLegacy("§a[DFS] 购买阶段继续 §f" + secondsLeft + "s");
    }

    /** 回滚到本回合购买阶段快照 */
    public void restartBuyFromSnapshot() {
        var snap = plugin.getSnapshotService() == null
                ? null : plugin.getSnapshotService().getSnapshot();
        if (snap == null) {
            return;
        }
        cancel();
        ArenaCleanup.clearDrops();
        if (plugin.getBombManager() != null) {
            plugin.getBombManager().reset();
        }
        state = RoundState.BUY;
        secondsLeft = Math.max(1, snap.buySeconds);
        buySituationTitleShown = false;

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null) continue;

            // 观战/导播：跳过还原，重新进入旁观
            if (!s.isPlaying()) {
                if (plugin.getSpectatorRoleManager() != null) {
                    plugin.getSpectatorRoleManager().applyRole(p);
                }
                continue;
            }

            // 还原 session 状态
            var snap_entry = snap.players.get(p.getUniqueId());
            if (snap_entry == null) continue;
            s.setMoney(snap_entry.money);
            s.setConsecutiveLosses(snap_entry.consecutiveLosses);
            s.setAlive(snap_entry.alive);
            s.setConnected(true);
            s.setOperatorId(snap_entry.operatorId);
            s.setDeathCounted(snap_entry.deathCounted);

            forceExitSpectator(p);
            p.setInvulnerable(false);
            p.setFallDistance(0f);
            // 安全恢复血量：MAX_HEALTH 属性可能为 null（1.11+ 弃用 getMaxHealth()），
            // 用属性接口判空兜底，并钳制在有效范围
            double maxHp = 20.0;
            try {
                var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) {
                    maxHp = Math.max(1.0, attr.getValue());
                }
            } catch (Throwable ignored) {
            }
            double hp = snap_entry.health;
            if (Double.isNaN(hp) || hp < 0) {
                hp = 0;
            } else if (hp > maxHp) {
                hp = maxHp;
            }
            p.setHealth(hp);
            p.setFoodLevel(snap_entry.food);
            try { p.setSaturation(snap_entry.saturation); } catch (Throwable ignored) {}
            for (var pe : p.getActivePotionEffects()) p.removePotionEffect(pe.getType());
            if (snap_entry.potionEffects != null) {
                for (var e : snap_entry.potionEffects) p.addPotionEffect(e);
            }
            p.getInventory().setContents(snap_entry.contents);
            p.getInventory().setArmorContents(snap_entry.armor);
            p.getInventory().setItemInOffHand(snap_entry.offHand);
            try { p.getInventory().setHeldItemSlot(snap_entry.heldSlot); } catch (Throwable ignored) {}

            if (snap_entry.world != null
                    && snap_entry.world.equalsIgnoreCase(
                            p.getWorld() == null ? "" : p.getWorld().getName())) {
                Location spawn = s.hasTeam()
                        ? ConfigKeys.readLocation(s.getTeam() == Team.T
                                ? "locations.team-t-spawn" : "locations.team-ct-spawn")
                        : ConfigKeys.readLocation("locations.queue-spawn");
                if (spawn != null) {
                    p.setFallDistance(0f);
                    p.teleport(spawn);
                    p.setFallDistance(0f);
                }
            }
            p.sendMessage("§6[DFS] 已回滚到购买阶段快照 §7($" + s.getMoney() + ")");
        }
        ArenaCleanup.clearDrops();
        if (plugin.getOperatorService() != null
                && plugin.getConfig().getBoolean("operator.enabled", true)) {
            plugin.getOperatorService().onRoundStart(match);
        }
        refreshUi();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state != RoundState.BUY) return;
            for (Player p : match.onlinePlayers()) {
                PlayerSession s = match.getSession(p.getUniqueId());
                if (s != null && s.isPlaying() && s.isAlive()) {
                    forceExitSpectator(p);
                    ShopGUI.open(p);
                }
            }
        });

        task = runBuyTimer();
    }

    /**
     * 购买阶段剩 5 秒：局势 Title。
     * 优先级：决胜局 &gt; 赛点 &gt; 上半场最终局 &gt; 半场手枪局（攻/防视角）
     */
    private void showBuyPhaseSituationTitles() {
        int winTarget = winTarget();
        int halfRound = halfRound();
        int round = match.getCurrentRound();
        int scoreT = match.getScoreT();
        int scoreCT = match.getScoreCT();

        int needT = winTarget - scoreT;
        int needCT = winTarget - scoreCT;

        boolean decisive = (needT == 1 && needCT == 2) || (needCT == 1 && needT == 2);
        boolean matchPoint = needT == 1 || needCT == 1;
        boolean firstHalfFinal = !halfTimeSwapped && round == halfRound;
        boolean firstHalfPistol = !halfTimeSwapped && round == 1;
        boolean secondHalfPistol = halfTimeSwapped && round == halfRound + 1;

        Title.Times times = Title.Times.times(
                Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400));
        Component scoreSub = Component.text(
                "比分 T " + scoreT + " - " + scoreCT + " CT  ·  回合 " + round,
                NamedTextColor.GRAY);

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            Component main;
            Component sub = scoreSub;

            if (decisive) {
                main = Component.text("决胜局", NamedTextColor.GOLD, TextDecoration.BOLD);
                sub = Component.text("一方胜出，抑或双方战平", NamedTextColor.YELLOW)
                        .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                        .append(scoreSub);
            } else if (matchPoint) {
                main = Component.text("赛点", NamedTextColor.RED, TextDecoration.BOLD);
                if (needT == 1 && needCT == 1) {
                    sub = Component.text("双方均差 1 分胜利", NamedTextColor.YELLOW)
                            .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                            .append(scoreSub);
                    // 一般不会触发，因为如果配置正常，双方均差1分胜利应该是平局
                } else if (needT == 1) {
                    sub = Component.text("进攻方 T 再胜 1 分胜利", NamedTextColor.RED)
                            .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                            .append(scoreSub);
                } else {
                    sub = Component.text("防守方 CT 再胜 1 分胜利", NamedTextColor.AQUA)
                            .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                            .append(scoreSub);
                }
            } else if (firstHalfFinal) {
                main = Component.text("上半场最终局", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
            } else if (firstHalfPistol || secondHalfPistol) {
                String half = firstHalfPistol ? "上半场" : "下半场";
                String role = "观战";
                if (s != null) {
                    if (s.getTeam() == Team.T) {
                        role = "进攻方";
                    } else if (s.getTeam() == Team.CT) {
                        role = "防守方";
                    }
                }
                main = Component.text(half, NamedTextColor.GOLD, TextDecoration.BOLD);
                sub = Component.text("以" + role + "进行游戏", NamedTextColor.YELLOW)
                        .append(Component.text(" · 手枪局", NamedTextColor.GRAY));
            } else {
                continue; // 无特殊局势，不弹 Title
            }

            p.showTitle(Title.title(main, sub, times));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        }
    }

    private void forceExitSpectator(Player p) {
        if (p == null || !p.isOnline()) {
            return;
        }
        try {
            if (plugin.getSpectatorLockService() != null) {
                plugin.getSpectatorLockService().clear(p);
            }
        } catch (Throwable ignored) {
        }
        try {
            p.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
        if (p.getGameMode() != GameMode.ADVENTURE) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        p.setFlying(false);
        p.setAllowFlight(false);
        try {
            p.setFlySpeed(0.1f);
            p.setWalkSpeed(0.2f);
        } catch (Throwable ignored) {
        }
        p.setFireTicks(0);
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);
    }

    private void ensureBaselineLoadoutNoBomb(Player p, PlayerSession s,
                                             ItemGiveService give, ItemManager items) {
        PlayerInventory inv = p.getInventory();

        ItemStack melee = inv.getItem(InventorySlots.MELEE);
        if (melee == null || melee.getType().isAir()) {
            give.give(p, "standard", true);
        }

        ItemStack off = inv.getItemInOffHand();
        boolean offEmpty = off.getType().isAir() || off.getAmount() <= 0;
        if (ConfigKeys.shieldEnabled()) {
            if (offEmpty || !items.isShield(off)) {
                give.give(p, "equipments.shield", true);
            }
        } else if (!offEmpty && (items.isShield(off) || off.getType() == Material.SHIELD)) {
            inv.setItemInOffHand(null);
        }

        if (give.hasRangedWeapon(p)) {
            give.refillArrowsTo(p, ConfigKeys.arrowsPerRanged());
        }

        if (s.getTeam() == Team.CT) {
            ItemStack slot2 = inv.getItem(InventorySlots.BOMB);
            if (slot2 != null && isPlantBombItem(items, slot2)) {
                inv.setItem(InventorySlots.BOMB, null);
            }
        }
    }

    private void clearPlantBombOnly(Player p, ItemManager items) {
        for (int i = 0; i <= 8; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st != null && isPlantBombItem(items, st)) {
                p.getInventory().setItem(i, null);
            }
        }
    }

    private void assignBombCarrier(ItemGiveService give, ItemManager items) {
        List<Player> tPlayers = new ArrayList<>();
        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || s.getTeam() != Team.T || !s.isConnected()) {
                continue;
            }
            tPlayers.add(p);
        }
        if (tPlayers.isEmpty()) {
            broadcastLegacy("§c[DFS] 进攻方无人，未分配改造TNT。");
            return;
        }
        for (Player p : tPlayers) {
            clearPlantBombOnly(p, items);
        }
        Collections.shuffle(tPlayers);
        Player carrier = tPlayers.get(0);

        ItemStack slot2 = carrier.getInventory().getItem(InventorySlots.BOMB);
        if (slot2 != null && !slot2.getType().isAir()) {
            carrier.getInventory().setItem(InventorySlots.BOMB, null);
        }

        boolean ok = give.give(carrier, "bomb.plant-bomb", true);
        if (ok) {
            carrier.sendMessage("§c§l[DFS] 你携带改造TNT！§7请安装到包点。（热键第3格）");
            for (Player p : tPlayers) {
                if (!p.equals(carrier)) {
                    p.sendMessage("§7[DFS] 队友 §c" + carrier.getName() + " §7携带改造TNT。");
                }
            }
        } else {
            broadcastLegacy("§c[DFS] 改造TNT发放失败。");
        }
    }

    private boolean isPlantBombItem(ItemManager items, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        String action = null;
        if (stack.hasItemMeta()) {
            action = stack.getItemMeta().getPersistentDataContainer().get(
                    ItemKeys.action(),
                    org.bukkit.persistence.PersistentDataType.STRING);
        }
        if ("defuse".equalsIgnoreCase(action)) {
            return false;
        }
        String type = items.getItemType(stack);
        if ("defuse".equalsIgnoreCase(type)) {
            return false;
        }
        String id = items.getItemId(stack);
        if (id != null && id.toLowerCase(Locale.ROOT).contains("defuse")) {
            return false;
        }
        if ("bomb".equalsIgnoreCase(type) || "plant".equalsIgnoreCase(action)) {
            return true;
        }
        return id != null && id.contains("plant-bomb");
    }

    private void startCombatPhase() {
        cancel();
        // 购买阶段仍断线的玩家：本回合视为阵亡（不占“存活”坑）
        for (PlayerSession s : match.getSessions().values()) {
            if (!s.isConnected() && s.isAlive()) {
                if (s.markDeathCounted()) {
                    s.addDeath();
                }
                s.setAlive(false);
            }
        }
        for (Player p : match.onlinePlayers()) {
            p.closeInventory();
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s != null && s.isAlive()) {
                forceExitSpectator(p);
            }
        }
        state = RoundState.COMBAT;
        secondsLeft = plugin.getConfig().getInt("round.combat-time", 100);
        broadcastLegacy("§c§l[DFS] 战斗开始！");
        refreshUi();
        checkWipe();

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != RoundState.COMBAT) {
                cancel();
                return;
            }
            actionBarLegacy("§c进攻时间 §f" + formatTime(secondsLeft));
            if (secondsLeft <= 0) {
                endRound(Team.CT, "进攻超时，防守方胜利");
                return;
            }
            refreshUi();
            secondsLeft--;
        }, 0L, 20L);
    }

    public void onBombPlanted() {
        if (state != RoundState.COMBAT) {
            return;
        }
        cancel();
        state = RoundState.BOMB_PLANTED;
        secondsLeft = plugin.getBombManager().getFuseLeft();
        broadcastLegacy("§c[DFS] 拆弹阶段！§7CT 潜行靠近拆除（钳在第3格更快）");
        refreshUi();

        if (match.allDead(Team.CT)) {
            endRound(Team.T, "防守方全灭");
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != RoundState.BOMB_PLANTED) {
                cancel();
                return;
            }
            int fuse = plugin.getBombManager().getFuseLeft();
            secondsLeft = fuse;
            if (fuse >= 0) {
                actionBarLegacy("§4爆炸倒计时 §c" + fuse + "s");
            }
            refreshUi();
        }, 0L, 5L);
    }

    public void checkWipe() {
        if (state != RoundState.COMBAT && state != RoundState.BOMB_PLANTED) {
            return;
        }
        if (match.allDead(Team.CT)) {
            endRound(Team.T, "防守方全灭");
            return;
        }
        if (state == RoundState.COMBAT && match.allDead(Team.T)) {
            endRound(Team.CT, "进攻方全灭");
        }
    }

    public void endRound(Team winner, String reason) {
        if (state == RoundState.ROUND_END || state == RoundState.IDLE) {
            return;
        }
        cancel();
        state = RoundState.ROUND_END;

        plugin.getBombManager().reset();
        ArenaCleanup.clearDrops();

        if (plugin.getOperatorService() != null) {
            plugin.getOperatorService().onRoundEnd(match);
        }

        match.addScore(winner);
        applyEconomy(winner);

        broadcastLegacy("§6[DFS] 回合结束: §f" + (reason == null ? "" : reason));
        broadcastLegacy("§7比分 §cT " + match.getScoreT() + " §7- §b" + match.getScoreCT() + " CT");

        showRoundResultTitles(winner, reason);
        refreshUi();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ArenaCleanup.clearDrops();
            startNextRound();
        }, 8 * 20L);
    }

    private void showRoundResultTitles(Team winner, String reason) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(150), Duration.ofSeconds(3), Duration.ofMillis(500));
        Component winMain = Component.text("回合胜利", NamedTextColor.GREEN, TextDecoration.BOLD);
        Component loseMain = Component.text("回合败北", NamedTextColor.RED, TextDecoration.BOLD);
        Component reasonComp = Component.text(reason == null ? "" : reason, NamedTextColor.GRAY);
        Component scoreComp = Component.text(
                "比分 T " + match.getScoreT() + " - " + match.getScoreCT() + " CT",
                NamedTextColor.YELLOW);
        Component subtitle = reasonComp
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(scoreComp);

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || !s.hasTeam()) {
                p.showTitle(Title.title(
                        Component.text("回合结束", NamedTextColor.GOLD, TextDecoration.BOLD),
                        scoreComp, times));
                continue;
            }
            if (s.getTeam() == winner) {
                p.showTitle(Title.title(winMain, subtitle, times));
                // 胜负音效改由 ClientUI 音乐盒播放，避免与自定义曲冲突
            } else {
                p.showTitle(Title.title(loseMain, subtitle, times));
            }
        }
    }

    private void applyEconomy(Team winner) {
        int winMoney = plugin.getConfig().getInt("economy.victory-bonus", 3200);
        List<Integer> defeat = plugin.getConfig().getIntegerList("economy.defeat-bonus");
        if (defeat.isEmpty()) {
            defeat = List.of(2000, 2550, 3100);
        }
        int pistol = plugin.getConfig().getInt("economy.pistol-round-bonus", 2550);

        for (PlayerSession s : match.getSessions().values()) {
            if (!s.hasTeam()) {
                continue;
            }
            if (s.getTeam() == winner) {
                s.addMoney(winMoney);
                s.setConsecutiveLosses(0);
            } else {
                int losses = s.getConsecutiveLosses() + 1;
                s.setConsecutiveLosses(losses);
                if (match.getCurrentRound() == 1) {
                    s.addMoney(pistol);
                } else {
                    s.addMoney(defeat.get(Math.min(losses - 1, defeat.size() - 1)));
                }
            }
        }
    }

    private void endMatchByScore() {
        cancel();
        state = RoundState.IDLE;
        plugin.getBombManager().reset();
        ArenaCleanup.clearDrops();
        String msg;
        if (match.getScoreT() > match.getScoreCT()) {
            msg = "进攻方 T 获胜！最终 " + match.getScoreT() + "-" + match.getScoreCT();
        } else if (match.getScoreCT() > match.getScoreT()) {
            msg = "防守方 CT 获胜！最终 " + match.getScoreT() + "-" + match.getScoreCT();
        } else {
            msg = "平局！最终 " + match.getScoreT() + "-" + match.getScoreCT();
        }
        // 交给 MatchManager 进入结算窗口：平局等待 /dfs overtime，超时自动判结束
        plugin.getMatchManager().enterEndWindow(msg);
    }

    private void actionBarLegacy(String legacyMsg) {
        Component c = LEGACY.deserialize(legacyMsg == null ? "" : legacyMsg);
        for (Player p : match.onlinePlayers()) {
            p.sendActionBar(c);
        }
    }

    private void broadcastLegacy(String legacyMsg) {
        if (legacyMsg == null) {
            return;
        }
        for (Player p : match.onlinePlayers()) {
            p.sendMessage(legacyMsg);
        }
    }

    private String formatTime(int sec) {
        sec = Math.max(0, sec);
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    private void refreshUi() {
        if (plugin.getScoreboardService() != null) {
            plugin.getScoreboardService().updateAll(match);
        }
        if (plugin.getTabListService() != null) {
            plugin.getTabListService().updateAll(match);
        }
        if (plugin.getHudSyncService() != null) {
            plugin.getHudSyncService().tick();
        }
    }

    public void shutdown() {
        cancel();
        state = RoundState.IDLE;
        plugin.getBombManager().reset();
    }

    private void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
