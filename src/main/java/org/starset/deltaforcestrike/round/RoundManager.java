package org.starset.deltaforcestrike.round;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemGiveService;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.shop.ShopGUI;
import org.starset.deltaforcestrike.util.ArenaCleanup;
import org.starset.deltaforcestrike.util.ConfigKeys;
import org.starset.deltaforcestrike.util.InventorySlots;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 回合状态机：BUY → COMBAT → BOMB_PLANTED → ROUND_END
 * ActionBar/Title 均不触发 Adventure LegacyFormattingDetected。
 */
public class RoundManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final DeltaForceStrike plugin;
    private final Match match;
    private RoundState state = RoundState.IDLE;
    private BukkitTask task;
    private int secondsLeft;
    private boolean halfTimeSwapped = false;

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

    // =====================================================================
    // 回合推进
    // =====================================================================

    public void startNextRound() {
        int winTarget = plugin.getConfig().getInt("match.win-target", 5);
        int maxRounds = plugin.getConfig().getInt("match.max-rounds", 8);
        int halfRound = plugin.getConfig().getInt("match.half-round", 4);

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
            if (s.isConnected()) {
                s.setAlive(true);
            }
        }
        startBuyPhase();
    }

    private void doHalfTimeSwap(int halfRound) {
        halfTimeSwapped = true;
        int startMoney = plugin.getConfig().getInt("economy.start-money", 800);

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
            p.getInventory().clear();
            p.getInventory().setHelmet(null);
            p.getInventory().setChestplate(null);
            p.getInventory().setLeggings(null);
            p.getInventory().setBoots(null);
            p.getInventory().setItemInOffHand(null);
            p.setGameMode(GameMode.ADVENTURE);
        }

        broadcastLegacy("§6§l[DFS] 半场结束（" + halfRound + " 回合）！交换攻防！");
        broadcastLegacy("§e金钱与装备已重置。§7 比分 §cT " + match.getScoreT()
                + " §7- §b" + match.getScoreCT() + " CT");
        refreshUi();
    }

    // =====================================================================
    // 购买阶段
    // =====================================================================

    private void startBuyPhase() {
        cancel();
        plugin.getBombManager().reset();
        ArenaCleanup.clearDrops();

        state = RoundState.BUY;
        secondsLeft = plugin.getConfig().getInt("round.prepare-time", 15);

        broadcastLegacy("§e[DFS] 第 §f" + match.getCurrentRound()
                + " §e回合 · 购买阶段 §f" + secondsLeft + "s");

        ItemGiveService give = plugin.getItemGiveService();
        ItemManager items = plugin.getItemManager();

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || !s.hasTeam()) {
                continue;
            }

            s.setAlive(true);
            s.setConnected(true);
            forceExitSpectator(p);

            p.setInvulnerable(false);
            p.setFallDistance(0f);
            plugin.getGameRulesService().fillHealth(p);
            plugin.getGameRulesService().fillFood(p);
            plugin.getMatchManager().teleportTeamSpawn(p, s.getTeam());

            clearPlantBombOnly(p, items);
            ensureBaselineLoadoutNoBomb(p, s, give, items);
            p.sendMessage("§6资金: §e$" + s.getMoney() + " §7| §a/dfs shop");
        }

        assignBombCarrier(give, items);
        refreshUi();

        // 防旁观锁抢回
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
            }
        });

        // 自动商店
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
        }, 5L);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != RoundState.BUY) {
                cancel();
                return;
            }

            for (Player p : match.onlinePlayers()) {
                PlayerSession s = match.getSession(p.getUniqueId());
                if (s != null && s.hasTeam() && s.isAlive()
                        && p.getGameMode() == GameMode.SPECTATOR) {
                    forceExitSpectator(p);
                }
            }

            // ActionBar：禁止 Component.text("§...")
            actionBarLegacy("§e购买阶段 §f" + secondsLeft + "s §7| §a/dfs shop");

            if (secondsLeft <= 0) {
                startCombatPhase();
                return;
            }
            if (secondsLeft <= 5) {
                broadcastLegacy("§e购买阶段剩余 §c" + secondsLeft + "s");
            }
            refreshUi();
            secondsLeft--;
        }, 0L, 20L);
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

        if (ConfigKeys.shieldEnabled()) {
            ItemStack off = inv.getItemInOffHand();
            if (off == null || off.getType().isAir() || !items.isShield(off)) {
                if (!give.give(p, "shield", true)) {
                    give.give(p, "equipments.shield", true);
                }
            }
        } else {
            ItemStack off = inv.getItemInOffHand();
            if (off != null && (items.isShield(off) || off.getType() == Material.SHIELD)) {
                inv.setItemInOffHand(null);
            }
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

        boolean ok = give.give(carrier, "plant-bomb", true);
        if (!ok) {
            ok = give.give(carrier, "bomb.plant-bomb", true);
        }

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
                    org.starset.deltaforcestrike.item.ItemKeys.action(),
                    org.bukkit.persistence.PersistentDataType.STRING
            );
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

    // =====================================================================
    // 战斗 / 拆弹
    // =====================================================================

    private void startCombatPhase() {
        cancel();
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

        // 只同步 UI，不自行倒数爆炸
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

    // =====================================================================
    // 回合结束
    // =====================================================================

    public void endRound(Team winner, String reason) {
        if (state == RoundState.ROUND_END || state == RoundState.IDLE) {
            return;
        }
        cancel();
        state = RoundState.ROUND_END;

        plugin.getBombManager().reset();
        ArenaCleanup.clearDrops();

        match.addScore(winner);
        applyEconomy(winner);

        broadcastLegacy("§6[DFS] 回合结束: §f" + (reason == null ? "" : reason));
        broadcastLegacy("§7比分 §cT " + match.getScoreT() + " §7- §b" + match.getScoreCT() + " CT");

        showRoundResultTitles(winner, reason);
        refreshUi();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ArenaCleanup.clearDrops();
            startNextRound();
        }, 5 * 20L);
    }

    private void showRoundResultTitles(Team winner, String reason) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(150),
                Duration.ofSeconds(3),
                Duration.ofMillis(500)
        );

        Component winMain = Component.text("回合胜利", NamedTextColor.GREEN, TextDecoration.BOLD);
        Component loseMain = Component.text("回合败北", NamedTextColor.RED, TextDecoration.BOLD);
        Component reasonComp = Component.text(reason == null ? "" : reason, NamedTextColor.GRAY);
        Component scoreComp = Component.text(
                "比分 T " + match.getScoreT() + " - " + match.getScoreCT() + " CT",
                NamedTextColor.YELLOW
        );
        Component subtitle = reasonComp
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(scoreComp);

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || !s.hasTeam()) {
                p.showTitle(Title.title(
                        Component.text("回合结束", NamedTextColor.GOLD, TextDecoration.BOLD),
                        scoreComp,
                        times
                ));
                continue;
            }
            if (s.getTeam() == winner) {
                p.showTitle(Title.title(winMain, subtitle, times));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            } else {
                p.showTitle(Title.title(loseMain, subtitle, times));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            }
        }
    }

    private void applyEconomy(Team winner) {
        int winMoney = plugin.getConfig().getInt("economy.victory-bonus", 3200);
        List<Integer> defeat = plugin.getConfig().getIntegerList("economy.defeat-bonus");
        if (defeat == null || defeat.isEmpty()) {
            defeat = List.of(1600, 2000, 2400);
        }
        int pistol = plugin.getConfig().getInt("economy.pistol-round-bonus", 2400);

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
            msg = "§a进攻方 T 获胜！§c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        } else if (match.getScoreCT() > match.getScoreT()) {
            msg = "§a防守方 CT 获胜！§c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        } else {
            msg = "§e平局！§c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        }
        plugin.getMatchManager().forceEnd(stripForForceEnd(msg));
    }

    /** forceEnd 若内部再用 § 广播，保持字符串 API 即可 */
    private String stripForForceEnd(String legacy) {
        // MatchManager.forceEnd 使用 sendMessage(String)，可带 §
        return legacy;
    }

    // =====================================================================
    // 消息工具（避免 LegacyFormattingDetected）
    // =====================================================================

    /**
     * ActionBar：§ 字符串必须 deserialize，禁止 Component.text("§...")
     */
    private void actionBarLegacy(String legacyMsg) {
        Component c = LEGACY.deserialize(legacyMsg == null ? "" : legacyMsg);
        for (Player p : match.onlinePlayers()) {
            p.sendActionBar(c);
        }
    }

    /**
     * 聊天广播：Bukkit 字符串 API 支持 §，不会触发 Adventure 警告。
     */
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
