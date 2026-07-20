package org.starset.deltaforcestrike.listener;

import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.operator.OperatorKeys;
import org.starset.deltaforcestrike.operator.OperatorLoadout;
import org.starset.deltaforcestrike.operator.OperatorService;
import org.starset.deltaforcestrike.operator.SkillKind;
import org.starset.deltaforcestrike.operator.skill.impl.RoundSmokeHandler;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.UUID;

/**
 * 技能释放：必须手持对应技能物品 + 正确槽位，禁止空手施放。
 */
public class OperatorSkillListener implements Listener {

    private final DeltaForceStrike plugin;

    public OperatorSkillListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!Worlds.isArena(player) || !plugin.getMatchManager().isInMatch(player)) {
            return;
        }

        OperatorService ops = plugin.getOperatorService();
        if (ops == null) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        // 空手 / 非技能：忽略（让 GrenadeListener 处理道具）
        if (!ops.isSkillStack(hand)) {
            return;
        }

        SkillKind kind = ops.getSkillKind(hand);
        if (kind == null) {
            return;
        }

        // 槽位必须匹配
        int held = player.getInventory().getHeldItemSlot();
        int expected = switch (kind) {
            case SIGNATURE -> InventorySlots.SIGNATURE;
            case PURCHASABLE -> InventorySlots.PURCHASABLE;
            case ULTIMATE -> InventorySlots.ULTIMATE;
            default -> -1;
        };
        if (expected < 0 || held != expected) {
            player.sendMessage("§c请在正确技能槽使用（7招牌 / 8购买 / 9大招）。");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        ops.tryUseSkill(player, kind);
    }

    /**
     * 技能投掷物落地（强效烟幕等）—— 不经过 GrenadeService。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSkillProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)) {
            return;
        }
        var pdc = ball.getPersistentDataContainer();
        if (!pdc.has(OperatorKeys.skillProjectile(), PersistentDataType.STRING)) {
            return;
        }

        String projId = pdc.get(OperatorKeys.skillProjectile(), PersistentDataType.STRING);
        if (projId == null) {
            ball.remove();
            return;
        }

        Location hit = resolveHitLocation(event, ball);

        if (RoundSmokeHandler.PROJECTILE_ID.equalsIgnoreCase(projId)) {
            handleRoundSmokeHit(ball, hit);
        }

        ball.remove();
    }

    private void handleRoundSmokeHit(Snowball ball, Location hit) {
        OperatorService ops = plugin.getOperatorService();
        if (ops == null || hit == null || hit.getWorld() == null) {
            return;
        }

        var pdc = ball.getPersistentDataContainer();
        double radius = pdc.getOrDefault(OperatorKeys.skillRadius(), PersistentDataType.DOUBLE,
                RoundSmokeHandler.DEFAULT_RADIUS);
        int duration = pdc.getOrDefault(OperatorKeys.skillDuration(), PersistentDataType.INTEGER,
                RoundSmokeHandler.DEFAULT_DURATION_SECONDS);

        Player owner = null;
        if (ball.getShooter() instanceof Player p) {
            owner = p;
        } else {
            String raw = pdc.get(OperatorKeys.skillOwner(), PersistentDataType.STRING);
            if (raw != null) {
                try {
                    owner = plugin.getServer().getPlayer(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        OperatorLoadout load = null;
        if (owner != null) {
            load = ops.getLoadout(owner.getUniqueId());
        }
        if (load == null && owner != null) {
            load = ops.ensureLoadout(owner);
        }
        if (load == null) {
            return;
        }

        ops.deployRoundSmoke(owner, load, hit, radius, duration);
    }

    private static Location resolveHitLocation(ProjectileHitEvent event, Snowball ball) {
        if (event.getHitBlock() != null) {
            if (event.getHitBlockFace() != null) {
                return event.getHitBlock().getRelative(event.getHitBlockFace())
                        .getLocation().add(0.5, 0.1, 0.5);
            }
            return event.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
        }
        if (event.getHitEntity() != null) {
            return event.getHitEntity().getLocation().add(0, 0.5, 0);
        }
        return ball.getLocation();
    }

    /** 技能雪球不造成原版伤害 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSkillProjectileDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Snowball ball)) {
            return;
        }
        if (ball.getPersistentDataContainer().has(OperatorKeys.skillProjectile(), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!arrow.getPersistentDataContainer().has(OperatorKeys.reconArrow(), PersistentDataType.BYTE)) {
            return;
        }

        double radius = arrow.getPersistentDataContainer()
                .getOrDefault(OperatorKeys.reconRadius(), PersistentDataType.DOUBLE, 24.0);
        int glowSec = arrow.getPersistentDataContainer()
                .getOrDefault(OperatorKeys.reconGlow(), PersistentDataType.INTEGER, 3);

        Match match = plugin.getMatchManager().getMatch();
        if (match == null) {
            return;
        }

        Player shooter = arrow.getShooter() instanceof Player p ? p : null;
        PlayerSession ss = shooter == null ? null : match.getSession(shooter.getUniqueId());
        var loc = arrow.getLocation();

        for (Player other : match.onlinePlayers()) {
            if (shooter != null && other.getUniqueId().equals(shooter.getUniqueId())) {
                continue;
            }
            PlayerSession os = match.getSession(other.getUniqueId());
            if (os == null || !os.isAlive()) {
                continue;
            }
            if (ss != null && os.getTeam() == ss.getTeam()) {
                continue;
            }
            double dx = other.getLocation().getX() - loc.getX();
            double dz = other.getLocation().getZ() - loc.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                other.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING, glowSec * 20, 0, false, true, true));
            }
        }
        arrow.remove();
    }
}
