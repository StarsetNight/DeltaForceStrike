package org.starset.deltaforcestrike.util;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class ArenaCleanup {

    private ArenaCleanup() {}

    public static void clearDrops() {
        World world = Worlds.arenaWorld();
        if (world == null) return;
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item) {
                entity.remove();
                count++;
            }
        }
        if (count > 0 && DeltaForceStrike.getInstance().getConfig().getBoolean("debug.enabled", false)) {
            DeltaForceStrike.getInstance().getLogger().info("[DFS] 清除掉落物: " + count);
        }
    }
}
