package org.starset.deltaforcestrike.round;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemGiveService;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.shop.ShopGUI;
import org.starset.deltaforcestrike.util.ArenaCleanup;
import org.starset.deltaforcestrike.util.InventorySlots;

import java.time.Duration;
import java.util.List;

public class RoundManager {

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

    public void startNextRound() {
        int winTarget = plugin.getConfig().getInt("match.win-target", 5);
        int maxRounds = plugin.getConfig().getInt("match.max-rounds", 8);
        int halfRound = plugin.getConfig().getInt("match.half-round", 4);

        if (match.getScoreT() >= winTarget || match.getScoreCT() >= winTarget
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
            p.getInventory().clear();
            p.getInventory().setHelmet(null);
            p.getInventory().setChestplate(null);
            p.getInventory().setLeggings(null);
            p.getInventory().setBoots(null);
            p.getInventory().setItemInOffHand(null);
            p.setGameMode(GameMode.ADVENTURE);
        }

        match.broadcast("§6§l[DFS] 半场结束（" + halfRound + " 回合）！交换攻防！");
        match.broadcast("§e金钱与装备已重置。§7 比分 §cT " + match.getScoreT()
                + " §7- §b" + match.getScoreCT() + " CT");
        refreshUi();
    }

    private void startBuyPhase() {
        cancel();
        plugin.getBombManager().reset();
        ArenaCleanup.clearDrops();

        state = RoundState.BUY;
        secondsLeft = plugin.getConfig().getInt("round.prepare-time", 15);

        match.broadcast("§e[DFS] 第 §f" + match.getCurrentRound()
                + " §e回合 · 购买阶段 §f" + secondsLeft + "s");

        ItemGiveService give = plugin.getItemGiveService();
        ItemManager items = plugin.getItemManager();

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || !s.hasTeam()) {
                continue;
            }

            p.setGameMode(GameMode.ADVENTURE);
            p.setInvulnerable(false);
            p.setFallDistance(0f);
            plugin.getGameRulesService().fillHealth(p);
            plugin.getGameRulesService().fillFood(p);
            plugin.getMatchManager().teleportTeamSpawn(p, s.getTeam());

            ensureBaselineLoadout(p, s, give, items);
            p.sendMessage("§6资金: §e$" + s.getMoney() + " §7| §a/dfs shop");
        }

        refreshUi();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != RoundState.BUY) {
                return;
            }
            for (Player p : match.onlinePlayers()) {
                PlayerSession s = match.getSession(p.getUniqueId());
                if (s != null && s.hasTeam() && s.isAlive()) {
                    ShopGUI.open(p);
                }
            }
        }, 5L);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != RoundState.BUY) {
                cancel();
                return;
            }
            actionBarAll("§e购买阶段 §f" + secondsLeft + "s §7| §a/dfs shop");
            if (secondsLeft <= 0) {
                startCombatPhase();
                return;
            }
            if (secondsLeft <= 5) {
                match.broadcast("§e购买阶段剩余 §c" + secondsLeft + "s");
            }
            refreshUi();
            secondsLeft--;
        }, 0L, 20L);
    }

    /**
     * 木剑 + T 包；不发盾。
     * 有弓弩：箭补满到 15。
     */
    private void ensureBaselineLoadout(Player p, PlayerSession s,
                                       ItemGiveService give, ItemManager items) {
        PlayerInventory inv = p.getInventory();

        ItemStack melee = inv.getItem(InventorySlots.MELEE);
        if (melee == null || melee.getType().isAir()) {
            give.give(p, "standard", true);
        }

        if (s.getTeam() == Team.T) {
            ItemStack bomb = inv.getItem(InventorySlots.BOMB);
            boolean has = bomb != null && !bomb.getType().isAir() && isBombItem(items, bomb);
            if (!has) {
                if (!give.give(p, "plant-bomb", true)) {
                    give.give(p, "bomb.plant-bomb", true);
                }
            }
        } else {
            // CT：移除改造 TNT
            ItemStack bomb = inv.getItem(InventorySlots.BOMB);
            if (bomb != null && isBombItem(items, bomb)) {
                inv.setItem(InventorySlots.BOMB, null);
            }
        }

        // 有远程武器：弹药回满 15
        if (give.hasRangedWeapon(p)) {
            int target = plugin.getConfig().getInt("shop.arrows-per-ranged",
                    ItemGiveService.ARROWS_WITH_RANGED);
            give.refillArrowsTo(p, target);
        }
    }

    private boolean isBombItem(ItemManager items, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if ("bomb".equalsIgnoreCase(items.getItemType(stack))) {
            return true;
        }
        String id = items.getItemId(stack);
        return id != null && id.contains("plant-bomb");
    }

    private void startCombatPhase() {
        cancel();
        for (Player p : match.onlinePlayers()) {
            p.closeInventory();
            p.setGameMode(GameMode.ADVENTURE);
        }

        state = RoundState.COMBAT;
        secondsLeft = plugin.getConfig().getInt("round.combat-time", 100);
        match.broadcast("§c§l[DFS] 战斗开始！");
        refreshUi();

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != RoundState.COMBAT) {
                cancel();
                return;
            }
            actionBarAll("§c⏱ 进攻时间 §f" + formatTime(secondsLeft));
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
        match.broadcast("§c[DFS] 拆弹阶段！");
        refreshUi();

        // 安包瞬间若 CT 已全灭，直接 T 胜
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
                actionBarAll("§4💣 爆炸倒计时 §c" + fuse + "s");
            }
            refreshUi();
        }, 0L, 10L);
    }

    /**
     * 歼灭判定：
     * - CT 全灭 → 始终 T 胜（含已安包）
     * - 未安包 T 全灭 → CT 胜
     * - 已安包 T 全灭 → 不结束，等拆/炸
     */
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

        match.addScore(winner);
        applyEconomy(winner);

        match.broadcast("§6[DFS] 回合结束: §f" + reason);
        match.broadcast("§7比分 §cT " + match.getScoreT() + " §7- §b" + match.getScoreCT() + " CT");

        // 胜负标题
        showRoundResultTitles(winner, reason);

        refreshUi();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ArenaCleanup.clearDrops();
            startNextRound();
        }, 5 * 20L);
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
            msg = "进攻方 T 获胜！§c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        } else if (match.getScoreCT() > match.getScoreT()) {
            msg = "防守方 CT 获胜！§c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        } else {
            msg = "平局！§c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        }
        plugin.getMatchManager().forceEnd(msg);
    }

    private void actionBarAll(String msg) {
        Component c = Component.text(msg);
        for (Player p : match.onlinePlayers()) {
            p.sendActionBar(c);
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

    private void showRoundResultTitles(Team winner, String reason) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(150),
                Duration.ofSeconds(3),
                Duration.ofMillis(500)
        );

        Component winMain = Component.text("回合胜利", NamedTextColor.GREEN, TextDecoration.BOLD);
        Component loseMain = Component.text("回合败北", NamedTextColor.RED, TextDecoration.BOLD);
        Component sub = Component.text(reason == null ? "" : reason, NamedTextColor.GRAY);
        Component score = Component.text(
                "比分 T " + match.getScoreT() + " - " + match.getScoreCT() + " CT",
                NamedTextColor.YELLOW
        );

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || !s.hasTeam()) {
                // 观战/无队伍：只显示结算
                p.showTitle(Title.title(
                        Component.text("回合结束", NamedTextColor.GOLD, TextDecoration.BOLD),
                        score,
                        times
                ));
                continue;
            }

            boolean won = s.getTeam() == winner;
            Component subtitle = sub.append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(score);

            if (won) {
                p.showTitle(Title.title(winMain, subtitle, times));
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            } else {
                p.showTitle(Title.title(loseMain, subtitle, times));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            }
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
