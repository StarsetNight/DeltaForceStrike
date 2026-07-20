package org.starset.deltaforcestrike.listener;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.operator.OperatorService;
import org.starset.deltaforcestrike.operator.SkillKind;
import org.starset.deltaforcestrike.util.ConfigKeys;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.ItemPlacement;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.Locale;

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
        OperatorService ops = plugin.getOperatorService();
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();

        // 箭 → 主背包
        if (stack.getType() == Material.ARROW
                || stack.getType() == Material.SPECTRAL_ARROW
                || stack.getType() == Material.TIPPED_ARROW) {
            event.setCancelled(true);
            int moved = putArrowsInStorage(player, stack);
            if (moved > 0) {
                shrinkGround(entity, stack, moved);
            }
            return;
        }

        // 技能物品 → 按 kind 回 7/8/9
        if (ops != null && ops.isSkillStack(stack)) {
            event.setCancelled(true);
            SkillKind kind = ops.getSkillKind(stack);
            if (kind == null) {
                return;
            }
            int slot = switch (kind) {
                case SIGNATURE -> InventorySlots.SIGNATURE;
                case PURCHASABLE -> InventorySlots.PURCHASABLE;
                case ULTIMATE -> InventorySlots.ULTIMATE;
                default -> -1;
            };
            if (slot < 0) {
                return;
            }
            ItemStack cur = player.getInventory().getItem(slot);
            if (cur != null && !cur.getType().isAir()) {
                // 槽已有技能：不捡
                return;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            player.getInventory().setItem(slot, one);
            shrinkGround(entity, stack, 1);
            return;
        }

        // 盾
        if (items.isShield(stack) || stack.getType() == Material.SHIELD) {
            event.setCancelled(true);
            if (!ConfigKeys.shieldEnabled()) {
                entity.remove();
                return;
            }
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
        String action = getAction(stack);

        if (isBomb(items, stack, id, type, action)) {
            event.setCancelled(true);
            Match match = plugin.getMatchManager().getMatch();
            PlayerSession session = match == null ? null : match.getSession(player.getUniqueId());
            if (session == null || session.getTeam() != Team.T) {
                return;
            }
            PlayerInventory inv = player.getInventory();
            if (!isEmpty(inv.getItem(InventorySlots.BOMB))) {
                return;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            inv.setItem(InventorySlots.BOMB, one);
            shrinkGround(entity, stack, 1);
            runSanitize(player);
            return;
        }

        if (isDefuseKit(items, stack, id, type, action)) {
            event.setCancelled(true);
            Match match = plugin.getMatchManager().getMatch();
            PlayerSession session = match == null ? null : match.getSession(player.getUniqueId());
            if (session == null || session.getTeam() != Team.CT) {
                return;
            }
            PlayerInventory inv = player.getInventory();
            if (!isEmpty(inv.getItem(InventorySlots.BOMB))) {
                return;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            inv.setItem(InventorySlots.BOMB, one);
            shrinkGround(entity, stack, 1);
            runSanitize(player);
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

        event.setCancelled(true);
    }

    private String getAction(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.action(), PersistentDataType.STRING);
    }

    private boolean isBomb(ItemManager items, ItemStack stack, String id, String type, String action) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if ("defuse".equalsIgnoreCase(action) || "defuse".equalsIgnoreCase(type)) {
            return false;
        }
        if ("plant".equalsIgnoreCase(action) || "bomb".equalsIgnoreCase(type)) {
            return true;
        }
        return id != null && id.contains("plant-bomb");
    }

    private boolean isDefuseKit(ItemManager items, ItemStack stack, String id, String type, String action) {
        if (stack == null) {
            return false;
        }
        if ("defuse".equalsIgnoreCase(action) || "defuse".equalsIgnoreCase(type)) {
            return true;
        }
        return id != null && id.toLowerCase(Locale.ROOT).contains("defuse");
    }

    private int putArrowsInStorage(Player player, ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return 0;
        }
        Material mat = source.getType();
        int remain = source.getAmount();
        PlayerInventory inv = player.getInventory();
        int moved = 0;

        for (int i = 9; i <= 35 && remain > 0; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.getType() != mat) {
                continue;
            }
            if (mat == Material.TIPPED_ARROW && !cur.isSimilar(source)) {
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
            moved += add;
        }
        for (int i = 9; i <= 35 && remain > 0; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur != null && !cur.getType().isAir()) {
                continue;
            }
            int put = Math.min(mat.getMaxStackSize(), remain);
            ItemStack one = source.clone();
            one.setAmount(put);
            inv.setItem(i, one);
            remain -= put;
            moved += put;
        }
        return moved;
    }

    private void runSanitize(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> lockListener.sanitizeAll(player));
    }

    /**
     * 兜底：射击过的箭落到地上、玩家走进来拾取，触发 PlayerPickupArrowEvent
     * （而非 EntityPickupItemEvent）。否则跳过对局内锁定和槽位规范。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();
        if (!shouldHandle(player)) {
            return;
        }

        // 取消默认行为（直接进物品栏），按对局规则重新放置
        event.setCancelled(true);

        Item item = event.getItem();
        ItemStack stack = item == null ? null : item.getItemStack();
        if (isEmpty(stack)) {
            if (item != null) {
                item.remove();
            }
            // 同时移除埋地方块上的 Arrow 实体
            try {
                event.getArrow().remove();
            } catch (Throwable ignored) {
            }
            return;
        }

        int moved = putArrowsInStorage(player, stack);
        if (moved > 0) {
            shrinkGround(item, stack, moved);
            // 全部回收后清理 Arrow 实体，避免残留在地上
            if (isEmpty(stack) || stack.getAmount() <= 0) {
                try {
                    event.getArrow().remove();
                } catch (Throwable ignored) {
                }
            }
        } else {
            // 背包满了：保留箭体在地上，玩家可见；不交给 vanilla 处理
        }
        runSanitize(player);
    }

    private boolean tryGiveToFixedSlot(Player player, ItemManager items, ItemStack stack) {
        String type = items.getItemType(stack);
        String slotHint = "";
        String id = items.getItemId(stack);
        String action = getAction(stack);
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

        if (isDefuseKit(items, stack, id, type, action) || isBomb(items, stack, id, type, action)
                || "bomb".equalsIgnoreCase(slotHint)) {
            if (!isEmpty(inv.getItem(InventorySlots.BOMB))) {
                return false;
            }
            inv.setItem(InventorySlots.BOMB, stack);
            return true;
        }

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
        if (preferred >= 0 && preferred <= 5) {
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
