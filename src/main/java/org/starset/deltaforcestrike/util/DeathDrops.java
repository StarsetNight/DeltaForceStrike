package org.starset.deltaforcestrike.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemManager;

/**
 * 伪死亡（取消原版死亡进旁观）时手动掉落。
 * 掉落：武器 / 道具 / 改造TNT / 箭
 * 不掉：护甲 / 盾 / 技能
 */
public final class DeathDrops {

    private DeathDrops() {}

    public static void dropAndClearLoadout(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        ItemManager items = DeltaForceStrike.getInstance().getItemManager();
        Location loc = player.getLocation();
        if (loc.getWorld() == null) {
            return;
        }

        PlayerInventory inv = player.getInventory();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (shouldNotDrop(items, stack)) {
                inv.setItem(i, null);
                continue;
            }
            loc.getWorld().dropItemNaturally(loc, stack.clone());
            inv.setItem(i, null);
        }

        ItemStack off = inv.getItemInOffHand();
        if (off != null && !off.getType().isAir()) {
            if (!shouldNotDrop(items, off)) {
                loc.getWorld().dropItemNaturally(loc, off.clone());
            }
            inv.setItemInOffHand(null);
        }

        // 护甲一律不掉，直接清除
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);

        player.updateInventory();
    }

    /** true = 不掉落并销毁（护甲/盾/技能） */
    public static boolean shouldNotDrop(ItemManager items, ItemStack stack) {
        if (stack == null) {
            return true;
        }
        if (items.isShield(stack)) {
            return true;
        }
        String type = items.getItemType(stack);
        if (type != null) {
            String t = type.toLowerCase();
            if (t.equals("armor") || t.equals("shield") || t.contains("skill")) {
                return true;
            }
        }
        // undroppable 的盾/甲；改造TNT 可掉所以 false
        if (items.isUndroppable(stack) && items.isShield(stack)) {
            return true;
        }
        if (type != null && type.equalsIgnoreCase("armor")) {
            return true;
        }
        // 带 undroppable 的非武器：甲
        return items.isUndroppable(stack)
                && !"bomb".equalsIgnoreCase(type)
                && !"melee".equalsIgnoreCase(type)
                && !"ranged".equalsIgnoreCase(type)
                && !"utility".equalsIgnoreCase(type);
    }
}
