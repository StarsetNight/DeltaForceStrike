package org.starset.deltaforcestrike.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;

public final class ItemPlacement {

    private ItemPlacement() {}

    public static boolean isNonStackable(ItemManager items, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (stack.getMaxStackSize() <= 1) {
            return true;
        }
        if (stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.instanceId(), PersistentDataType.STRING)) {
            return true;
        }
        String type = items.getItemType(stack);
        if (type == null) {
            return false;
        }
        type = type.toLowerCase();
        return type.equals("utility")
                || type.equals("bomb")
                || type.equals("shield")
                || type.equals("melee")
                || type.equals("ranged")
                || type.contains("skill");
    }

    /**
     * @param allowMerge 仅对可堆叠物有效；竞技自定义物应始终 false
     */
    public static boolean putInSlot(PlayerInventory inv, int slot, ItemStack stack,
                                    ItemManager items, boolean allowMerge) {
        if (stack == null || stack.getType().isAir()) {
            return true;
        }

        if (isNonStackable(items, stack) && stack.getAmount() != 1) {
            stack.setAmount(1);
        }

        ItemStack cur = inv.getItem(slot);
        boolean empty = cur == null || cur.getType().isAir() || cur.getAmount() <= 0;

        if (isNonStackable(items, stack) || !allowMerge) {
            if (!empty) {
                return false;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            inv.setItem(slot, one);
            return true;
        }

        if (empty) {
            inv.setItem(slot, stack);
            return true;
        }
        if (cur.isSimilar(stack) && cur.getAmount() < cur.getMaxStackSize()) {
            int can = cur.getMaxStackSize() - cur.getAmount();
            int add = Math.min(can, stack.getAmount());
            cur.setAmount(cur.getAmount() + add);
            stack.setAmount(stack.getAmount() - add);
            inv.setItem(slot, cur);
            return stack.getAmount() <= 0;
        }
        return false;
    }

    public static int findEmptyUtility(PlayerInventory inv) {
        for (int s = InventorySlots.UTIL_1; s <= InventorySlots.UTIL_3; s++) {
            ItemStack cur = inv.getItem(s);
            if (cur == null || cur.getType().isAir()) {
                return s;
            }
        }
        return -1;
    }

    public static int countUtility(PlayerInventory inv) {
        int n = 0;
        for (int s = InventorySlots.UTIL_1; s <= InventorySlots.UTIL_3; s++) {
            ItemStack cur = inv.getItem(s);
            if (cur != null && !cur.getType().isAir()) {
                n++;
            }
        }
        return n;
    }
}
