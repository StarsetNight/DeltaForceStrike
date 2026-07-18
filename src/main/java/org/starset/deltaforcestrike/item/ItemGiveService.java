package org.starset.deltaforcestrike.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.ItemPlacement;

public final class ItemGiveService {

    /** 弓弩标准备弹量 */
    public static final int ARROWS_WITH_RANGED = 15;

    private final ItemManager itemManager;

    public ItemGiveService(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    public boolean give(Player player, String id) {
        return give(player, id, true);
    }

    /**
     * @param replaceWeapons true=覆盖近战/远程/炸弹（商店/指令）
     */
    public boolean give(Player player, String id, boolean replaceWeapons) {
        GameItem def = itemManager.getGameItem(id);
        if (def == null) {
            return false;
        }

        // 护甲套装
        if (def.isArmorSet()) {
            boolean ok = itemManager.giveArmorSet(player, id);
            if (ok && isIronArmorId(id, def)) {
                giveShieldToOffhand(player);
            }
            return ok;
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

        // 道具 3–5
        if (preferred == InventorySlots.UTIL_1
                || "utility".equals(type)
                || "utility".equals(slotHint)) {
            int empty = ItemPlacement.findEmptyUtility(inv);
            if (empty < 0) {
                player.sendMessage("§c[DFS] 战术道具槽已满（最多3个）。");
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
            inv.setItem(preferred, stack);

            // 远程：补满 15 箭（进背包，不叠成无限）
            if (isRangedWeapon(type, slotHint, stack.getType())) {
                refillArrowsTo(player, ARROWS_WITH_RANGED);
            }
            return true;
        }

        return false;
    }

    private boolean isIronArmorId(String id, GameItem def) {
        String check = ((id == null ? "" : id) + " " + (def.getId() == null ? "" : def.getId()))
                .toLowerCase();
        return check.contains("iron-armor") || check.contains("iron_armor");
    }

    private void giveShieldToOffhand(Player player) {
        ItemStack shield = itemManager.createItem("shield");
        if (shield == null) {
            shield = itemManager.createItem("equipments.shield");
        }
        if (shield != null) {
            player.getInventory().setItemInOffHand(shield);
            player.sendMessage("§b[DFS] 重型护甲附带 §f守护 §b盾牌。");
        }
    }

    private boolean isRangedWeapon(String type, String slotHint, Material mat) {
        if ("ranged".equalsIgnoreCase(type) || "ranged".equalsIgnoreCase(slotHint)) {
            return true;
        }
        return mat == Material.BOW || mat == Material.CROSSBOW;
    }

    public boolean hasRangedWeapon(Player player) {
        ItemStack ranged = player.getInventory().getItem(InventorySlots.RANGED);
        if (ranged == null || ranged.getType().isAir()) {
            return false;
        }
        String type = itemManager.getItemType(ranged);
        if ("ranged".equalsIgnoreCase(type)) {
            return true;
        }
        Material m = ranged.getType();
        return m == Material.BOW || m == Material.CROSSBOW;
    }

    public int countArrows(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.ARROW) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    /**
     * 将箭补到目标总数（只增加不删减多余）。
     * 优先合并已有箭堆，再放入主背包 9–35。
     */
    public void refillArrowsTo(Player player, int targetTotal) {
        int have = countArrows(player);
        if (have >= targetTotal) {
            return;
        }
        giveArrows(player, targetTotal - have);
    }

    /**
     * 向背包添加箭（不优先热键 0–2）。
     */
    public void giveArrows(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        int remain = amount;

        // 1) 合并已有箭
        for (int i = 0; i < inv.getSize() && remain > 0; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.getType() != Material.ARROW) {
                continue;
            }
            int can = cur.getMaxStackSize() - cur.getAmount();
            if (can <= 0) {
                continue;
            }
            int add = Math.min(can, remain);
            cur.setAmount(cur.getAmount() + add);
            inv.setItem(i, cur);
            remain -= add;
        }

        // 2) 主背包空位 9–35
        for (int i = 9; i <= 35 && remain > 0; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur != null && !cur.getType().isAir()) {
                continue;
            }
            int put = Math.min(64, remain);
            inv.setItem(i, new ItemStack(Material.ARROW, put));
            remain -= put;
        }

        // 3) 热键 3–8 空位（不占 0 近战 / 1 远程 / 2 包）
        for (int i = 3; i <= 8 && remain > 0; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur != null && !cur.getType().isAir()) {
                continue;
            }
            int put = Math.min(64, remain);
            inv.setItem(i, new ItemStack(Material.ARROW, put));
            remain -= put;
        }

        // 4) 仍满：掉落
        if (remain > 0) {
            player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    new ItemStack(Material.ARROW, remain)
            );
        }
    }
}
