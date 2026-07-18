package org.starset.deltaforcestrike.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.util.Worlds;

public class BombListener implements Listener {

    private final DeltaForceStrike plugin;

    public BombListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!Worlds.isArena(event.getPlayer())) return;
        if (event.getBlockPlaced().getType() != Material.TNT) return;

        if (plugin.getBombManager().tryBeginPlant(event.getPlayer(), event.getBlockAgainst())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getMatchManager().isInMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!Worlds.isArena(player)) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (plugin.getBombManager().tryBeginPlant(player, event.getClickedBlock())) {
                event.setCancelled(true);
                return;
            }
        }

        if (plugin.getBombManager().isPlanted() && player.isSneaking()) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    || event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (plugin.getBombManager().tryBeginDefuse(player)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!Worlds.isArena(event.getPlayer())) return;
        if (!event.isSneaking()) {
            plugin.getBombManager().cancelChannel(event.getPlayer());
            return;
        }
        plugin.getBombManager().tryBeginDefuse(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (event.getEntity() instanceof TNTPrimed tnt
                && plugin.getBombManager().isOurPrimed(tnt)) {
            event.setFire(false);
            event.setRadius(0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof TNTPrimed tnt) {
            if (plugin.getBombManager().isOurPrimed(tnt) || Worlds.isArena(tnt.getWorld())) {
                event.blockList().clear();
                event.setYield(0f);
            }
        }
    }
}
