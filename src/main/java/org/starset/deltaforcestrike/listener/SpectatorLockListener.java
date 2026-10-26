package org.starset.deltaforcestrike.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.spectator.SpectatorLockService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpectatorLockListener implements Listener {

    private final DeltaForceStrike plugin;
    private final SpectatorLockService lock;
    /** onMove 纠正冷却：无目标时每个移动包都调度任务会形成任务风暴 */
    private static final long MOVE_FIX_INTERVAL_MS = 500;
    private final Map<UUID, Long> moveFixLastMs = new ConcurrentHashMap<>();

    public SpectatorLockListener(DeltaForceStrike plugin, SpectatorLockService lock) {
        this.plugin = plugin;
        this.lock = lock;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        if (!lock.shouldLock(p)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> lock.tickPlayer(p));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!lock.shouldLock(p)) {
            return;
        }
        if (p.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        if (p.getSpectatorTarget() == null) {
            // 节流：每 0.5s 最多调度一次纠正，兜底由 tickAll 覆盖
            long now = System.currentTimeMillis();
            UUID id = p.getUniqueId();
            Long last = moveFixLastMs.get(id);
            if (last != null && now - last < MOVE_FIX_INTERVAL_MS) {
                return;
            }
            moveFixLastMs.put(id, now);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                moveFixLastMs.remove(id);
                lock.tickPlayer(p);
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (!plugin.getConfig().getBoolean("spectator.scroll-cycle", true)) {
            return;
        }
        Player p = event.getPlayer();
        if (!lock.shouldLock(p) || p.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        int prev = event.getPreviousSlot();
        int now = event.getNewSlot();
        int dir = 1;
        if (now < prev || (prev == 0 && now == 8)) {
            dir = -1;
        }
        if (prev == 8 && now == 0) {
            dir = 1;
        }
        lock.cycleTeammate(p, dir);
    }
}
