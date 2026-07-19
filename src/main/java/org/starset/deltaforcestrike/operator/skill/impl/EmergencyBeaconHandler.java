package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.operator.OperatorLoadout;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

/**
 * 妮可购买技能：我不打了！
 * 第一次：放置信标（部署保护期内不会立刻传送）
 * 第二次：传送到信标并消耗
 */
public class EmergencyBeaconHandler implements SkillHandler {

    /** 部署后忽略再次触发的毫秒数（阻断同一次点击的二次检测） */
    public static final long DEPLOY_GRACE_MS = 750L;

    @Override
    public String id() {
        return "emergency_beacon";
    }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        OperatorLoadout load = ctx.loadout();

        // 部署保护：刚放下时不允许当作“传送”
        if (load.isBeaconDeployGrace()) {
            return SkillResult.fail("装置部署中，请稍后再传送");
        }

        // 已部署且不在保护期 → 传送
        if (load.isBeaconArmed() && load.getBeaconLocation() != null) {
            Location dest = load.getBeaconLocation().clone();
            if (dest.getWorld() == null) {
                load.clearBeacon();
                return SkillResult.fail("避险点已失效");
            }

            p.teleport(dest);
            dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            dest.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 50, 0.4, 0.8, 0.4, 0.15);

            load.clearBeacon();
            // 标记：传送完成，uses 由 OperatorService 扣到 0
            load.setBeaconTeleportConsumed(true);
            return SkillResult.ok("已传送至应急避险点（已消耗）");
        }

        // 第一次：仅放置
        Location feet = p.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
        load.setBeaconLocation(feet);
        load.setBeaconArmed(true);
        load.setBeaconTeleportConsumed(false);
        load.markBeaconJustDeployed(DEPLOY_GRACE_MS);

        feet.getWorld().spawnParticle(Particle.END_ROD, feet.clone().add(0, 1, 0), 30, 0.35, 0.6, 0.35, 0.02);
        feet.getWorld().playSound(feet, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
        p.sendMessage("§d[DFS] 应急避险装置已部署。§7再次右键技能可传送（仅一次）");

        return SkillResult.ok("已放置应急避险装置");
    }
}
