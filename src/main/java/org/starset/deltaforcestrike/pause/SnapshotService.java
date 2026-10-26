package org.starset.deltaforcestrike.pause;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.match.RoundState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每回合购买阶段开始时拍照一次，管理员可 /dfs restore 将回合回滚到该快照。
 *
 * <p>快照内容：当前回合号、比分、半场标记、全员金钱/连败、库存/血量/食物/位置/朝向、
 * 干员 ID、存活标记、队伍、炸弹携带者、干员充能（通过 onRoundStart 之后状态）。
 * 恢复后重新执行购买阶段开局（teleport 到出生点、ejects spectator），金钱/库存还原，
 * 不重新发干员技能（保留快照时的充能）：通过 §3 调用 {@link
 * org.starset.deltaforcestrike.match.RoundManager#restartBuyFromSnapshot()} 实现。</p>
 */
public final class SnapshotService {

    private final DeltaForceStrike plugin;
    private RoundSnapshot snapshot;
    /** 回滚时离线玩家的库存快照，重连购买阶段时恢复 */
    private final Map<UUID, PlayerEntry> pendingRestores = new LinkedHashMap<>();

    public SnapshotService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    /** 购买阶段开始后调用：固化本回合快照 */
    public void captureAtBuyStart() {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) return;
        if (match.getRoundManager().getState() != RoundState.BUY) return;
        pendingRestores.clear();
        snapshot = capture(match);
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Snapshot] 已拍摄 R" + match.getCurrentRound()
                    + " 买阶段快照（" + snapshot.players.size() + " 人）");
        }
    }

    public boolean hasSnapshot() {
        return snapshot != null;
    }

    public RoundSnapshot getSnapshot() {
        return snapshot;
    }

    public void clear() {
        snapshot = null;
        pendingRestores.clear();
    }

    public RoundSnapshot capture(Match match) {
        RoundSnapshot snap = new RoundSnapshot();
        snap.round = match.getCurrentRound();
        snap.scoreT = match.getScoreT();
        snap.scoreCT = match.getScoreCT();
        snap.halfSwapped = match.getRoundManager().isHalfTimeSwapped();
        // 记录实际剩余购买秒数，回滚后沿用（而非总是满额重置）
        snap.buySeconds = Math.max(1, match.getRoundManager().getSecondsLeft());

        for (PlayerSession s : match.getSessions().values()) {
            if (!s.isPlaying()) continue;
            Player p = Bukkit.getPlayer(s.getUuid());
            if (p == null) continue;
            PlayerEntry e = new PlayerEntry();
            e.uuid = s.getUuid();
            e.name = s.getName();
            e.team = s.getTeam();
            e.money = s.getMoney();
            e.consecutiveLosses = s.getConsecutiveLosses();
            e.alive = s.isAlive();
            e.connected = s.isConnected();
            e.operatorId = s.getOperatorId();
            e.deathCounted = s.isDeathCountedMark();
            e.kills = s.getKills();
            e.deaths = s.getDeaths();

            e.health = p.getHealth();
            e.food = p.getFoodLevel();
            e.saturation = p.getSaturation();
            e.potionEffects = p.getActivePotionEffects().toArray(new PotionEffect[0]);

            PlayerInventory inv = p.getInventory();
            // 逐元素克隆：getContents() 返回的是活镜像，只克隆数组挡不住原槽位的原地修改
            e.contents = deepCopy(inv.getContents());
            e.armor = deepCopy(inv.getArmorContents());
            e.offHand = inv.getItemInOffHand().clone();
            e.heldSlot = inv.getHeldItemSlot();

            Location l = p.getLocation();
            e.world = l.getWorld() == null ? null : l.getWorld().getName();
            e.x = l.getX();
            e.y = l.getY();
            e.z = l.getZ();
            e.yaw = l.getYaw();
            e.pitch = l.getPitch();

            snap.players.put(e.uuid, e);
        }
        return snap;
    }

    private static ItemStack[] deepCopy(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }

    /** 管理员命令：回滚到本回合购买阶段快照。admin 可为 null（console 调用） */
    public boolean restore(Player admin) {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            if (admin != null) admin.sendMessage("§c[DFS] 当前不在对局中。");
            return false;
        }
        if (snapshot == null) {
            if (admin != null) admin.sendMessage("§c[DFS] 没有本回合的购买阶段快照。");
            return false;
        }
        // 只允许在购买阶段回滚，且快照必须是本回合的，
        // 否则结算窗口内回滚会把比分/金钱与已记账的回合错位
        if (match.getRoundManager().getState() != RoundState.BUY) {
            if (admin != null) admin.sendMessage("§c[DFS] 只能在购买阶段回滚。");
            return false;
        }
        if (snapshot.round != match.getCurrentRound()) {
            if (admin != null) admin.sendMessage("§c[DFS] 快照不是本回合的，无法回滚。");
            return false;
        }
        match.broadcast("§6[DFS] §e管理员回滚回合到购买阶段快照 §7(R" + snapshot.round + ")");
        match.getRoundManager().restartBuyFromSnapshot();
        return true;
    }

    /** 回滚时离线玩家的库存快照入队，重连购买阶段时恢复 */
    public void stashPendingRestore(UUID uuid, PlayerEntry entry) {
        if (uuid != null && entry != null && entry.contents != null) {
            pendingRestores.put(uuid, entry);
        }
    }

    /** 重连归队（购买阶段）时恢复回滚前快照的库存 */
    public void applyPendingRestore(Player p) {
        if (p == null) return;
        PlayerEntry e = pendingRestores.remove(p.getUniqueId());
        if (e == null) return;
        if (e.contents != null) {
            p.getInventory().setContents(e.contents);
        }
        if (e.armor != null) {
            p.getInventory().setArmorContents(e.armor);
        }
        p.getInventory().setItemInOffHand(e.offHand);
        try {
            p.getInventory().setHeldItemSlot(e.heldSlot);
        } catch (Throwable ignored) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "Pending snapshot held slot restore failed for " + p.getUniqueId(), ignored);
        }
        p.updateInventory();
        p.sendMessage("§6[DFS] 已恢复回滚前快照的库存。");
    }

    /** 关闭：进入战斗后建议清理（但有快照允许战斗中也回滚到购买阶段） */
    public void onDisable() {
        snapshot = null;
        pendingRestores.clear();
    }

    // ------------------------------------------------------------------

    public static final class RoundSnapshot {
        public int round;
        public int scoreT;
        public int scoreCT;
        public boolean halfSwapped;
        public int buySeconds;
        public final Map<UUID, PlayerEntry> players = new LinkedHashMap<>();
    }

    public static final class PlayerEntry {
        public UUID uuid;
        public String name;
        public Team team;
        public int money;
        public int consecutiveLosses;
        public boolean alive;
        public boolean connected;
        public String operatorId;
        public boolean deathCounted;
        public int kills;
        public int deaths;

        public double health;
        public int food;
        public float saturation;
        public PotionEffect[] potionEffects;

        public ItemStack[] contents;
        public ItemStack[] armor;
        public ItemStack offHand;
        public int heldSlot;

        public String world;
        public double x, y, z;
        public float yaw, pitch;
    }
}
