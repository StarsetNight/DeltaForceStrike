package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.starset.deltaforcestrike.operator.PotionSpec;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class NikoBerserkHandler implements SkillHandler {
    @Override public String id() { return "niko_berserk"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        int duration = ctx.skill().getInt("duration-seconds", 0);
        int ticks = duration <= 0 ? 20 * 60 * 10 : duration * 20; // 回合内：给很长，回合结束清

        for (PotionSpec spec : ctx.skill().getEffects()) {
            p.addPotionEffect(new PotionEffect(spec.type(), ticks, spec.amplifier(), false, true, true));
        }
        // 默认套装若 effects 空
        if (ctx.skill().getEffects().isEmpty()) {
            p.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, ticks, 1));
            p.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, ticks, 0));
            p.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, ticks, 0));
            p.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, ticks, 0));
        }
        ctx.loadout().setUltimateActiveThisRound(true);
        return SkillResult.ok("狂起来吧！");
    }
}
