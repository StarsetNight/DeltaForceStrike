package org.starset.deltaforcestrike.util;

import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.starset.deltaforcestrike.DeltaForceStrike;

public final class ArenaCleanup {

    private ArenaCleanup() {}

    /** 清除竞技世界全部掉落物 */
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
        if (DeltaForceStrike.getInstance().getConfig().getBoolean("debug.enabled", false) && count > 0) {
            DeltaForceStrike.getInstance().getLogger()
                    .info("[DFS] 已清除掉落物: " + count);
        }
    }
}
