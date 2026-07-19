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

public class SplashHarmingHandler implements SkillHandler {
    @Override public String id() { return "splash_harming"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        Player p = ctx.player();
        int amp = ctx.skill().getInt("potion-amplifier", 0);
        ItemStack pot = new ItemStack(org.bukkit.Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) pot.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, amp), true);
        meta.setColor(org.bukkit.Color.PURPLE);
        pot.setItemMeta(meta);
        ThrownPotion thrown = p.launchProjectile(ThrownPotion.class);
        thrown.setItem(pot);
        return SkillResult.ok("老贝榨！");
    }
}
