package org.starset.deltaforcestrike.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.ItemPlacement;

public class PickupListener implements Listener {

    private final DeltaForceStrike plugin;
    private final InventoryLockListener lockListener;
    private boolean alwaysLock = true;

    public PickupListener(DeltaForceStrike plugin, InventoryLockListener lockListener) {
        this.plugin = plugin;
        this.lockListener = lockListener;
    }

    public void setAlwaysLock(boolean alwaysLock) {
        this.alwaysLock = alwaysLock;
    }

    private boolean shouldLock(Player player) {
        if (alwaysLock) {
            return true;
        }
        return plugin.getGameManager().getMatchManager().isInMatch(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!shouldLock(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();

        // 盾 → 仅副手，一次 1 个
        if (items.isShield(stack)) {
            event.setCancelled(true);
            if (isEmpty(player.getInventory().getItemInOffHand())) {
                ItemStack one = stack.clone();
                one.setAmount(1);
                player.getInventory().setItemInOffHand(one);
                shrinkGround(entity, stack, 1);
                player.sendMessage("§a[DFS] 盾牌已装备到副手。");
            } else {
                player.sendMessage("§c[DFS] 副手已有物品，无法拾取盾牌。");
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> lockListener.sanitizeAll(player));
            return;
        }

        String id = items.getItemId(stack);
        if (id != null) {
            event.setCancelled(true);

            ItemStack one = stack.clone();
            one.setAmount(1);

            if (tryGiveToFixedSlot(player, items, one)) {
                shrinkGround(entity, stack, 1);
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> lockListener.sanitizeAll(player));
            return;
        }

        // 非本插件物品：锁定期禁止
        event.setCancelled(true);
    }

    private void shrinkGround(Item entity, ItemStack stack, int taken) {
        int left = stack.getAmount() - taken;
        if (left <= 0) {
            entity.remove();
        } else {
            stack.setAmount(left);
            entity.setItemStack(stack);
        }
    }

    private boolean tryGiveToFixedSlot(Player player, ItemManager items, ItemStack stack) {
        String type = items.getItemType(stack);
        String slotHint = "";
        String id = items.getItemId(stack);
        if (id != null) {
            var gi = items.getGameItem(id);
            if (gi != null) {
                if (gi.getSlot() != null) {
                    slotHint = gi.getSlot();
                }
                if (type == null) {
                    type = gi.getType();
                }
            }
        }

        PlayerInventory inv = player.getInventory();
        int preferred = InventorySlots.preferredHotbarSlot(type, slotHint);

        if (preferred == InventorySlots.UTIL_1
                || "utility".equalsIgnoreCase(type)
                || "utility".equalsIgnoreCase(slotHint)) {
            int empty = ItemPlacement.findEmptyUtility(inv);
            if (empty < 0) {
                return false;
            }
            return ItemPlacement.putInSlot(inv, empty, stack, items, false);
        }

        if (preferred >= 0 && preferred <= 8) {
            // 拾取：仅空槽，不覆盖、不堆叠
            return ItemPlacement.putInSlot(inv, preferred, stack, items, false);
        }

        return false;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }
}
