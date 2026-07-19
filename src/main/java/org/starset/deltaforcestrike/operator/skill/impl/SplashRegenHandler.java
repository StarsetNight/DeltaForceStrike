package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class SplashRegenHandler implements SkillHandler {
    @Override public String id() { return "splash_regen"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        int amp = ctx.skill().getInt("potion-amplifier", 1);
        int dur = ctx.skill().getInt("potion-duration-ticks", 440);
        ItemStack pot = new ItemStack(org.bukkit.Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) pot.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, dur, amp), true);
        meta.setColor(org.bukkit.Color.FUCHSIA);
        pot.setItemMeta(meta);
        ThrownPotion thrown = p.launchProjectile(ThrownPotion.class);
        thrown.setItem(pot);
        return SkillResult.ok("生命恩典！");
    }
}
