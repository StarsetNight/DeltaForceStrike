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

public class SpectatorLockListener implements Listener {

    private final DeltaForceStrike plugin;
    private final SpectatorLockService lock;

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
            plugin.getServer().getScheduler().runTask(plugin, () -> lock.tickPlayer(p));
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
