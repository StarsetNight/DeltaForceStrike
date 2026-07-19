package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class SummonVexHandler implements SkillHandler {
    @Override public String id() { return "summon_vex"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        Match match = ctx.match();
        if (match == null) return SkillResult.fail("无对局");

        PlayerSession self = match.getSession(p.getUniqueId());
        if (self == null) return SkillResult.fail("无会话");

        boolean requireDowned = ctx.skill().getBoolean("require-downed-teammate", true);
        if (requireDowned) {
            boolean anyDown = match.getSessions().values().stream()
                    .anyMatch(s -> s.getTeam() == self.getTeam() && s.isConnected() && !s.isAlive());
            if (!anyDown) {
                return SkillResult.fail("需要有队友被击倒才能召唤亡灵");
            }
        }

        int count = ctx.skill().getInt("count", 2);
        double hp = Math.max(0.1, ctx.skill().getDouble("health", 1.0));

        Player nearestEnemy = null;
        double best = Double.MAX_VALUE;
        for (Player other : match.onlinePlayers()) {
            PlayerSession os = match.getSession(other.getUniqueId());
            if (os == null || !os.isAlive() || os.getTeam() == self.getTeam()) continue;
            double d = other.getLocation().distanceSquared(p.getLocation());
            if (d < best) {
                best = d;
                nearestEnemy = other;
            }
        }

        for (int i = 0; i < count; i++) {
            Vex vex = (Vex) p.getWorld().spawnEntity(p.getLocation().add(i * 0.5, 1, 0), EntityType.VEX);
            AttributeInstance maxHealth = vex.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(hp);
                vex.setHealth(Math.min(hp, maxHealth.getValue()));
            }
            if (nearestEnemy != null) {
                vex.setTarget(nearestEnemy);
            }
        }
        return SkillResult.ok("亡灵已召唤！");
    }
}
