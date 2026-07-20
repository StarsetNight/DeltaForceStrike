package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.scheduler.BukkitRunnable;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 艾尔大招：召唤恼鬼，仇恨仅锁定敌方存活玩家。
 */
public class SummonVexHandler implements SkillHandler {

    @Override
    public String id() {
        return "summon_vex";
    }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        Match match = ctx.match();
        if (match == null) {
            return SkillResult.fail("无对局");
        }

        PlayerSession self = match.getSession(p.getUniqueId());
        if (self == null) {
            return SkillResult.fail("无会话");
        }

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
        UUID ownerId = p.getUniqueId();
        var ownerTeam = self.getTeam();

        List<Vex> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vex vex = (Vex) p.getWorld().spawnEntity(
                    p.getLocation().add(i * 0.5, 1, 0), EntityType.VEX);
            AttributeInstance maxHealth = vex.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(hp);
                vex.setHealth(Math.min(hp, maxHealth.getValue()));
            }
            try {
                vex.setRemoveWhenFarAway(true);
            } catch (Throwable ignored) {
            }
            spawned.add(vex);
        }

        // 每 10 tick 强制仇恨 → 最近敌方玩家；误锁友方/非玩家则纠正
        DeltaForceStrike plugin = ctx.plugin();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 10;
                if (ticks > 20 * 60) {
                    for (Vex v : spawned) {
                        if (v.isValid()) {
                            v.remove();
                        }
                    }
                    cancel();
                    return;
                }
                Match m = plugin.getMatchManager().getMatch();
                if (m == null) {
                    cancel();
                    return;
                }
                boolean anyAlive = false;
                for (Vex vex : spawned) {
                    if (!vex.isValid() || vex.isDead()) {
                        continue;
                    }
                    anyAlive = true;
                    Player target = nearestEnemy(m, ownerId, ownerTeam, vex);
                    if (target != null) {
                        if (!(vex.getTarget() instanceof Player cur)
                                || !cur.getUniqueId().equals(target.getUniqueId())) {
                            vex.setTarget(target);
                        }
                    } else {
                        // 无合法敌人：清空仇恨，避免打友方/动物
                        if (vex.getTarget() != null) {
                            vex.setTarget(null);
                        }
                    }
                }
                if (!anyAlive) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);

        return SkillResult.ok("亡灵已召唤！（仅攻击敌方）");
    }

    private static Player nearestEnemy(Match match, UUID ownerId,
                                       org.starset.deltaforcestrike.match.Team ownerTeam,
                                       Vex vex) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player other : match.onlinePlayers()) {
            if (other.getUniqueId().equals(ownerId)) {
                continue;
            }
            PlayerSession os = match.getSession(other.getUniqueId());
            if (os == null || !os.isAlive() || !os.isConnected()) {
                continue;
            }
            if (os.getTeam() == ownerTeam) {
                continue;
            }
            if (other.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double d = other.getLocation().distanceSquared(vex.getLocation());
            if (d < bestD) {
                bestD = d;
                best = other;
            }
        }
        return best;
    }
}
