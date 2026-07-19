package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class WindChargeHandler implements SkillHandler {
    @Override public String id() { return "wind_charge"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        try {
            WindCharge wc = p.launchProjectile(WindCharge.class);
            wc.setVelocity(p.getLocation().getDirection().multiply(1.4));
            return SkillResult.ok("腾龙踏云！");
        } catch (Throwable t) {
            // 旧版本无 WindCharge：用雪球+击退近似
            var ball = p.launchProjectile(org.bukkit.entity.Snowball.class);
            ball.setVelocity(p.getLocation().getDirection().multiply(1.6));
            return SkillResult.ok("腾龙踏云！（兼容模式）");
        }
    }
}
