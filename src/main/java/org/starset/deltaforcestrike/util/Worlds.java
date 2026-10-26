package org.starset.deltaforcestrike.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class Worlds {

    private Worlds() {}

    private static volatile String cachedArenaName;

    public static String arenaName() {
        String cached = cachedArenaName;
        if (cached != null) {
            return cached;
        }
        String name = DeltaForceStrike.getInstance()
                .getConfig()
                .getString("world.arena", "delta_force_strike");
        cachedArenaName = name;
        return name;
    }

    /** /dfs reload 后调用：arena 名可能被改 */
    public static void invalidateCache() {
        cachedArenaName = null;
    }

    public static boolean isArena(World world) {
        return world != null && world.getName().equalsIgnoreCase(arenaName());
    }

    public static boolean isArena(Player player) {
        return player != null && isArena(player.getWorld());
    }

    public static World arenaWorld() {
        return Bukkit.getWorld(arenaName());
    }
}
