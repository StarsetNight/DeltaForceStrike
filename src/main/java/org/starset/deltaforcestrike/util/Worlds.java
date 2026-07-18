package org.starset.deltaforcestrike.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class Worlds {

    private Worlds() {}

    public static String arenaName() {
        return DeltaForceStrike.getInstance()
                .getConfig()
                .getString("world.arena", "delta_force_strike");
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
