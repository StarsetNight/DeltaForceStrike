package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.starset.deltaforcestrike.operator.skill.PassiveSkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillContext;

public final class SneakSpeedHandler implements PassiveSkillHandler {
    @Override public String id() { return "sneak_speed"; }

    @Override
    public boolean apply(SkillContext ctx) {
        if (ctx.player().isSneaking()) {
            int amp = ctx.skill().getInt("amplifier", 1);
            ctx.player().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, amp, false, false, true));
        }
        return true;
    }
}
