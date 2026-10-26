package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

import java.util.UUID;

/**
 * 布若可购技能：断后——滞留型迟缓区域。
 * 用受控任务替代 AreaEffectCloud：AEC 是真实实体且无法按队伍过滤，会跨回合残留。
 */
public class LingeringSlownessHandler implements SkillHandler {
    @Override public String id() { return "lingering_slowness"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        Location loc = p.getLocation();
        int duration = ctx.skill().getInt("duration-ticks", 120);
        float radius = (float) ctx.skill().getDouble("radius", 3.0);
        int amp = ctx.skill().getInt("amplifier", 0);

        // 投掷型：向前落点约 4 格
        Location center = loc.clone().add(loc.getDirection().multiply(3)).add(0, 0.2, 0);
        var world = center.getWorld();
        if (world == null) {
            return SkillResult.fail("无效位置");
        }

        DeltaForceStrike plugin = ctx.plugin();
        Match match = ctx.match();
        UUID ownerId = p.getUniqueId();

        BukkitRunnable task = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= duration) {
                    cancel();
                    plugin.getOperatorService().untrackEffectTask(this);
                    return;
                }
                if (t % 4 == 0) {
                    try {
                        world.spawnParticle(Particle.ENTITY_EFFECT, center,
                                8, radius * 0.5, 0.3, radius * 0.5, 0.0,
                                Color.GRAY);
                    } catch (Throwable ex) {
                        world.spawnParticle(Particle.CLOUD, center,
                                8, radius * 0.5, 0.3, radius * 0.5, 0.01);
                    }
                }
                if (t % 20 == 0) {
                    for (Player pl : world.getPlayers()) {
                        if (!plugin.getMatchManager().isInMatch(pl)) {
                            continue;
                        }
                        if (pl.getLocation().distanceSquared(center)
                                > radius * radius) {
                            continue;
                        }
                        if (pl.getUniqueId().equals(ownerId)) {
                            continue;
                        }
                        var gs = plugin.getGrenadeService();
                        if (gs != null && !gs.shouldAffect(pl, p)) {
                            continue;
                        }
                        var ms = match == null ? null : match.getSession(pl.getUniqueId());
                        if (ms != null && !ms.isAlive()) {
                            continue;
                        }
                        pl.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 40, amp, false, true, true));
                    }
                }
                t++;
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        plugin.getOperatorService().trackEffectTask(task);
        return SkillResult.ok("断后！");
    }
}
