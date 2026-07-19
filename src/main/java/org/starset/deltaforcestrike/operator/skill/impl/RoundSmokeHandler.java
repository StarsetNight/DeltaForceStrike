package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.operator.OperatorKeys;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

/**
 * 艾尔购买技能：强效烟幕（投掷型）。
 * 使用独立 PDC（op_skill_projectile），绝不走 GrenadeService / 普通烟雾弹逻辑。
 * 落点由 OperatorSkillListener 处理，持续 duration-seconds（默认 90）。
 */
public class RoundSmokeHandler implements SkillHandler {

    public static final String PROJECTILE_ID = "round_smoke";
    public static final int DEFAULT_DURATION_SECONDS = 90;
    public static final double DEFAULT_RADIUS = 5.0;

    @Override
    public String id() {
        return "round_smoke";
    }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();

        double radius = ctx.skill().getDouble("radius", DEFAULT_RADIUS);
        int durationSec = ctx.skill().getInt("duration-seconds", DEFAULT_DURATION_SECONDS);
        if (durationSec <= 0) {
            durationSec = DEFAULT_DURATION_SECONDS;
        }
        if (radius <= 0) {
            radius = DEFAULT_RADIUS;
        }

        Snowball ball = p.launchProjectile(Snowball.class);
        ball.setShooter(p);
        ball.setGravity(true);
        ball.setVelocity(p.getLocation().getDirection().multiply(1.35));

        // 仅技能 PDC，禁止写入 GrenadeKeys
        var pdc = ball.getPersistentDataContainer();
        pdc.set(OperatorKeys.skillProjectile(), PersistentDataType.STRING, PROJECTILE_ID);
        pdc.set(OperatorKeys.skillOwner(), PersistentDataType.STRING, p.getUniqueId().toString());
        pdc.set(OperatorKeys.skillRadius(), PersistentDataType.DOUBLE, radius);
        pdc.set(OperatorKeys.skillDuration(), PersistentDataType.INTEGER, durationSec);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.85f, 0.85f);
        return SkillResult.ok("强效烟幕已投出（落地持续 " + durationSec + " 秒）");
    }
}
