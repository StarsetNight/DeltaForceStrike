package org.starset.deltaforcestrike.live;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.BombSites;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主线程构建导播快照 JSON，供 HTTP 线程只读。
 */
public final class LiveSnapshotService {

    public static final int PROTOCOL = 1;

    private final DeltaForceStrike plugin;
    private final AtomicReference<String> cachedJson = new AtomicReference<>("{}");

    public LiveSnapshotService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public String getJson() {
        return cachedJson.get();
    }

    /** 应在主线程调用 */
    public void tick() {
        try {
            cachedJson.set(buildJson());
        } catch (Throwable t) {
            plugin.getLogger().warning("[Live] 快照构建失败: " + t.getMessage());
        }
    }

    private String buildJson() {
        Map<String, String> root = new LinkedHashMap<>();
        root.put("protocol", LiveJson.num(PROTOCOL));
        root.put("serverTime", LiveJson.num(System.currentTimeMillis()));
        root.put("map", buildMap());
        root.put("match", buildMatch());
        root.put("players", buildPlayers());
        root.put("kills", buildKills());
        return LiveJson.obj(root);
    }

    private String buildKills() {
        List<String> list = new ArrayList<>();
        LiveKillFeedService feed = plugin.getLiveKillFeedService();
        if (feed == null) {
            return LiveJson.arr(list);
        }
        for (LiveKillFeedService.Entry e : feed.snapshot()) {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("id", LiveJson.num(e.id()));
            o.put("time", LiveJson.num(e.time()));
            o.put("type", LiveJson.str(e.type()));
            o.put("killer", LiveJson.str(e.killer()));
            o.put("weapon", LiveJson.str(e.weapon()));
            o.put("victim", LiveJson.str(e.victim()));
            o.put("killerTeam", LiveJson.str(e.killerTeam()));
            o.put("victimTeam", LiveJson.str(e.victimTeam()));
            list.add(LiveJson.obj(o));
        }
        return LiveJson.arr(list);
    }

    private String buildMap() {
        Map<String, String> m = new LinkedHashMap<>();
        String name = plugin.getConfig().getString("live.map.name", Worlds.arenaName());
        m.put("name", LiveJson.str(name == null ? Worlds.arenaName() : name));
        m.put("world", LiveJson.str(Worlds.arenaName()));

        double minX = plugin.getConfig().getDouble("live.map.min-x", -200);
        double minZ = plugin.getConfig().getDouble("live.map.min-z", -200);
        double maxX = plugin.getConfig().getDouble("live.map.max-x", 200);
        double maxZ = plugin.getConfig().getDouble("live.map.max-z", 200);
        // 保证 min < max
        if (minX > maxX) {
            double t = minX;
            minX = maxX;
            maxX = t;
        }
        if (minZ > maxZ) {
            double t = minZ;
            minZ = maxZ;
            maxZ = t;
        }
        m.put("minX", LiveJson.num(minX));
        m.put("minZ", LiveJson.num(minZ));
        m.put("maxX", LiveJson.num(maxX));
        m.put("maxZ", LiveJson.num(maxZ));

        List<String> sites = new ArrayList<>();
        for (BombSites.Site site : BombSites.load()) {
            Map<String, String> s = new LinkedHashMap<>();
            s.put("id", LiveJson.str(site.id()));
            Location c = site.center();
            s.put("x", LiveJson.num(c.getX()));
            s.put("y", LiveJson.num(c.getY()));
            s.put("z", LiveJson.num(c.getZ()));
            s.put("radius", LiveJson.num(site.radius()));
            sites.add(LiveJson.obj(s));
        }
        m.put("sites", LiveJson.arr(sites));
        return LiveJson.obj(m);
    }

    private String buildMatch() {
        Map<String, String> m = new LinkedHashMap<>();
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            m.put("active", LiveJson.bool(false));
            m.put("state", LiveJson.str("NONE"));
            m.put("roundState", LiveJson.str("IDLE"));
            m.put("scoreT", LiveJson.num(0));
            m.put("scoreCT", LiveJson.num(0));
            m.put("round", LiveJson.num(0));
            m.put("secondsLeft", LiveJson.num(0));
            m.put("bombPlanted", LiveJson.bool(false));
            m.put("fuseLeft", LiveJson.num(-1));
            return LiveJson.obj(m);
        }

        MatchState ms = match.getState();
        RoundState rs = match.getRoundManager().getState();
        int secondsLeft = match.getRoundManager().getSecondsLeft();
        boolean bombPlanted = plugin.getBombManager() != null && plugin.getBombManager().isPlanted();
        int fuseLeft = plugin.getBombManager() != null ? plugin.getBombManager().getFuseLeft() : -1;
        if (bombPlanted && fuseLeft >= 0) {
            secondsLeft = fuseLeft;
        }

        m.put("active", LiveJson.bool(true));
        m.put("id", LiveJson.str(match.getMatchId().toString()));
        m.put("state", LiveJson.str(ms.name()));
        m.put("roundState", LiveJson.str(rs.name()));
        m.put("scoreT", LiveJson.num(match.getScoreT()));
        m.put("scoreCT", LiveJson.num(match.getScoreCT()));
        m.put("round", LiveJson.num(match.getCurrentRound()));
        m.put("secondsLeft", LiveJson.num(Math.max(0, secondsLeft)));
        m.put("winTarget", LiveJson.num(plugin.getConfig().getInt("match.win-target", 13)));
        m.put("halfRound", LiveJson.num(plugin.getConfig().getInt("match.half-round", 12)));
        m.put("halfSwapped", LiveJson.bool(match.getRoundManager().isHalfTimeSwapped()));
        m.put("maxPlayers", LiveJson.num(match.size()));
        m.put("bombPlanted", LiveJson.bool(bombPlanted));
        m.put("fuseLeft", LiveJson.num(fuseLeft));

        Team last = match.getLastRoundWinner();
        m.put("lastRoundWinner", LiveJson.str(
                last == null || last == Team.NONE ? "" : last.name()));

        if (plugin.getBombManager() != null && bombPlanted) {
            Location plant = plugin.getBombManager().getPlantLocation();
            if (plant != null) {
                Map<String, String> bp = new LinkedHashMap<>();
                bp.put("x", LiveJson.num(plant.getX()));
                bp.put("y", LiveJson.num(plant.getY()));
                bp.put("z", LiveJson.num(plant.getZ()));
                m.put("bombPos", LiveJson.obj(bp));
            } else {
                m.put("bombPos", LiveJson.nul());
            }
        } else {
            m.put("bombPos", LiveJson.nul());
        }

        return LiveJson.obj(m);
    }

    private String buildPlayers() {
        Match match = plugin.getMatchManager().getMatch();
        List<String> list = new ArrayList<>();
        if (match == null) {
            return LiveJson.arr(list);
        }

        ItemManager items = plugin.getItemManager();
        // 每队编号：按进 session 顺序 1..n（稳定，与雷达一致）
        int numT = 0;
        int numCT = 0;
        Map<UUID, Integer> numbers = new LinkedHashMap<>();
        for (PlayerSession s : match.getSessions().values()) {
            if (s.getTeam() == Team.T) {
                numbers.put(s.getUuid(), ++numT);
            } else if (s.getTeam() == Team.CT) {
                numbers.put(s.getUuid(), ++numCT);
            } else {
                numbers.put(s.getUuid(), 0);
            }
        }
        for (PlayerSession s : match.getSessions().values()) {
            list.add(buildPlayer(match, s, items, numbers.getOrDefault(s.getUuid(), 0)));
        }
        return LiveJson.arr(list);
    }

    private String buildPlayer(Match match, PlayerSession s, ItemManager items, int number) {
        Map<String, String> p = new LinkedHashMap<>();
        UUID uuid = s.getUuid();
        Player online = Bukkit.getPlayer(uuid);

        p.put("uuid", LiveJson.str(uuid.toString()));
        p.put("name", LiveJson.str(s.getName()));
        p.put("number", LiveJson.num(number));
        p.put("team", LiveJson.str(s.getTeam() == null ? "NONE" : s.getTeam().name()));
        p.put("alive", LiveJson.bool(s.isAlive()));
        p.put("connected", LiveJson.bool(s.isConnected() && online != null && online.isOnline()));
        p.put("money", LiveJson.num(s.getMoney()));
        p.put("kills", LiveJson.num(s.getKills()));
        p.put("deaths", LiveJson.num(s.getDeaths()));
        p.put("operatorId", LiveJson.str(s.getOperatorId() == null ? "" : s.getOperatorId()));

        double health = 0;
        double maxHealth = 20;
        boolean spectator = false;
        String melee = "";
        String ranged = "";
        String bomb = "";
        List<String> utils = new ArrayList<>();
        Map<String, String> pos = new LinkedHashMap<>();
        pos.put("x", LiveJson.num(0));
        pos.put("y", LiveJson.num(0));
        pos.put("z", LiveJson.num(0));
        pos.put("yaw", LiveJson.num(0));

        if (online != null && online.isOnline()) {
            health = online.getHealth();
            try {
                var attr = online.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                maxHealth = attr != null ? attr.getValue() : Math.max(health, 20);
            } catch (Throwable t) {
                maxHealth = Math.max(health, 20);
            }
            spectator = online.getGameMode() == org.bukkit.GameMode.SPECTATOR;
            Location loc = online.getLocation();
            pos.put("x", LiveJson.num(round3(loc.getX())));
            pos.put("y", LiveJson.num(round3(loc.getY())));
            pos.put("z", LiveJson.num(round3(loc.getZ())));
            pos.put("yaw", LiveJson.num(round3(loc.getYaw())));

            PlayerInventory inv = online.getInventory();
            melee = itemLabel(items, inv.getItem(InventorySlots.MELEE));
            ranged = itemLabel(items, inv.getItem(InventorySlots.RANGED));
            bomb = itemLabel(items, inv.getItem(InventorySlots.BOMB));
            for (int i = InventorySlots.UTIL_1; i <= InventorySlots.UTIL_3; i++) {
                String u = itemLabel(items, inv.getItem(i));
                if (!u.isEmpty()) {
                    utils.add(LiveJson.str(u));
                }
            }
        }

        p.put("health", LiveJson.num(round3(health)));
        p.put("maxHealth", LiveJson.num(round3(maxHealth)));
        p.put("healthPercent", LiveJson.num(
                maxHealth > 0 ? Math.min(100, Math.round(health / maxHealth * 100)) : 0));
        p.put("spectator", LiveJson.bool(spectator));
        p.put("position", LiveJson.obj(pos));

        Map<String, String> weapons = new LinkedHashMap<>();
        weapons.put("melee", LiveJson.str(melee));
        weapons.put("ranged", LiveJson.str(ranged));
        weapons.put("bomb", LiveJson.str(bomb));
        weapons.put("utilities", LiveJson.arr(utils));
        p.put("weapons", LiveJson.obj(weapons));

        return LiveJson.obj(p);
    }

    private static String itemLabel(ItemManager items, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        if (items != null) {
            String id = items.getItemId(stack);
            if (id != null && !id.isEmpty()) {
                var gi = items.getGameItem(id);
                if (gi != null && gi.getName() != null) {
                    return stripColor(gi.getName());
                }
                return id;
            }
        }
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String stripColor(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
