package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

/**
 * 妮可招牌：可控前向突进（非末影珍珠）。
 * 以水平位移为主，严格限制竖直分量，避免上房顶。
 * operators.yml: power / max-y / max-distance
 */
public class DashHandler implements SkillHandler {

    @Override
    public String id() {
        return "dash";
    }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        double power = ctx.skill().getDouble("power", 1.65);
        // 竖直速度上限（正/负），防止飞屋顶
        double maxY = ctx.skill().getDouble("max-y", 0.28);
        double maxDist = ctx.skill().getDouble("max-distance", 6.5);

        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection();
        // 水平方向（可略带俯仰，但 Y 会被 cap）
        Vector horiz = new Vector(look.getX(), 0, look.getZ());
        if (horiz.lengthSquared() < 1.0e-4) {
            // 完全抬头/低头时用面向 yaw
            float yaw = p.getLocation().getYaw();
            double rad = Math.toRadians(yaw);
            horiz = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        }
        horiz.normalize();

        // 轻微跟随俯仰，但最终 Y 被 clamp
        double pitchFactor = Math.max(-0.2, Math.min(0.35, look.getY() * 0.35));
        Vector vel = horiz.multiply(power);
        vel.setY(Math.max(-maxY * 0.5, Math.min(maxY, pitchFactor * power)));

        // 前方短距离无方块阻挡时再给速度（防穿墙）
        Location feet = p.getLocation();
        Vector step = horiz.clone().multiply(0.4);
        double traveled = 0;
        Location probe = feet.clone().add(0, 0.2, 0);
        while (traveled < maxDist) {
            probe.add(step);
            traveled += step.length();
            if (probe.getBlock().getType().isSolid()
                    || probe.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                // 撞墙：略微减弱
                vel.multiply(0.45);
                break;
            }
        }

        p.setVelocity(vel);
        p.setFallDistance(0f);

        // 落地后压低残留竖直速度，进一步防爬升
        var plugin = ctx.plugin();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Vector v = p.getVelocity();
            if (v.getY() > maxY) {
                v.setY(maxY);
                p.setVelocity(v);
            }
        }, 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Vector v = p.getVelocity();
            if (v.getY() > 0.15) {
                v.setY(0.08);
                p.setVelocity(v);
            }
            p.setFallDistance(0f);
        }, 6L);

        var world = p.getWorld();
        if (world != null) {
            world.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.35f);
            try {
                world.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.5f, 1.6f);
            } catch (Throwable t) {
                world.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.4f, 1.8f);
            }
            world.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.5, 0),
                    12, 0.3, 0.15, 0.3, 0.02);
            world.spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 1);
        }

        return SkillResult.ok("DASH！");
    }
}
