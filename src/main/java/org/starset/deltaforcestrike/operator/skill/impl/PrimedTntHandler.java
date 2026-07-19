package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class PrimedTntHandler implements SkillHandler {
    @Override public String id() { return "primed_tnt"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        int fuse = ctx.skill().getInt("fuse-ticks", 40);
        float yield = (float) ctx.skill().getDouble("yield", 2.0);
        p.getWorld().spawn(p.getLocation(), TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(fuse);
            tnt.setYield(yield);
            tnt.setSource(p);
        });
        return SkillResult.ok("TNT！");
    }
}
