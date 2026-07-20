package org.starset.deltaforcestrike.bomb;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.util.BombSites;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A/B 包点中心浮动文字：每个包点 id 全局仅保留 1 个实体。
 */
public final class BombSiteMarkerService {

    private static final String PDC_PREFIX = "dfs_site:";

    private final DeltaForceStrike plugin;
    /** siteId(lower) → 当前唯一实体 UUID */
    private final Map<String, UUID> markers = new HashMap<>();
    private BukkitTask task;
    private boolean spawning;

    public BombSiteMarkerService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        // 延迟 1 tick：等世界实体加载后再扫残留
        Bukkit.getScheduler().runTask(plugin, () -> {
            clearAll();
            if (plugin.getConfig().getBoolean("bomb.site-markers.enabled", true)) {
                for (BombSites.Site site : BombSites.load()) {
                    ensureOne(site);
                }
            }
        });
        // 每 10 秒检查一次即可，避免频繁误判 spawn
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::ensureMarkers, 200L, 200L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAll();
    }

    public void respawnAll() {
        clearAll();
        if (!plugin.getConfig().getBoolean("bomb.site-markers.enabled", true)) {
            return;
        }
        for (BombSites.Site site : BombSites.load()) {
            ensureOne(site);
        }
    }

    private void ensureMarkers() {
        if (spawning) {
            return;
        }
        if (!plugin.getConfig().getBoolean("bomb.site-markers.enabled", true)) {
            clearAll();
            return;
        }

        List<BombSites.Site> sites = BombSites.load();
        Set<String> want = new HashSet<>();
        for (BombSites.Site site : sites) {
            String key = site.id().toLowerCase(Locale.ROOT);
            want.add(key);
            ensureOne(site);
        }

        // 配置里已删除的包点
        List<String> remove = new ArrayList<>();
        for (String key : markers.keySet()) {
            if (!want.contains(key)) {
                remove.add(key);
            }
        }
        for (String key : remove) {
            removeAllForKey(key);
            markers.remove(key);
        }

        // 全图去重：同一 key 只留一个
        dedupeWorldMarkers();
    }

    /**
     * 保证该包点有且仅有 1 个标记实体。
     */
    private void ensureOne(BombSites.Site site) {
        if (site == null || site.center() == null || site.center().getWorld() == null) {
            return;
        }
        String key = site.id().toLowerCase(Locale.ROOT);

        // 1) 已跟踪且仍有效 → 只更新文字，不重生
        UUID tracked = markers.get(key);
        Entity trackedEnt = tracked == null ? null : Bukkit.getEntity(tracked);
        if (trackedEnt != null && trackedEnt.isValid() && isOurMarker(trackedEnt, key)) {
            updateText(trackedEnt, site);
            // 仍清掉同 key 其它残留
            removeAllForKeyExcept(key, trackedEnt.getUniqueId());
            return;
        }

        // 2) 世界中是否已有该 key 的标记（map 丢了 UUID）
        Entity existing = findFirstMarker(key);
        if (existing != null && existing.isValid()) {
            markers.put(key, existing.getUniqueId());
            updateText(existing, site);
            removeAllForKeyExcept(key, existing.getUniqueId());
            return;
        }

        // 3) 真正缺失 → 先清残留再生成一个
        removeAllForKey(key);
        spawnMarker(site);
    }

    private void spawnMarker(BombSites.Site site) {
        if (spawning) {
            return;
        }
        String key = site.id().toLowerCase(Locale.ROOT);
        double yOff = plugin.getConfig().getDouble("bomb.site-markers.y-offset", 1.6);
        Location loc = site.center().clone().add(0, yOff, 0);
        World world = loc.getWorld();
        if (world == null || !Worlds.isArena(world)) {
            return;
        }

        Component text = buildText(site);
        spawning = true;
        try {
            // 再清一次，防并发
            removeAllForKey(key);

            TextDisplay display = world.spawn(loc, TextDisplay.class, d -> {
                d.text(text);
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setDefaultBackground(false);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(80, 0, 0, 0));
                try {
                    d.setAlignment(TextDisplay.TextAlignment.CENTER);
                } catch (Throwable ignored) {
                }
                try {
                    d.setLineWidth(200);
                } catch (Throwable ignored) {
                }
                // persistent=true 避免区块卸载后 ensure 反复补刷叠实体
                d.setPersistent(true);
                d.getPersistentDataContainer().set(
                        ItemKeys.id(), PersistentDataType.STRING, PDC_PREFIX + key);
            });
            markers.put(key, display.getUniqueId());
        } catch (Throwable t) {
            removeAllForKey(key);
            var stand = world.spawn(loc, org.bukkit.entity.ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setMarker(true);
                as.setCustomNameVisible(true);
                as.customName(Component.text(
                        site.id().toUpperCase(Locale.ROOT) + " 包点",
                        NamedTextColor.RED, TextDecoration.BOLD));
                as.setPersistent(true);
                as.getPersistentDataContainer().set(
                        ItemKeys.id(), PersistentDataType.STRING, PDC_PREFIX + key);
            });
            markers.put(key, stand.getUniqueId());
        } finally {
            spawning = false;
        }
    }

    private Component buildText(BombSites.Site site) {
        String label = site.id().toUpperCase(Locale.ROOT);
        return Component.text(label + " 包点", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("半径 " + (int) site.radius() + " 格", NamedTextColor.GRAY));
    }

    private void updateText(Entity e, BombSites.Site site) {
        Component text = buildText(site);
        if (e instanceof TextDisplay td) {
            try {
                td.text(text);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                e.customName(Component.text(
                        site.id().toUpperCase(Locale.ROOT) + " 包点",
                        NamedTextColor.RED, TextDecoration.BOLD));
                e.setCustomNameVisible(true);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isOurMarker(Entity e, String key) {
        if (e == null) {
            return false;
        }
        String id = e.getPersistentDataContainer().get(ItemKeys.id(), PersistentDataType.STRING);
        return id != null && id.equalsIgnoreCase(PDC_PREFIX + key);
    }

    private Entity findFirstMarker(String key) {
        World w = Worlds.arenaWorld();
        if (w == null) {
            return null;
        }
        String want = PDC_PREFIX + key;
        for (Entity e : w.getEntities()) {
            String id = e.getPersistentDataContainer().get(ItemKeys.id(), PersistentDataType.STRING);
            if (want.equalsIgnoreCase(id) && e.isValid()) {
                return e;
            }
        }
        return null;
    }

    private void removeAllForKey(String key) {
        removeAllForKeyExcept(key, null);
    }

    private void removeAllForKeyExcept(String key, UUID keep) {
        World w = Worlds.arenaWorld();
        if (w == null) {
            return;
        }
        String want = PDC_PREFIX + key;
        for (Entity e : new ArrayList<>(w.getEntities())) {
            String id = e.getPersistentDataContainer().get(ItemKeys.id(), PersistentDataType.STRING);
            if (want.equalsIgnoreCase(id)) {
                if (keep != null && keep.equals(e.getUniqueId())) {
                    continue;
                }
                e.remove();
            }
        }
    }

    /** 扫描竞技世界：每个 dfs_site:key 只留 1 个 */
    private void dedupeWorldMarkers() {
        World w = Worlds.arenaWorld();
        if (w == null) {
            return;
        }
        Map<String, Entity> keep = new HashMap<>();
        for (Entity e : new ArrayList<>(w.getEntities())) {
            String id = e.getPersistentDataContainer().get(ItemKeys.id(), PersistentDataType.STRING);
            if (id == null || !id.regionMatches(true, 0, PDC_PREFIX, 0, PDC_PREFIX.length())) {
                continue;
            }
            String key = id.substring(PDC_PREFIX.length()).toLowerCase(Locale.ROOT);
            Entity existing = keep.get(key);
            if (existing == null || !existing.isValid()) {
                keep.put(key, e);
                markers.put(key, e.getUniqueId());
            } else {
                // 已有一个 → 删掉多余
                e.remove();
            }
        }
    }

    private void clearAll() {
        markers.clear();
        World w = Worlds.arenaWorld();
        if (w == null) {
            return;
        }
        for (Entity e : new ArrayList<>(w.getEntities())) {
            String id = e.getPersistentDataContainer().get(ItemKeys.id(), PersistentDataType.STRING);
            if (id != null && id.regionMatches(true, 0, PDC_PREFIX, 0, PDC_PREFIX.length())) {
                e.remove();
            }
        }
    }
}
