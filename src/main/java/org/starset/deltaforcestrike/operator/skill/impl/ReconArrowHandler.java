package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.operator.OperatorKeys;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class ReconArrowHandler implements SkillHandler {
    @Override public String id() { return "recon_arrow"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        AbstractArrow arrow = p.launchProjectile(org.bukkit.entity.Arrow.class);
        arrow.setShooter(p);
        arrow.setVelocity(p.getLocation().getDirection().multiply(2.2));
        try {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        } catch (Throwable ignored) {
        }
        arrow.getPersistentDataContainer().set(
                OperatorKeys.reconArrow(), PersistentDataType.BYTE, (byte) 1);
        arrow.getPersistentDataContainer().set(
                OperatorKeys.reconRadius(), PersistentDataType.DOUBLE,
                ctx.skill().getDouble("radius", 24.0));
        arrow.getPersistentDataContainer().set(
                OperatorKeys.reconGlow(), PersistentDataType.INTEGER,
                ctx.skill().getInt("glow-seconds", 3));
        return SkillResult.ok("侦查箭已射出！");
    }
}
