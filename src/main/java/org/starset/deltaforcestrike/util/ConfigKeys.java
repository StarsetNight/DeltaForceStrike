package org.starset.deltaforcestrike.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class ConfigKeys {

    private ConfigKeys() {}

    public static int teamSize() {
        FileConfiguration c = cfg();
        // 唯一：queue.team-size
        return Math.max(1, c.getInt("queue.team-size", 3));
    }

    public static int maxPlayers() {
        FileConfiguration c = cfg();
        // 唯一：queue.max-players（兼容误写 max-player）
        if (c.contains("queue.max-players")) {
            return Math.max(2, c.getInt("queue.max-players"));
        }
        if (c.contains("queue.max-player")) {
            return Math.max(2, c.getInt("queue.max-player"));
        }
        // 兼容旧 game.*（读一次后建议删掉配置里的重复）
        if (c.contains("game.max-players")) {
            return Math.max(2, c.getInt("game.max-players"));
        }
        if (c.contains("game.max-player")) {
            return Math.max(2, c.getInt("game.max-player"));
        }
        return teamSize() * 2;
    }

    /**
     * 读取 locations.xxx
     * 失败返回 null（调用方不要静默用 0,64,0）
     */
    public static Location readLocation(String node) {
        // node 例: "locations.queue-spawn"
        FileConfiguration c = cfg();
        DeltaForceStrike plugin = DeltaForceStrike.getInstance();

        if (!c.isConfigurationSection(node)) {
            plugin.getLogger().warning("[Config] 缺少坐标节点: " + node
                    + " — 请编辑 plugins/DeltaForceStrike/config.yml");
            return null;
        }

        String worldName = c.getString(node + ".world", Worlds.arenaName());
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            world = Worlds.arenaWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("[Config] 世界不存在: " + worldName
                    + "（节点 " + node + "）");
            return null;
        }

        // 必须显式配置 x/y/z；缺一则失败，避免默认摔死
        if (!c.contains(node + ".x") || !c.contains(node + ".y") || !c.contains(node + ".z")) {
            plugin.getLogger().warning("[Config] " + node + " 缺少 x/y/z");
            return null;
        }

        double x = c.getDouble(node + ".x");
        double y = c.getDouble(node + ".y");
        double z = c.getDouble(node + ".z");
        float yaw = (float) c.getDouble(node + ".yaw", 0.0);
        float pitch = (float) c.getDouble(node + ".pitch", 0.0);

        Location loc = new Location(world, x, y, z, yaw, pitch);

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Config] 读取 " + node + " → "
                    + world.getName() + " " + x + "," + y + "," + z);
        }
        return loc;
    }

    private static FileConfiguration cfg() {
        return DeltaForceStrike.getInstance().getConfig();
    }
}
