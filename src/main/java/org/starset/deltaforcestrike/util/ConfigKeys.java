package org.starset.deltaforcestrike.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class ConfigKeys {

    private ConfigKeys() {}

    private static FileConfiguration cfg() {
        return DeltaForceStrike.getInstance().getConfig();
    }

    public static int teamSize() {
        return Math.max(1, cfg().getInt("queue.team-size", 3));
    }

    public static int maxPlayers() {
        FileConfiguration c = cfg();
        if (c.contains("queue.max-players")) {
            return Math.max(2, c.getInt("queue.max-players"));
        }
        if (c.contains("queue.max-player")) {
            return Math.max(2, c.getInt("queue.max-player"));
        }
        return Math.max(2, teamSize() * 2);
    }

    public static double buyZoneRadius() {
        return Math.max(1.0, cfg().getDouble("round.buy-zone-radius", 5.0));
    }

    /** 是否启用守护盾系统 */
    public static boolean shieldEnabled() {
        return cfg().getBoolean("player.shield-enabled", false);
    }

    /** 背包箭矢上限（发远程补满、拾取上限共用） */
    public static int arrowsPerRanged() {
        return Math.max(1, cfg().getInt("shop.arrows-per-ranged", 15));
    }

    /** 经济上限（默认 16000） */
    public static int maxMoney() {
        return Math.max(0, cfg().getInt("economy.max-money", 16000));
    }

    /**
     * 读取 locations.xxx；缺键返回 null。
     */
    public static Location readLocation(String node) {
        FileConfiguration c = cfg();
        DeltaForceStrike plugin = DeltaForceStrike.getInstance();

        if (!c.isConfigurationSection(node)) {
            plugin.getLogger().warning("[Config] 缺少坐标节点: " + node);
            return null;
        }
        if (!c.contains(node + ".x") || !c.contains(node + ".y") || !c.contains(node + ".z")) {
            plugin.getLogger().warning("[Config] " + node + " 缺少 x/y/z");
            return null;
        }

        String worldName = c.getString(node + ".world", Worlds.arenaName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Worlds.arenaWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("[Config] 世界不存在: " + worldName);
            return null;
        }

        Location loc = new Location(
                world,
                c.getDouble(node + ".x"),
                c.getDouble(node + ".y"),
                c.getDouble(node + ".z"),
                (float) c.getDouble(node + ".yaw", 0.0),
                (float) c.getDouble(node + ".pitch", 0.0)
        );

        if (c.getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Config] " + node + " -> " + world.getName()
                    + " " + loc.getX() + "," + loc.getY() + "," + loc.getZ());
        }
        return loc;
    }
}
