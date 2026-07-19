package org.starset.deltaforcestrike.grenade;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.persistence.PersistentDataType;
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
import org.starset.deltaforcestrike.util.ConfigKeys;
import org.starset.deltaforcestrike.util.GrenadeKeys;
import org.starset.deltaforcestrike.util.GrenadeType;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.UUID;

/**
 * 战术道具：烟雾 / 凋零 / 高爆火焰。
 * 雪球轨迹 + PDC 标记（不使用已弃用 Metadata API）。
 */
public class GrenadeService {

    private final DeltaForceStrike plugin;
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    public GrenadeService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    private double particleMul() {
        return Math.max(1.0, plugin.getConfig().getDouble("grenade.particle-multiplier", 2.0));
    }

    /** 粒子数量 × 倍率 */
    private int pc(int base) {
        return Math.max(1, (int) Math.round(base * particleMul()));
    }

    // ------------------------------------------------------------------
    // 使用条件
    // ------------------------------------------------------------------

    public boolean canUseUtility(Player player) {
        if (player == null || !Worlds.isArena(player)) {
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
        if (rs != RoundState.COMBAT && rs != RoundState.BOMB_PLANTED) {
            return false;
        }
        PlayerSession s = match.getSession(player.getUniqueId());
        return s != null && s.isAlive();
    }

    // ------------------------------------------------------------------
    // 投掷
    // ------------------------------------------------------------------

    /**
     * 尝试投掷手中战术道具。
     *
     * @return true 表示已处理（调用方应 cancel 交互）
     */
    public boolean tryThrow(Player player, EquipmentSlot hand) {
        if (!canUseUtility(player)) {
            return false;
        }

        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        // 干员技能（如艾尔强效烟幕 aier_smoke）禁止走战术道具路径
        var ops = plugin.getOperatorService();
        if (ops != null && ops.isSkillStack(stack)) {
            return false;
        }

        ItemManager items = plugin.getItemManager();
        GrenadeType type = GrenadeType.fromItem(items, stack);
        if (type == null) {
            return false;
        }

        // 配置开启盾时：举盾不可投
        if (ConfigKeys.shieldEnabled() && player.isBlocking()) {
            player.sendActionBar(LEGACY.deserialize("§c举盾时无法使用道具"));
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
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(stack);
            } else {
                player.getInventory().setItemInMainHand(stack);
            }
        }

        Snowball ball = player.launchProjectile(Snowball.class);
        ball.setVelocity(player.getLocation().getDirection().multiply(1.35));
        ball.setShooter(player);
        ball.setGravity(true);

        // PDC 标记（替代 FixedMetadataValue）
        ball.getPersistentDataContainer().set(
                GrenadeKeys.type(),
                PersistentDataType.STRING,
                type.name()
        );
        ball.getPersistentDataContainer().set(
                GrenadeKeys.thrower(),
                PersistentDataType.STRING,
                player.getUniqueId().toString()
        );

        ball.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.9f);
        return true;
    }

    // ------------------------------------------------------------------
    // PDC 读取
    // ------------------------------------------------------------------

    public boolean isGrenade(Snowball ball) {
        if (ball == null) {
            return false;
        }
        // 仅 GrenadeKeys；技能投掷物（op_skill_projectile）不算战术道具
        return ball.getPersistentDataContainer().has(GrenadeKeys.type(), PersistentDataType.STRING);
    }

    public GrenadeType getType(Snowball ball) {
        if (ball == null || !isGrenade(ball)) {
            return null;
        }
        String raw = ball.getPersistentDataContainer().get(GrenadeKeys.type(), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return GrenadeType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID getThrowerId(Snowball ball) {
        if (ball == null) {
            return null;
        }
        String raw = ball.getPersistentDataContainer().get(GrenadeKeys.thrower(), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Player resolveThrower(Snowball ball) {
        if (ball.getShooter() instanceof Player p) {
            return p;
        }
        UUID id = getThrowerId(ball);
        return id == null ? null : plugin.getServer().getPlayer(id);
    }

    // ------------------------------------------------------------------
    // 落点
    // ------------------------------------------------------------------

    public void onImpact(Snowball ball, Location hit) {
        GrenadeType type = getType(ball);
        if (type == null || hit == null || hit.getWorld() == null) {
            return;
        }

        Player thrower = resolveThrower(ball);

        switch (type) {
            case SMOKE -> detonateSmoke(hit);
            case WITHER -> detonateWither(hit, thrower);
            case INCENDIARY -> detonateIncendiary(hit, thrower);
        }
    }

    // ------------------------------------------------------------------
    // 烟雾弹
    // ------------------------------------------------------------------

    private void detonateSmoke(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.6f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 0.5f);

        int durationTicks = plugin.getConfig().getInt("grenade.smoke.duration-ticks", 160);
        double radius = plugin.getConfig().getDouble("grenade.smoke.radius", 4.0);
        boolean darkness = plugin.getConfig().getBoolean("grenade.smoke.apply-darkness", false);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= durationTicks) {
                    cancel();
                    return;
                }
                if (t % 2 == 0) {
                    int samples = pc(40);
                    for (int i = 0; i < samples; i++) {
                        double ox = (Math.random() * 2 - 1) * radius;
                        double oy = Math.random() * 2.8;
                        double oz = (Math.random() * 2 - 1) * radius;
                        if (ox * ox + oz * oz > radius * radius) {
                            continue;
                        }
                        Location p = center.clone().add(ox, oy, oz);
                        try {
                            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p, pc(2), 0.15, 0.2, 0.15, 0.01);
                            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, p, pc(1), 0.2, 0.25, 0.2, 0.01);
                        } catch (Throwable ex) {
                            world.spawnParticle(Particle.SMOKE, p, pc(4), 0.2, 0.3, 0.2, 0.02);
                            world.spawnParticle(Particle.LARGE_SMOKE, p, pc(2), 0.25, 0.35, 0.25, 0.01);
                        }
                    }
                    world.spawnParticle(
                            Particle.CLOUD,
                            center.clone().add(0, 1, 0),
                            pc(15),
                            radius * 0.4,
                            1.0,
                            radius * 0.4,
                            0.02
                    );
                }
                if (darkness && t % 20 == 0) {
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(center) <= radius * radius) {
                            p.addPotionEffect(new PotionEffect(
                                    PotionEffectType.DARKNESS, 30, 0, false, false, true));
                        }
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------
    // 凋零手雷
    // ------------------------------------------------------------------

    private void detonateWither(Location center, Player thrower) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.4f);
        world.playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.8f);

        int durationTicks = plugin.getConfig().getInt("grenade.wither.duration-ticks", 120);
        float radius = (float) plugin.getConfig().getDouble("grenade.wither.radius", 3.0);
        int amplifier = plugin.getConfig().getInt("grenade.wither.amplifier", 0);

        world.spawn(center.clone().add(0, 0.2, 0), AreaEffectCloud.class, c -> {
            c.setRadius(radius);
            c.setDuration(durationTicks);
            c.setRadiusPerTick(0f);
            c.setRadiusOnUse(0f);
            c.setWaitTime(10);
            c.setReapplicationDelay(20);
            c.setSource(thrower);
            try {
                c.setParticle(Particle.ENTITY_EFFECT, Color.fromRGB(40, 40, 40));
            } catch (Throwable t) {
                c.setParticle(Particle.SMOKE);
            }
            c.addCustomEffect(
                    new PotionEffect(PotionEffectType.WITHER, 40, amplifier, false, true, true),
                    true
            );
        });

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= durationTicks) {
                    cancel();
                    return;
                }
                if (t % 4 == 0) {
                    world.spawnParticle(
                            Particle.SMOKE,
                            center.clone().add(0, 0.5, 0),
                            pc(8),
                            radius * 0.5,
                            0.4,
                            radius * 0.5,
                            0.01
                    );
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------
    // 高爆火焰弹
    // ------------------------------------------------------------------

    private void detonateIncendiary(Location center, Player thrower) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.1f);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.7f);

        try {
            world.spawnParticle(Particle.EXPLOSION, center, pc(2));
        } catch (Throwable t) {
            world.spawnParticle(Particle.SMOKE, center, pc(20), 0.5, 0.5, 0.5, 0.05);
        }
        world.spawnParticle(Particle.FLAME, center, pc(40), 0.8, 0.5, 0.8, 0.08);
        world.spawnParticle(Particle.LAVA, center, pc(8), 0.4, 0.2, 0.4, 0);

        double radius = plugin.getConfig().getDouble("grenade.incendiary.radius", 4.5);
        double damage = plugin.getConfig().getDouble("grenade.incendiary.damage", 4.0);
        double kbH = plugin.getConfig().getDouble("grenade.incendiary.knockback-horizontal", 2.2);
        double kbV = plugin.getConfig().getDouble("grenade.incendiary.knockback-vertical", 0.85);
        boolean selfDamage = plugin.getConfig().getBoolean("grenade.incendiary.self-damage", false);

        Match match = plugin.getMatchManager().getMatch();

        for (Player p : world.getPlayers()) {
            if (!plugin.getMatchManager().isInMatch(p)) {
                continue;
            }
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            PlayerSession s = match == null ? null : match.getSession(p.getUniqueId());
            if (s != null && !s.isAlive()) {
                continue;
            }

            double dist = p.getLocation().distance(center);
            if (dist > radius) {
                continue;
            }

            boolean isSelf = thrower != null && p.getUniqueId().equals(thrower.getUniqueId());
            if (!(isSelf && !selfDamage)) {
                double falloff = 1.0 - (dist / radius);
                p.damage(Math.max(1.0, damage * falloff), thrower);
            }

            Vector dir = p.getLocation().toVector().subtract(center.toVector());
            if (dir.lengthSquared() < 0.01) {
                dir = p.getLocation().getDirection().multiply(-1);
            }
            dir.normalize();
            double strength = Math.max(0.35, 1.0 - dist / radius);
            Vector kb = dir.multiply(kbH * strength).setY(kbV * (0.5 + 0.5 * strength));
            p.setVelocity(p.getVelocity().add(kb));
            p.setFallDistance(0f);
        }
    }
}
