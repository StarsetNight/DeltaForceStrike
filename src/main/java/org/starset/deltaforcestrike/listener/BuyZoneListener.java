package org.starset.deltaforcestrike.listener;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.ConfigKeys;
import org.starset.deltaforcestrike.util.Worlds;

public class BuyZoneListener implements Listener {

    private final DeltaForceStrike plugin;
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    public BuyZoneListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        // Paper: getTo() 在移动事件中非 null；仍用局部变量避免重复调用
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!Worlds.isArena(player)) return;

        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) return;
        if (match.getRoundManager().getState() != RoundState.BUY) return;

        PlayerSession session = match.getSession(player.getUniqueId());
        if (session == null || !session.hasTeam() || !session.isAlive()) return;

        Location center = teamSpawn(session.getTeam());
        if (center == null || center.getWorld() == null) return;
        if (!player.getWorld().equals(center.getWorld())) return;

        double dx = to.getX() - center.getX();
        double dz = to.getZ() - center.getZ();
        double distSq = dx * dx + dz * dz;
        double r = ConfigKeys.buyZoneRadius();
        if (distSq <= r * r) return;

        double dist = Math.sqrt(distSq);
        if (dist < 1e-6) {
            return;
        }
        double nx = center.getX() + dx / dist * (r - 0.25);
        double nz = center.getZ() + dz / dist * (r - 0.25);
        event.setTo(new Location(to.getWorld(), nx, to.getY(), nz, to.getYaw(), to.getPitch()));
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
        player.sendActionBar(LEGACY.deserialize("§c购买阶段请留在出生点 §e" + (int) r + " §c格内"));
    }

    private Location teamSpawn(Team team) {
        String node = team == Team.T ? "locations.team-t-spawn" : "locations.team-ct-spawn";
        return ConfigKeys.readLocation(node);
    }
}
