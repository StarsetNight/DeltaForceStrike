package org.starset.deltaforcestrike.round;

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
import org.starset.deltaforcestrike.util.ArenaCleanup;
import org.starset.deltaforcestrike.util.InventorySlots;

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

    public void startNextRound() {
        int winTarget = plugin.getConfig().getInt("match.win-target", 5);
        int maxRounds = plugin.getConfig().getInt("match.max-rounds", 8);
        int halfRound = plugin.getConfig().getInt("match.half-round", 4);

        if (match.getScoreT() >= winTarget || match.getScoreCT() >= winTarget
                || match.getCurrentRound() >= maxRounds) {
            endMatchByScore();
            return;
        }

        // 半场换边：刚打完 half-round 局（currentRound == halfRound）后、开下一局前
        // 例如 half=4：第 4 回合结束后 currentRound 仍是 4，下一回合前换边
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

    /**
     * 半场换边：玩家 T↔CT，比分对调（分仍挂在同一批人身上）。
     */
    private void doHalfTimeSwap(int halfRound) {
        halfTimeSwapped = true;

        for (PlayerSession s : match.getSessions().values()) {
            if (s.getTeam() == Team.T) {
                s.setTeam(Team.CT);
            } else if (s.getTeam() == Team.CT) {
                s.setTeam(Team.T);
            }
        }

        // 比分随边显示：换边后对调
        match.swapScores();

        // 换边后 T 需要包，CT 不应持有包：简单处理 — 清所有人 bomb 槽，稍后 BUY 给新 T 发
        for (Player p : match.onlinePlayers()) {
            ItemStack bombSlot = p.getInventory().getItem(InventorySlots.BOMB);
            if (bombSlot != null) {
                p.getInventory().setItem(InventorySlots.BOMB, null);
            }
        }

        match.broadcast("§6§l[DFS] 半场结束（" + halfRound + " 回合）！双方交换攻防！");
        match.broadcast("§7比分 §cT " + match.getScoreT() + " §7- §b" + match.getScoreCT() + " CT §8(已换边)");
        plugin.getScoreboardService().updateAll(match);
    }

    private void startBuyPhase() {
        cancel();
        state = RoundState.BUY;
        secondsLeft = plugin.getConfig().getInt("round.prepare-time", 15);

        // 回合开始：清掉落（含上一回合残留）
        ArenaCleanup.clearDrops();

        match.broadcast("§e[DFS] 第 §f" + match.getCurrentRound()
                + " §e回合 · 购买阶段 §f" + secondsLeft + "s");

        ItemGiveService give = plugin.getItemGiveService();
        ItemManager items = plugin.getItemManager();

        for (Player p : match.onlinePlayers()) {
            PlayerSession s = match.getSession(p.getUniqueId());
            if (s == null || !s.hasTeam()) continue;

            p.setGameMode(GameMode.SURVIVAL);
            p.setInvulnerable(false);
            p.setFallDistance(0f);
            plugin.getGameRulesService().fillHealth(p);
            plugin.getGameRulesService().fillFood(p);
            plugin.getMatchManager().teleportTeamSpawn(p, s.getTeam());

            // ===== 保留装备：不再 inventory.clear() =====
            ensureBaselineLoadout(p, s, give, items);

            p.sendMessage("§6[DFS] 资金: §e$" + s.getMoney() + " §7(装备已保留)");
        }

        plugin.getScoreboardService().updateAll(match);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != RoundState.BUY) {
                cancel();
                return;
            }
            if (secondsLeft <= 0) {
                startCombatPhase();
                return;
            }
            if (secondsLeft <= 5) {
                match.broadcast("§e购买阶段剩余 §c" + secondsLeft + "s");
            }
            // 每秒刷计分板（金钱等）
            if (secondsLeft % 2 == 0) {
                plugin.getScoreboardService().updateAll(match);
            }
            secondsLeft--;
        }, 20L, 20L);
    }

    /**
     * 只补「没有的基础件」，不删已有枪械/道具/护甲。
     */
    private void ensureBaselineLoadout(Player p, PlayerSession s,
                                       ItemGiveService give, ItemManager items) {
        PlayerInventory inv = p.getInventory();

        // 近战空则木剑
        ItemStack melee = inv.getItem(InventorySlots.MELEE);
        if (melee == null || melee.getType().isAir()) {
            give.give(p, "standard", true);
        }

        // 盾：副手没有盾则补
        ItemStack off = inv.getItemInOffHand();
        if (!items.isShield(off)) {
            give.give(p, "shield", true);
        }

        // 改造 TNT：仅 T；CT 若还拿着则清掉
        if (s.getTeam() == Team.T) {
            ItemStack bomb = inv.getItem(InventorySlots.BOMB);
            boolean hasBomb = bomb != null && !bomb.getType().isAir()
                    && "bomb".equalsIgnoreCase(items.getItemType(bomb));
            if (!hasBomb) {
                if (!give.give(p, "plant-bomb", true)) {
                    give.give(p, "bomb.plant-bomb", true);
                }
            }
        } else {
            ItemStack bomb = inv.getItem(InventorySlots.BOMB);
            if (bomb != null && !bomb.getType().isAir()) {
                String type = items.getItemType(bomb);
                String id = items.getItemId(bomb);
                if ("bomb".equalsIgnoreCase(type)
                        || (id != null && id.contains("plant"))) {
                    inv.setItem(InventorySlots.BOMB, null);
                }
            }
        }
    }

    private void startCombatPhase() {
        cancel();
        state = RoundState.COMBAT;
        secondsLeft = plugin.getConfig().getInt("round.combat-time", 100);
        match.broadcast("§c§l[DFS] 战斗开始！");
        plugin.getScoreboardService().updateAll(match);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != RoundState.COMBAT) {
                cancel();
                return;
            }
            if (secondsLeft <= 0) {
                endRound(Team.CT, "时间耗尽，防守方胜利");
                return;
            }
            if (secondsLeft % 5 == 0) {
                plugin.getScoreboardService().updateAll(match);
            }
            secondsLeft--;
        }, 20L, 20L);
    }

    public void checkWipe() {
        if (state != RoundState.COMBAT) return;
        if (match.allDead(Team.T)) {
            endRound(Team.CT, "进攻方全灭");
        } else if (match.allDead(Team.CT)) {
            endRound(Team.T, "防守方全灭");
        }
    }

    public void endRound(Team winner, String reason) {
        if (state == RoundState.ROUND_END || state == RoundState.IDLE) return;
        cancel();
        state = RoundState.ROUND_END;

        // 回合结束立刻清掉落
        ArenaCleanup.clearDrops();

        match.addScore(winner);
        applyEconomy(winner);

        match.broadcast("§6[DFS] 回合结束: §f" + reason);
        match.broadcast("§7比分 §cT " + match.getScoreT() + " §7- §b" + match.getScoreCT() + " CT");

        plugin.getScoreboardService().updateAll(match);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ArenaCleanup.clearDrops(); // 结算等待期再清一次
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
            if (!s.hasTeam()) continue;
            if (s.getTeam() == winner) {
                s.addMoney(winMoney);
                s.setConsecutiveLosses(0);
            } else {
                int losses = s.getConsecutiveLosses() + 1;
                s.setConsecutiveLosses(losses);
                if (match.getCurrentRound() == 1) {
                    s.addMoney(pistol);
                } else {
                    int idx = Math.min(losses - 1, defeat.size() - 1);
                    s.addMoney(defeat.get(idx));
                }
            }
        }
    }

    private void endMatchByScore() {
        cancel();
        state = RoundState.IDLE;
        ArenaCleanup.clearDrops();

        String msg;
        if (match.getScoreT() > match.getScoreCT()) {
            msg = "进攻方 T 获胜！最终 §c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        } else if (match.getScoreCT() > match.getScoreT()) {
            msg = "防守方 CT 获胜！最终 §c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        } else {
            msg = "平局！§c" + match.getScoreT() + "§7-§b" + match.getScoreCT();
        }
        plugin.getMatchManager().forceEnd(msg);
    }

    public void shutdown() {
        cancel();
        state = RoundState.IDLE;
    }

    private void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
