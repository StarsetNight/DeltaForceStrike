package org.starset.deltaforcestrike.grenade;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.round.RoundState;
import org.starset.deltaforcestrike.util.GrenadeType;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.Collection;
import java.util.UUID;

/**
 * 战术道具：烟雾 / 凋零 / 高爆火焰
 */
public class GrenadeService {

    public static final String META_TYPE = "dfs_grenade_type";
    public static final String META_THROWER = "dfs_grenade_thrower";

    private final DeltaForceStrike plugin;

    public GrenadeService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public boolean canUseUtility(Player player) {
        if (!Worlds.isArena(player)) {
            return false;
        }
        if (!plugin.getMatchManager().isInMatch(player)) {
            return false;
        }
        Match match = plugin.getMatchManager().getMatch();
        if (match == null || match.getState() != MatchState.IN_PROGRESS) {
            return false;
        }
        RoundState rs = match.getRoundManager().getState();
        // 仅战斗 / 拆弹阶段可投（购买阶段禁止）
        if (rs != RoundState.COMBAT && rs != RoundState.BOMB_PLANTED) {
            return false;
        }
        PlayerSession s = match.getSession(player.getUniqueId());
        return s != null && s.isAlive();
    }

    /**
     * 尝试投掷手中道具。
     * @return true 表示已处理（应 cancel 交互）
     */
    public boolean tryThrow(Player player, EquipmentSlot hand) {
        if (!canUseUtility(player)) {
            return false;
        }
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        ItemManager items = plugin.getItemManager();
        GrenadeType type = GrenadeType.fromItem(items, stack);
        if (type == null) {
            return false;
        }

        // 举盾时 GDD 不可用道具——若副手是盾且在 blocking，可禁
        if (player.isBlocking()) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§c举盾时无法使用道具"));
            return true;
        }

        // 消耗 1 个
        if (stack.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }

        Snowball ball = player.launchProjectile(Snowball.class);
        ball.setVelocity(player.getLocation().getDirection().multiply(1.35));
        ball.setShooter(player);
        ball.setGravity(true);
        ball.setMetadata(META_TYPE, new FixedMetadataValue(plugin, type.name()));
        ball.setMetadata(META_THROWER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // 视觉：略大的雪球轨迹粒子
        ball.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.9f);

        return true;
    }

    public boolean isGrenade(Snowball ball) {
        return ball != null && ball.hasMetadata(META_TYPE);
    }

    public GrenadeType getType(Snowball ball) {
        if (!isGrenade(ball)) {
            return null;
        }
        try {
            return GrenadeType.valueOf(ball.getMetadata(META_TYPE).get(0).asString());
        } catch (Exception e) {
            return null;
        }
    }

    public void onImpact(Snowball ball, Location hit) {
        GrenadeType type = getType(ball);
        if (type == null || hit == null || hit.getWorld() == null) {
            return;
        }
        UUID throwerId = null;
        if (ball.hasMetadata(META_THROWER)) {
            try {
                throwerId = UUID.fromString(ball.getMetadata(META_THROWER).get(0).asString());
            } catch (Exception ignored) {
            }
        }
        Player thrower = throwerId == null ? null : plugin.getServer().getPlayer(throwerId);

        switch (type) {
            case SMOKE -> detonateSmoke(hit);
            case WITHER -> detonateWither(hit, thrower);
            case INCENDIARY -> detonateIncendiary(hit, thrower);
        }
    }

    // ------------------------------------------------------------------
    // 烟雾弹：落点大量粒子，持续遮挡
    // ------------------------------------------------------------------

    private void detonateSmoke(Location center) {
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.6f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 0.5f);

        int durationTicks = plugin.getConfig().getInt("grenade.smoke.duration-ticks", 20 * 8); // 8s
        double radius = plugin.getConfig().getDouble("grenade.smoke.radius", 4.0);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= durationTicks) {
                    cancel();
                    return;
                }
                // 每 2 tick 喷一波
                if (t % 2 == 0) {
                    for (int i = 0; i < 40; i++) {
                        double ox = (Math.random() * 2 - 1) * radius;
                        double oy = Math.random() * 2.8;
                        double oz = (Math.random() * 2 - 1) * radius;
                        if (ox * ox + oz * oz > radius * radius) {
                            continue;
                        }
                        Location p = center.clone().add(ox, oy, oz);
                        try {
                            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p, 2, 0.15, 0.2, 0.15, 0.01);
                            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, p, 1, 0.2, 0.25, 0.2, 0.01);
                        } catch (Throwable ex) {
                            world.spawnParticle(Particle.SMOKE, p, 4, 0.2, 0.3, 0.2, 0.02);
                            world.spawnParticle(Particle.LARGE_SMOKE, p, 2, 0.25, 0.35, 0.25, 0.01);
                        }
                    }
                    // 中心加浓
                    world.spawnParticle(Particle.CLOUD, center.clone().add(0, 1, 0), 15, radius * 0.4, 1.0, radius * 0.4, 0.02);
                }
                // 可选：给云内敌人短暂黑暗（增强遮挡，可配置）
                if (plugin.getConfig().getBoolean("grenade.smoke.apply-darkness", false) && t % 20 == 0) {
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(center) <= radius * radius) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false, true));
                        }
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------
    // 凋零手雷：滞留型凋零 I
    // ------------------------------------------------------------------

    private void detonateWither(Location center, Player thrower) {
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.4f);
        world.playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.8f);

        int durationTicks = plugin.getConfig().getInt("grenade.wither.duration-ticks", 20 * 6);
        float radius = (float) plugin.getConfig().getDouble("grenade.wither.radius", 3.0);
        int amplifier = plugin.getConfig().getInt("grenade.wither.amplifier", 0); // 0 = 凋零I

        AreaEffectCloud cloud = world.spawn(center.clone().add(0, 0.2, 0), AreaEffectCloud.class, c -> {
            c.setRadius(radius);
            c.setDuration(durationTicks);
            c.setRadiusPerTick(0f); // 不缩小，更像固定滞留区
            c.setRadiusOnUse(0f);
            c.setWaitTime(10);
            c.setReapplicationDelay(20);
            c.setSource(thrower);
            try {
                c.setParticle(Particle.ENTITY_EFFECT, Color.fromRGB(40, 40, 40));
            } catch (Throwable t) {
                c.setParticle(Particle.SMOKE);
            }
            // 凋零 I
            c.addCustomEffect(new PotionEffect(
                    PotionEffectType.WITHER,
                    40, // 每次施加持续 2s，云会重复上
                    amplifier,
                    false,
                    true,
                    true
            ), true);
        });

        // 额外黑色粒子点缀
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= durationTicks || cloud.isDead()) {
                    cancel();
                    return;
                }
                if (t % 4 == 0) {
                    world.spawnParticle(Particle.SMOKE, center.clone().add(0, 0.5, 0),
                            8, radius * 0.5, 0.4, radius * 0.5, 0.01);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------
    // 高爆火焰弹：低伤 + 大击退（类 BedWars Fireball）
    // ------------------------------------------------------------------

    private void detonateIncendiary(Location center, Player thrower) {
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.1f);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.7f);

        try {
            world.spawnParticle(Particle.EXPLOSION, center, 2);
        } catch (Throwable t) {
            world.spawnParticle(Particle.SMOKE, center, 20, 0.5, 0.5, 0.5, 0.05);
        }
        world.spawnParticle(Particle.FLAME, center, 40, 0.8, 0.5, 0.8, 0.08);
        world.spawnParticle(Particle.LAVA, center, 8, 0.4, 0.2, 0.4, 0);

        double radius = plugin.getConfig().getDouble("grenade.incendiary.radius", 4.5);
        double damage = plugin.getConfig().getDouble("grenade.incendiary.damage", 4.0); // 2 心
        double kbHorizontal = plugin.getConfig().getDouble("grenade.incendiary.knockback-horizontal", 2.2);
        double kbVertical = plugin.getConfig().getDouble("grenade.incendiary.knockback-vertical", 0.85);
        boolean selfDamage = plugin.getConfig().getBoolean("grenade.incendiary.self-damage", false);

        Collection<Player> players = world.getPlayers();
        for (Player p : players) {
            if (!plugin.getMatchManager().isInMatch(p)) {
                continue;
            }
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            PlayerSession s = plugin.getMatchManager().getMatch() == null
                    ? null
                    : plugin.getMatchManager().getMatch().getSession(p.getUniqueId());
            if (s != null && !s.isAlive()) {
                continue;
            }

            double dist = p.getLocation().distance(center);
            if (dist > radius) {
                continue;
            }

            // 不伤自己（默认，BedWars 火球通常会推自己）
            boolean isSelf = thrower != null && p.getUniqueId().equals(thrower.getUniqueId());
            if (isSelf && !selfDamage) {
                // 仍给强击退
            } else {
                double falloff = 1.0 - (dist / radius);
                double dmg = Math.max(1.0, damage * falloff);
                if (!(isSelf && !selfDamage)) {
                    p.damage(dmg, thrower);
                }
            }

            // 大击退：中心越近越强
            Vector dir = p.getLocation().toVector().subtract(center.toVector());
            if (dir.lengthSquared() < 0.01) {
                dir = p.getLocation().getDirection().multiply(-1);
            }
            dir.normalize();
            double strength = (1.0 - dist / radius);
            strength = Math.max(0.35, strength);
            Vector kb = dir.multiply(kbHorizontal * strength).setY(kbVertical * (0.5 + 0.5 * strength));
            p.setVelocity(p.getVelocity().add(kb));
            p.setFallDistance(0f);
        }

        // 不破坏方块；可选小火焰（易伤图可关）
        if (plugin.getConfig().getBoolean("grenade.incendiary.place-fire", false)) {
            // 不默认放火，防破坏地图
        }
    }
}
