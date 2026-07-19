package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class LingeringSlownessHandler implements SkillHandler {
    @Override public String id() { return "lingering_slowness"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        Location loc = p.getLocation();
        int duration = ctx.skill().getInt("duration-ticks", 120);
        float radius = (float) ctx.skill().getDouble("radius", 3.0);
        int amp = ctx.skill().getInt("amplifier", 0);

        // 投掷型：向前落点约 4 格
        Location center = loc.clone().add(loc.getDirection().multiply(3)).add(0, 0.2, 0);
        p.getWorld().spawn(center, AreaEffectCloud.class, c -> {
            c.setRadius(radius);
            c.setDuration(duration);
            c.setSource(p);
            c.setRadiusPerTick(0f);
            try {
                c.setParticle(Particle.ENTITY_EFFECT, Color.GRAY);
            } catch (Throwable t) {
                c.setParticle(Particle.CLOUD);
            }
            c.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, amp), true);
        });
        return SkillResult.ok("断后！");
    }
}
