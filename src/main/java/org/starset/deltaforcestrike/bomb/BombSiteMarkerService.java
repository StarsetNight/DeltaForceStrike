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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A/B 包点中心浮动文字提示。
 */
public final class BombSiteMarkerService {

    private final DeltaForceStrike plugin;
    private final Map<String, UUID> markers = new HashMap<>();
    private BukkitTask task;

    public BombSiteMarkerService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        respawnAll();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::ensureMarkers, 40L, 40L);
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
            spawnMarker(site);
        }
    }

    private void ensureMarkers() {
        if (!plugin.getConfig().getBoolean("bomb.site-markers.enabled", true)) {
            clearAll();
            return;
        }
        List<BombSites.Site> sites = BombSites.load();
        Set<String> want = new HashSet<>();
        for (BombSites.Site site : sites) {
            want.add(site.id().toLowerCase(Locale.ROOT));
            UUID id = markers.get(site.id().toLowerCase(Locale.ROOT));
            Entity e = id == null ? null : Bukkit.getEntity(id);
            if (e == null || !e.isValid()) {
                spawnMarker(site);
            }
        }
        // 移除已删除包点
        markers.entrySet().removeIf(en -> {
            if (want.contains(en.getKey())) {
                return false;
            }
            Entity e = Bukkit.getEntity(en.getValue());
            if (e != null) {
                e.remove();
            }
            return true;
        });
    }

    private void spawnMarker(BombSites.Site site) {
        if (site == null || site.center() == null || site.center().getWorld() == null) {
            return;
        }
        String key = site.id().toLowerCase(Locale.ROOT);
        UUID old = markers.remove(key);
        if (old != null) {
            Entity e = Bukkit.getEntity(old);
            if (e != null) {
                e.remove();
            }
        }

        double yOff = plugin.getConfig().getDouble("bomb.site-markers.y-offset", 1.6);
        Location loc = site.center().clone().add(0, yOff, 0);
        World world = loc.getWorld();
        if (world == null || !Worlds.isArena(world)) {
            return;
        }

        String label = site.id().toUpperCase(Locale.ROOT);
        Component text = Component.text(label + " 包点", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("半径 " + (int) site.radius() + " 格", NamedTextColor.GRAY));

        try {
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
                d.setPersistent(false);
                d.getPersistentDataContainer().set(
                        ItemKeys.id(), PersistentDataType.STRING, "dfs_site:" + key);
            });
            markers.put(key, display.getUniqueId());
        } catch (Throwable t) {
            // TextDisplay 不可用时：盔甲架名称兜底
            var stand = world.spawn(loc, org.bukkit.entity.ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setMarker(true);
                as.setCustomNameVisible(true);
                as.customName(Component.text(label + " 包点", NamedTextColor.RED, TextDecoration.BOLD));
                as.setPersistent(false);
                as.getPersistentDataContainer().set(
                        ItemKeys.id(), PersistentDataType.STRING, "dfs_site:" + key);
            });
            markers.put(key, stand.getUniqueId());
        }
    }

    private void clearAll() {
        for (UUID id : markers.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        markers.clear();
        // 清理残留
        World w = Worlds.arenaWorld();
        if (w == null) {
            return;
        }
        for (Entity e : w.getEntities()) {
            String id = e.getPersistentDataContainer().get(ItemKeys.id(), PersistentDataType.STRING);
            if (id != null && id.startsWith("dfs_site:")) {
                e.remove();
            }
        }
    }
}
