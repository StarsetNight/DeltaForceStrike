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
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.ItemPlacement;
import org.starset.deltaforcestrike.util.Worlds;

public class PickupListener implements Listener {

    private final DeltaForceStrike plugin;
    private final InventoryLockListener lockListener;

    public PickupListener(DeltaForceStrike plugin, InventoryLockListener lockListener) {
        this.plugin = plugin;
        this.lockListener = lockListener;
    }

    private boolean shouldHandle(Player player) {
        if (!Worlds.isArena(player)) {
            return false;
        }
        if (!plugin.getMatchManager().isInMatch(player)) {
            return false;
        }
        Match m = plugin.getMatchManager().getMatch();
        return m != null && m.getState() == MatchState.IN_PROGRESS;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!shouldHandle(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();

        // 盾 → 副手
        if (items.isShield(stack)) {
            event.setCancelled(true);
            if (isEmpty(player.getInventory().getItemInOffHand())) {
                ItemStack one = stack.clone();
                one.setAmount(1);
                player.getInventory().setItemInOffHand(one);
                shrinkGround(entity, stack, 1);
            }
            runSanitize(player);
            return;
        }

        String id = items.getItemId(stack);
        String type = items.getItemType(stack);

        // 改造 TNT：仅 T 可捡进槽 2；CT 静默禁止
        if (isBomb(items, stack, id, type)) {
            event.setCancelled(true);

            Match match = plugin.getMatchManager().getMatch();
            PlayerSession session = match == null ? null : match.getSession(player.getUniqueId());
            if (session == null || session.getTeam() != Team.T) {
                return;
            }

            PlayerInventory inv = player.getInventory();
            ItemStack slot2 = inv.getItem(InventorySlots.BOMB);
            if (!isEmpty(slot2)) {
                return;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            inv.setItem(InventorySlots.BOMB, one);
            shrinkGround(entity, stack, 1);
            runSanitize(player);
            return;
        }

        // 原版箭：允许正常拾取进背包（不拦截）
        if (stack.getType() == org.bukkit.Material.ARROW
                || stack.getType() == org.bukkit.Material.SPECTRAL_ARROW
                || stack.getType() == org.bukkit.Material.TIPPED_ARROW) {
            return;
        }

        if (id != null) {
            event.setCancelled(true);
            ItemStack one = stack.clone();
            one.setAmount(1);
            if (tryGiveToFixedSlot(player, items, one)) {
                shrinkGround(entity, stack, 1);
            }
            runSanitize(player);
            return;
        }

        // 其它非自定义：禁止
        event.setCancelled(true);
    }

    private void runSanitize(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> lockListener.sanitizeAll(player));
    }

    private boolean isBomb(ItemManager items, ItemStack stack, String id, String type) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if ("bomb".equalsIgnoreCase(type)) {
            return true;
        }
        if (id != null && id.contains("plant-bomb")) {
            return true;
        }
        return stack.getType() == org.bukkit.Material.TNT && id != null;
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
            return ItemPlacement.putInSlot(inv, preferred, stack, items, false);
        }
        return false;
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

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }
}
