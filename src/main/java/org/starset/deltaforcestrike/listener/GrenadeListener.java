package org.starset.deltaforcestrike.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.grenade.GrenadeService;
import org.starset.deltaforcestrike.util.Worlds;

public class GrenadeListener implements Listener {

    private final DeltaForceStrike plugin;
    private final GrenadeService grenades;

    public GrenadeListener(DeltaForceStrike plugin, GrenadeService grenades) {
        this.plugin = plugin;
        this.grenades = grenades;
    }

    /**
     * 右键投掷战术道具（主手/副手）。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == null) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!Worlds.isArena(player)) {
            return;
        }

        // 只处理主手一次，避免双触发；副手单独
        EquipmentSlot hand = event.getHand();
        if (grenades.tryThrow(player, hand)) {
            event.setCancelled(true);
            // 防止对着方块还放置
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    /**
     * 落点引爆；取消雪球对实体的击退/伤害。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)) {
            return;
        }
        if (!grenades.isGrenade(ball)) {
            return;
        }

        // 落点：方块或实体位置
        org.bukkit.Location hit;
        if (event.getHitBlock() != null) {
            hit = event.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
            if (event.getHitBlockFace() != null) {
                hit = event.getHitBlock().getRelative(event.getHitBlockFace())
                        .getLocation().add(0.5, 0.1, 0.5);
            }
        } else if (event.getHitEntity() != null) {
            hit = event.getHitEntity().getLocation().add(0, 0.5, 0);
        } else {
            hit = ball.getLocation();
        }

        grenades.onImpact(ball, hit);
        ball.remove();
    }

    /**
     * 防止手雷雪球造成原版雪球伤害（若有）。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Snowball ball && grenades.isGrenade(ball)) {
            event.setCancelled(true);
        }
    }
}
