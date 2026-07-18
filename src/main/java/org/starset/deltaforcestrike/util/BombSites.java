package org.starset.deltaforcestrike.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.starset.deltaforcestrike.DeltaForceStrike;

import java.util.ArrayList;
import java.util.List;

public final class BombSites {

    public record Site(String id, Location center, double radius) {}

    private BombSites() {}

    public static List<Site> load() {
        List<Site> list = new ArrayList<>();
        ConfigurationSection sec = DeltaForceStrike.getInstance()
                .getConfig()
                .getConfigurationSection("bomb.sites");
        if (sec == null) {
            return list;
        }

        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            if (!s.contains("x") || !s.contains("y") || !s.contains("z")) {
                continue;
            }

            String worldName = s.getString("world", Worlds.arenaName());
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                world = Worlds.arenaWorld();
            }
            if (world == null) {
                continue;
            }

            Location center = new Location(
                    world,
                    s.getDouble("x"),
                    s.getDouble("y"),
                    s.getDouble("z")
            );
            double radius = Math.max(0.5, s.getDouble("radius", 3.0));
            list.add(new Site(key, center, radius));
        }
        return list;
    }

    /** 未配置任何包点时返回 false（禁止到处下包） */
    public static boolean isInAnySite(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        List<Site> sites = load();
        if (sites.isEmpty()) {
            return false;
        }
        for (Site site : sites) {
            if (site.center.getWorld() == null) {
                continue;
            }
            if (!site.center.getWorld().equals(loc.getWorld())) {
                continue;
            }
            if (Math.abs(loc.getY() - site.center.getY()) > 4.0) {
                continue;
            }
            double dx = loc.getX() - site.center.getX();
            double dz = loc.getZ() - site.center.getZ();
            if (dx * dx + dz * dz <= site.radius * site.radius) {
                return true;
            }
        }
        return false;
    }

    public static String describeSites() {
        List<Site> sites = load();
        if (sites.isEmpty()) {
            return "未配置(请 /dfs setsite a)";
        }
        StringBuilder sb = new StringBuilder();
        for (Site s : sites) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s.id.toUpperCase());
        }
        return sb.toString();
    }
}
