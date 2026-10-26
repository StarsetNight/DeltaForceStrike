package org.starset.deltaforcestrike.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.tournament.TournamentService;
import org.starset.deltaforcestrike.util.Worlds;

/**
 * 接收客户端赛事握手包并在 PlayerJoin/Quit 时触发超时检查与清理。
 */
public final class TournamentListener implements PluginMessageListener, Listener {

    private final DeltaForceStrike plugin;

    public TournamentListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!TournamentService.CHANNEL.equals(channel) || player == null) return;
        TournamentService svc = plugin.getTournamentService();
        if (svc == null) return;
        byte[] reply = svc.handleIncoming(player, message);
        if (reply != null) {
            player.sendPluginMessage(plugin, channel, reply);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        TournamentService svc = plugin.getTournamentService();
        if (svc == null || !svc.isEnabled()) return;
        Player p = e.getPlayer();
        if (!Worlds.isArena(p)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && Worlds.isArena(p)) {
                svc.onJoin(p);
            }
        });
    }

    /**
     * 从大堂/主世界传送进入竞技世界的玩家不会触发 PlayerJoinEvent，
     * 必须监听换世界，否则可绕过赛事握手无限期逗留。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        TournamentService svc = plugin.getTournamentService();
        if (svc == null || !svc.isEnabled()) return;
        Player p = e.getPlayer();
        if (!Worlds.isArena(p)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && Worlds.isArena(p)) {
                svc.onJoin(p);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        TournamentService svc = plugin.getTournamentService();
        if (svc == null) return;
        svc.onQuit(e.getPlayer());
    }
}
