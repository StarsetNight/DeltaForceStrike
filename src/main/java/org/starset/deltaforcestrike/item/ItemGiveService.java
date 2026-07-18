package org.starset.deltaforcestrike.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.ItemPlacement;

public final class ItemGiveService {

    private final ItemManager itemManager;

    public ItemGiveService(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    /**
     * 按类型放到正确槽位。
     * 购买/指令发放：武器/炸弹可覆盖对应槽；道具只进空道具槽且不堆叠。
     */
    public boolean give(Player player, String id) {
        return give(player, id, true);
    }

    /**
     * @param replaceWeapons true=覆盖近战/远程/炸弹槽；false=仅空槽（拾取用可走 Placement）
     */
    public boolean give(Player player, String id, boolean replaceWeapons) {
        GameItem def = itemManager.getGameItem(id);
        if (def == null) {
            return false;
        }

        if (def.isArmorSet()) {
            return itemManager.giveArmorSet(player, id);
        }

        ItemStack stack = itemManager.createItem(id);
        if (stack == null) {
            return false;
        }
        stack.setAmount(1);

        String type = def.getType() == null ? "" : def.getType().toLowerCase();
        String slotHint = def.getSlot() == null ? "" : def.getSlot().toLowerCase();
        PlayerInventory inv = player.getInventory();

        // 盾 → 副手
        if ("shield".equals(type) || "shield".equals(slotHint) || "offhand".equals(slotHint)) {
            inv.setItemInOffHand(stack);
            return true;
        }

        int preferred = InventorySlots.preferredHotbarSlot(type, slotHint);

        // 道具 3–5：只进空槽，不堆叠
        if (preferred == InventorySlots.UTIL_1
                || "utility".equals(type)
                || "utility".equals(slotHint)) {
            int empty = ItemPlacement.findEmptyUtility(inv);
            if (empty < 0) {
                player.sendMessage("§c[DFS] 战术道具槽已满（最多3个，每格1个）。");
                return false;
            }
            return ItemPlacement.putInSlot(inv, empty, stack, itemManager, false);
        }

        // 精确热键
        if (preferred >= 0 && preferred <= 8) {
            if (!replaceWeapons) {
                ItemStack cur = inv.getItem(preferred);
                if (cur != null && !cur.getType().isAir()) {
                    return false;
                }
            }
            // 覆盖或空槽
            inv.setItem(preferred, stack);
            return true;
        }

        return false;
    }
}
