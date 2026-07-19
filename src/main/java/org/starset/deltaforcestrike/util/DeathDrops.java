package org.starset.deltaforcestrike.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemManager;

/**
 * 伪死亡掉落：
 * 掉落：近战/远程/道具/改造TNT/箭
 * 不掉：护甲/技能/盾
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
            } else {
                loc.getWorld().dropItemNaturally(loc, stack.clone());
                inv.setItem(i, null);
            }
        }

        ItemStack off = inv.getItemInOffHand();
        if (!off.getType().isAir()) {
            if (shouldNotDrop(items, off)) {
                inv.setItemInOffHand(null);
            } else {
                loc.getWorld().dropItemNaturally(loc, off.clone());
                inv.setItemInOffHand(null);
            }
        }

        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);

        player.updateInventory();
    }

    /**
     * @return true = 不掉落，直接清除
     */
    public static boolean shouldNotDrop(ItemManager items, ItemStack stack) {
        if (stack == null) {
            return true;
        }

        Material mat = stack.getType();
        if (mat == Material.ARROW || mat == Material.SPECTRAL_ARROW || mat == Material.TIPPED_ARROW) {
            return false;
        }

        String type = items.getItemType(stack);
        if (type != null) {
            String t = type.toLowerCase();
            if (t.equals("armor") || t.equals("shield") || t.contains("skill")) {
                return true;
            }
            if (t.equals("melee") || t.equals("ranged") || t.equals("utility") || t.equals("bomb")) {
                return false;
            }
        }

        if (items.isShield(stack) || mat == Material.SHIELD) {
            return true;
        }

        String id = items.getItemId(stack);
        if (id != null && id.toLowerCase().contains("shield")) {
            return true;
        }

        // 锁链/铁/皮甲部件（无 type 时兜底）
        if (isArmorMaterial(mat)) {
            return true;
        }

        // 自定义物品默认掉落
        return false;
    }

    private static boolean isArmorMaterial(Material mat) {
        if (mat == null) {
            return false;
        }
        String n = mat.name();
        return n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS");
    }
}
