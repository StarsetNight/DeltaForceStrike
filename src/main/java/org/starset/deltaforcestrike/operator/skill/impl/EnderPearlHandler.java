package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class EnderPearlHandler implements SkillHandler {
    @Override public String id() { return "ender_pearl"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        EnderPearl pearl = p.launchProjectile(EnderPearl.class);
        pearl.setShooter(p);
        pearl.setVelocity(p.getLocation().getDirection().multiply(1.5));
        return SkillResult.ok("DASH！");
    }
}
