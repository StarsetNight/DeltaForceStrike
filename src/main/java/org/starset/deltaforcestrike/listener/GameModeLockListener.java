package org.starset.deltaforcestrike.listener;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.util.Worlds;

public class GameModeLockListener implements Listener {

    private final DeltaForceStrike plugin;

    public GameModeLockListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (!Worlds.isArena(event.getPlayer())) return;
        if (!plugin.getMatchManager().isInMatch(event.getPlayer())) return;

        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) return;

        GameMode neu = event.getNewGameMode();
        if (neu == GameMode.SPECTATOR) return; // 死亡旁观
        if (neu == GameMode.ADVENTURE) return;

        event.setCancelled(true);
        event.getPlayer().setGameMode(GameMode.ADVENTURE);
    }
}
