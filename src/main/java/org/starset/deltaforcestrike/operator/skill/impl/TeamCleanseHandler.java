package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.operator.skill.PassiveSkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillContext;

public final class TeamCleanseHandler implements PassiveSkillHandler {
    @Override public String id() { return "team_cleanse"; }

    @Override
    public void tick(SkillContext ctx) {
        PlayerSession self = ctx.match().getSession(ctx.player().getUniqueId());
        if (self == null || !self.isAlive()) {
            return;
        }
        for (Player mate : ctx.match().onlinePlayers()) {
            PlayerSession session = ctx.match().getSession(mate.getUniqueId());
            if (session == null || session.getTeam() != self.getTeam() || !session.isAlive()) {
                continue;
            }
            for (PotionEffect effect : mate.getActivePotionEffects()) {
                if (isNegative(effect.getType())) {
                    mate.removePotionEffect(effect.getType());
                }
            }
        }
    }

    private boolean isNegative(PotionEffectType type) {
        if (type == null) {
            return false;
        }
        return type.equals(PotionEffectType.POISON)
                || type.equals(PotionEffectType.WITHER)
                || type.equals(PotionEffectType.SLOWNESS)
                || type.equals(PotionEffectType.MINING_FATIGUE)
                || type.equals(PotionEffectType.BLINDNESS)
                || type.equals(PotionEffectType.HUNGER)
                || type.equals(PotionEffectType.WEAKNESS)
                || type.equals(PotionEffectType.LEVITATION)
                || type.equals(PotionEffectType.UNLUCK)
                || type.equals(PotionEffectType.DARKNESS)
                || type.equals(PotionEffectType.INSTANT_DAMAGE);
    }
}
