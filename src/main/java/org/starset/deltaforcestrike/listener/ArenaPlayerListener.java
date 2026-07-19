package org.starset.deltaforcestrike.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.MatchManager;
import org.starset.deltaforcestrike.util.Worlds;

public class ArenaPlayerListener implements Listener {

    private final DeltaForceStrike plugin;

    public ArenaPlayerListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!Worlds.isArena(player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getMatchManager().handleEnterArena(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        MatchManager mm = plugin.getMatchManager();

        if (Worlds.isArena(player)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> mm.handleEnterArena(player));
        } else if (Worlds.isArena(event.getFrom())) {
            mm.leave(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 对局中断线：保留 session；战斗阶段判死；购买阶段可重连归队
        if (plugin.getMatchManager().isInMatch(event.getPlayer())) {
            plugin.getMatchManager().handleDisconnect(event.getPlayer());
        }
    }
}
