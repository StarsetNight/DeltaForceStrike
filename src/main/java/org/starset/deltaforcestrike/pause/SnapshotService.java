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
import org.starset.deltaforcestrike.round.RoundState;

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
 * org.starset.deltaforcestrike.round.RoundManager#restartBuyFromSnapshot()} 实现。</p>
 */
public final class SnapshotService {

    private final DeltaForceStrike plugin;
    private RoundSnapshot snapshot;

    public SnapshotService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    /** 购买阶段开始后调用：固化本回合快照 */
    public void captureAtBuyStart() {
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) return;
        if (match.getRoundManager().getState() != RoundState.BUY) return;
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
    }

    public RoundSnapshot capture(Match match) {
        RoundSnapshot snap = new RoundSnapshot();
        snap.round = match.getCurrentRound();
        snap.scoreT = match.getScoreT();
        snap.scoreCT = match.getScoreCT();
        snap.halfSwapped = match.getRoundManager().isHalfTimeSwapped();
        snap.buySeconds = plugin.getConfig().getInt("round.prepare-time", 20);

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
            try {
                var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                e.maxHealth = attr != null ? attr.getValue() : 20.0;
            } catch (Throwable t) {
                e.maxHealth = 20.0;
            }
            e.food = p.getFoodLevel();
            e.saturation = p.getSaturation();
            e.gamemode = p.getGameMode();
            e.potionEffects = p.getActivePotionEffects().toArray(new PotionEffect[0]);

            PlayerInventory inv = p.getInventory();
            e.contents = inv.getContents().clone();
            e.armor = inv.getArmorContents().clone();
            e.offHand = inv.getItemInOffHand().clone();
            e.heldSlot = inv.getHeldItemSlot();

            Location l = p.getLocation();
            e.world = l.getWorld() == null ? null : l.getWorld().getName();
            e.x = l.getX();
            e.y = l.getY();
            e.z = l.getZ();
            e.yaw = l.getYaw();
            e.pitch = l.getPitch();

            e.invulnerable = p.isInvulnerable();
            e.flying = p.isFlying();
            e.allowFlight = p.getAllowFlight();

            snap.players.put(e.uuid, e);
        }
        return snap;
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
        if (match.getRoundManager().getState() == RoundState.BOMB_PLANTED) {
            if (admin != null) admin.sendMessage("§c[DFS] 拆弹阶段不能回滚。");
            return false;
        }
        match.broadcast("§6[DFS] §e管理员回滚回合到购买阶段快照 §7(R" + snapshot.round + ")");
        match.getRoundManager().restartBuyFromSnapshot();
        return true;
    }

    /** 关闭：进入战斗后建议清理（但有快照允许战斗中也回滚到购买阶段） */
    public void onDisable() {
        snapshot = null;
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
        public double maxHealth;
        public int food;
        public float saturation;
        public org.bukkit.GameMode gamemode;
        public PotionEffect[] potionEffects;

        public ItemStack[] contents;
        public ItemStack[] armor;
        public ItemStack offHand;
        public int heldSlot;

        public String world;
        public double x, y, z;
        public float yaw, pitch;

        public boolean invulnerable;
        public boolean flying;
        public boolean allowFlight;
    }
}
