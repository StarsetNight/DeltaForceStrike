package org.starset.deltaforcestrike.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.ItemPlacement;
import org.starset.deltaforcestrike.util.Worlds;

import java.util.Locale;
import java.util.UUID;

public class InventoryLockListener implements Listener {

    private final DeltaForceStrike plugin;
    private boolean alwaysLock = true;

    public InventoryLockListener(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void setAlwaysLock(boolean alwaysLock) {
        this.alwaysLock = alwaysLock;
    }

    public boolean shouldLock(Player player) {
        if (!Worlds.isArena(player)) return false;
        if (!plugin.getMatchManager().isInMatch(player)) return false;
        Match match = plugin.getMatchManager().getMatch();
        if (match == null) return false;
        // 仅正式对局锁物品栏；排队期不锁（像大厅）
        return match.getState() == MatchState.IN_PROGRESS
                || match.getState() == MatchState.AGENT_SELECT;
    }


    // =====================================================================
    // 点击
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!shouldLock(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        Inventory bottom = event.getView().getBottomInventory();
        Inventory clicked = event.getClickedInventory();
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (!(bottom instanceof PlayerInventory playerInv)) {
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            if (handleNumberKey(event, player, items, clicked, bottom, playerInv)) {
                return;
            }
        }

        boolean clickOnPlayerInv = clicked != null && clicked.equals(bottom);

        if (clickOnPlayerInv) {
            int slot = event.getSlot();

            if (slot == 40) {
                if (!allowOffhandClick(items, cursor, current, event.getAction())) {
                    event.setCancelled(true);
                    deny(player, "副手只能装备盾牌，且盾牌不可移出。");
                    forceShieldOffhandNextTick(player);
                    return;
                }
            }

            if (slot >= 36 && slot <= 39) {
                if (!isEmpty(current) || !isEmpty(cursor)) {
                    event.setCancelled(true);
                    deny(player, "护甲不可手动移动，请通过商店购买。");
                    return;
                }
            }

            if (isStorageSlot(slot)) {
                if (isPlacingOrSwap(event.getAction()) && isCustomItem(items, cursor)) {
                    event.setCancelled(true);
                    deny(player, "自定义装备只能放在固定热键槽，不能放进背包。");
                    return;
                }
                if (isPlacingOrSwap(event.getAction()) && isShield(items, cursor)) {
                    event.setCancelled(true);
                    deny(player, "盾牌只能放在副手。");
                    return;
                }
                if (event.isShiftClick() && (isCustomItem(items, current) || isShield(items, current))) {
                    event.setCancelled(true);
                    deny(player, "请手动将物品放到对应固定槽，禁止 Shift。");
                    return;
                }
            }

            if (InventorySlots.isHotbar(slot)) {
                if (InventorySlots.isLockedSkillSlot(slot)) {
                    event.setCancelled(true);
                    deny(player, "技能槽已锁定。");
                    return;
                }
                if (isPlacingOrSwap(event.getAction()) && !isEmpty(cursor)) {
                    if (isShield(items, cursor)) {
                        event.setCancelled(true);
                        deny(player, "盾牌只能放在副手。");
                        forceShieldOffhandNextTick(player);
                        return;
                    }
                    // 道具槽已有物品时禁止把另一件叠/换进去（非空则仅允许 swap 同类？直接禁止堆叠：cursor amount>1 或目标非空且 similar）
                    if (isCustomItem(items, cursor) && !canPlaceInHotbar(items, cursor, slot)) {
                        event.setCancelled(true);
                        deny(player, "该物品不能放在此槽位。");
                        return;
                    }
                    // 禁止往已有非堆叠物的格子「合并」
                    if (!isEmpty(current)
                            && ItemPlacement.isNonStackable(items, cursor)
                            && ItemPlacement.isNonStackable(items, current)
                            && event.getAction() != InventoryAction.SWAP_WITH_CURSOR) {
                        // PLACE 到非空格会变成合并尝试
                        if (event.getAction() == InventoryAction.PLACE_ALL
                                || event.getAction() == InventoryAction.PLACE_ONE
                                || event.getAction() == InventoryAction.PLACE_SOME) {
                            event.setCancelled(true);
                            deny(player, "该槽已有物品，每格仅限 1 个。");
                            return;
                        }
                    }
                }
                if (event.isShiftClick() && isCustomItem(items, current)) {
                    event.setCancelled(true);
                    deny(player, "自定义物品不能移入背包。");
                    return;
                }
                if (event.isShiftClick() && (InventorySlots.isLockedSkillSlot(slot)
                        || isSkillItem(items, current)
                        || isShield(items, current)
                        || (!isEmpty(current) && items.isUndroppable(current)))) {
                    event.setCancelled(true);
                    deny(player, "该槽位物品不可移出。");
                    return;
                }
            }

            if (slot == 40 && event.isShiftClick()) {
                event.setCancelled(true);
                deny(player, "盾牌不可移出副手。");
                forceShieldOffhandNextTick(player);
                return;
            }
        }

        if (!isEmpty(cursor) && isShield(items, cursor)) {
            if (clickOnPlayerInv && event.getSlot() != 40) {
                event.setCancelled(true);
                deny(player, "盾牌只能放在副手。");
                forceShieldOffhandNextTick(player);
            }
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && isSkillItem(items, current)) {
            event.setCancelled(true);
        }

        // 双击收集非堆叠物
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && ItemPlacement.isNonStackable(items, current)) {
            event.setCancelled(true);
        }
    }

    private boolean handleNumberKey(InventoryClickEvent event, Player player, ItemManager items,
                                    Inventory clicked, Inventory bottom, PlayerInventory playerInv) {
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton < 0) {
            return false;
        }

        if (InventorySlots.isLockedSkillSlot(hotbarButton)) {
            event.setCancelled(true);
            deny(player, "技能槽不可用数字键交换。");
            return true;
        }

        if (clicked == null || !clicked.equals(bottom)) {
            return false;
        }

        int slot = event.getSlot();

        if (InventorySlots.isHotbar(slot) && InventorySlots.isLockedSkillSlot(slot)) {
            event.setCancelled(true);
            deny(player, "技能槽不可用数字键交换。");
            return true;
        }

        if (slot == 40) {
            ItemStack hotbarItem = playerInv.getItem(hotbarButton);
            ItemStack off = playerInv.getItemInOffHand();
            if (!isEmpty(off) && (isShield(items, off) || items.isUndroppable(off))) {
                event.setCancelled(true);
                deny(player, "盾牌不可移出副手。");
                forceShieldOffhandNextTick(player);
                return true;
            }
            if (!isEmpty(hotbarItem) && !isShield(items, hotbarItem)) {
                event.setCancelled(true);
                deny(player, "副手只能装备盾牌。");
                return true;
            }
        }

        if (isStorageSlot(slot)) {
            ItemStack hotbarItem = playerInv.getItem(hotbarButton);
            if (isCustomItem(items, hotbarItem)
                    || isCustomItem(items, event.getCurrentItem())
                    || isShield(items, hotbarItem)
                    || isShield(items, event.getCurrentItem())) {
                event.setCancelled(true);
                deny(player, "自定义物品/盾牌不能进入背包。");
                return true;
            }
        }

        if (InventorySlots.isHotbar(slot)) {
            ItemStack movingToHotbar = event.getCurrentItem();
            ItemStack movingToSlot = playerInv.getItem(hotbarButton);
            if (isShield(items, movingToHotbar) || isShield(items, movingToSlot)) {
                event.setCancelled(true);
                deny(player, "盾牌只能在副手，不能进热键栏。");
                forceShieldOffhandNextTick(player);
                return true;
            }
            if (isCustomItem(items, movingToHotbar) && !canPlaceInHotbar(items, movingToHotbar, hotbarButton)) {
                event.setCancelled(true);
                deny(player, "该物品不能放在此槽位。");
                return true;
            }
            if (isCustomItem(items, movingToSlot) && !canPlaceInHotbar(items, movingToSlot, slot)) {
                event.setCancelled(true);
                deny(player, "该物品不能放在此槽位。");
                return true;
            }
        }

        return false;
    }

    // =====================================================================
    // 拖拽
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!shouldLock(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        ItemStack old = event.getOldCursor();
        int topSize = event.getView().getTopInventory().getSize();

        // 非堆叠物禁止一次拖到多格
        if (ItemPlacement.isNonStackable(items, old) && event.getRawSlots().size() > 1) {
            event.setCancelled(true);
            deny(player, "该物品每格仅限 1 个，不可拖拽分堆。");
            return;
        }

        for (int raw : event.getRawSlots()) {
            int invSlot;
            if (event.getView().getType() == InventoryType.CRAFTING) {
                invSlot = event.getView().convertSlot(raw);
            } else if (raw >= topSize) {
                invSlot = event.getView().convertSlot(raw);
            } else {
                continue;
            }
            if (invSlot < 0) {
                continue;
            }

            if (isStorageSlot(invSlot) && (isCustomItem(items, old) || isShield(items, old))) {
                event.setCancelled(true);
                deny(player, "自定义物品/盾牌不能放进背包。");
                return;
            }
            if (InventorySlots.isLockedSkillSlot(invSlot)) {
                event.setCancelled(true);
                deny(player, "不可拖拽到技能槽。");
                return;
            }
            if (invSlot >= 36 && invSlot <= 39) {
                event.setCancelled(true);
                deny(player, "不可拖拽到护甲槽。");
                return;
            }
            if (invSlot == 40) {
                if (!isEmpty(old) && !isShield(items, old)) {
                    event.setCancelled(true);
                    deny(player, "副手只能放盾牌。");
                    return;
                }
            }
            if (InventorySlots.isHotbar(invSlot)) {
                if (isShield(items, old)) {
                    event.setCancelled(true);
                    deny(player, "盾牌只能放在副手。");
                    return;
                }
                if (isCustomItem(items, old) && !canPlaceInHotbar(items, old, invSlot)) {
                    event.setCancelled(true);
                    deny(player, "该物品不能放在此槽位。");
                    return;
                }
            }
        }
    }

    // =====================================================================
    // F 键
    // =====================================================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!shouldLock(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        ItemStack mainBefore = event.getMainHandItem();
        ItemStack offBefore = event.getOffHandItem();

        if (isShield(items, offBefore)) {
            event.setCancelled(true);
            forceShieldOffhandNextTick(player);
            deny(player, "盾牌锁定在副手，无法用 F 交换。");
            return;
        }

        if (!isEmpty(offBefore) && items.isUndroppable(offBefore)) {
            event.setCancelled(true);
            deny(player, "副手物品已锁定。");
            return;
        }

        if (!isEmpty(mainBefore) && !isShield(items, mainBefore)) {
            event.setCancelled(true);
            deny(player, "副手只能装备盾牌。");
            return;
        }

        if (InventorySlots.isLockedSkillSlot(player.getInventory().getHeldItemSlot())) {
            event.setCancelled(true);
            deny(player, "技能槽不可与副手交换。");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> sanitizeAll(player));
    }

    // =====================================================================
    // 丢弃
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!shouldLock(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        ItemStack stack = event.getItemDrop().getItemStack();

        if (items.isUndroppable(stack) || isShield(items, stack) || isSkillItem(items, stack)) {
            event.setCancelled(true);
            deny(player, "该物品不可丢弃。");
            forceShieldOffhandNextTick(player);
            return;
        }
        if (InventorySlots.isLockedSkillSlot(player.getInventory().getHeldItemSlot())) {
            event.setCancelled(true);
            deny(player, "技能槽不可丢弃。");
        }
    }

    // =====================================================================
    // 关背包
    // =====================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!shouldLock(player)) {
            return;
        }
        sanitizeAll(player);
    }

    // =====================================================================
    // 公开校正
    // =====================================================================

    public void forceShieldOffhandNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> sanitizeAll(player));
    }

    public void sanitizeAll(Player player) {
        splitIllegalStacks(player);
        sanitizeOffhandAndShields(player);
        sanitizeStorageCustomItems(player);
    }

    /**
     * 拆分 amount>1 的非堆叠自定义物。
     */
    public void splitIllegalStacks(Player player) {
        if (player == null || !player.isOnline() || !shouldLock(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        PlayerInventory inv = player.getInventory();
        boolean any = false;

        for (int i = 0; i <= 40; i++) {
            ItemStack stack;
            if (i == 40) {
                stack = inv.getItemInOffHand();
            } else if (i <= 35) {
                stack = inv.getItem(i);
            } else {
                continue; // 36-39 护甲不拆堆
            }

            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!ItemPlacement.isNonStackable(items, stack)) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                // 仍确保 maxStackSize / instance
                ensureUniqueMeta(stack);
                if (i == 40) {
                    inv.setItemInOffHand(stack);
                } else {
                    inv.setItem(i, stack);
                }
                continue;
            }

            int extra = stack.getAmount() - 1;
            stack.setAmount(1);
            ensureUniqueMeta(stack);
            if (i == 40) {
                inv.setItemInOffHand(stack);
            } else {
                inv.setItem(i, stack);
            }
            any = true;

            for (int k = 0; k < extra; k++) {
                ItemStack one = stack.clone();
                one.setAmount(1);
                rekeyInstance(one);
                if (!tryPlaceInLegalSlot(player, items, one)) {
                    player.getWorld().dropItemNaturally(player.getLocation(), one);
                }
            }
        }

        if (any) {
            player.sendMessage("§e[DFS] 已拆分堆叠物品（每格仅 1 个）。");
        }
    }

    public void sanitizeOffhandAndShields(Player player) {
        if (player == null || !player.isOnline() || !shouldLock(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        PlayerInventory inv = player.getInventory();
        ItemStack keptShield = null;

        ItemStack off = inv.getItemInOffHand();
        if (isShield(items, off)) {
            keptShield = off.clone();
            keptShield.setAmount(1);
        } else if (!isEmpty(off)) {
            inv.setItemInOffHand(null);
            relocateOrDrop(player, items, off);
        }

        for (int i = 0; i <= 35; i++) {
            ItemStack stack = inv.getItem(i);
            if (!isShield(items, stack)) {
                continue;
            }
            inv.setItem(i, null);
            if (keptShield == null) {
                keptShield = stack.clone();
                keptShield.setAmount(1);
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (isShield(items, cursor)) {
            player.setItemOnCursor(null);
            if (keptShield == null) {
                keptShield = cursor.clone();
                keptShield.setAmount(1);
            }
        }

        if (keptShield != null) {
            inv.setItemInOffHand(keptShield);
        }
    }

    public void sanitizeStorageCustomItems(Player player) {
        if (player == null || !player.isOnline() || !shouldLock(player)) {
            return;
        }

        ItemManager items = plugin.getItemManager();
        PlayerInventory inv = player.getInventory();

        // 1) 主背包 9–35
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = inv.getItem(i);
            if (isEmpty(stack)) {
                continue;
            }

            if (isShield(items, stack)) {
                inv.setItem(i, null);
                if (isEmpty(inv.getItemInOffHand())) {
                    ItemStack one = stack.clone();
                    one.setAmount(1);
                    inv.setItemInOffHand(one);
                }
                continue;
            }

            if (!isCustomItem(items, stack)) {
                inv.setItem(i, null);
                continue;
            }

            inv.setItem(i, null);
            ItemStack one = stack.clone();
            one.setAmount(1);
            if (!tryPlaceInLegalSlot(player, items, one)) {
                player.getWorld().dropItemNaturally(player.getLocation(), one);
                player.sendMessage("§e[DFS] 背包中的自定义物品无空固定槽，已掉落在地。");
            }
        }

        // 2) 热键错槽
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = inv.getItem(i);
            if (!isCustomItem(items, stack) || isShield(items, stack)) {
                continue;
            }
            if (canPlaceInHotbar(items, stack, i)) {
                // 合法但 amount>1 已由 split 处理
                if (stack.getAmount() > 1 && ItemPlacement.isNonStackable(items, stack)) {
                    // 再保险
                }
                continue;
            }
            inv.setItem(i, null);
            ItemStack one = stack.clone();
            one.setAmount(1);
            if (!tryPlaceInLegalSlot(player, items, one)) {
                player.getWorld().dropItemNaturally(player.getLocation(), one);
                player.sendMessage("§e[DFS] 热键槽物品位置非法且无空位，已掉落。");
            }
        }

        // 3) 光标
        ItemStack cursor = player.getItemOnCursor();
        if (isCustomItem(items, cursor) && !isShield(items, cursor)) {
            ItemStack clone = cursor.clone();
            clone.setAmount(1);
            if (tryPlaceInLegalSlot(player, items, clone)) {
                player.setItemOnCursor(null);
            }
        }
    }

    public boolean tryPlaceInLegalSlot(Player player, ItemManager items, ItemStack stack) {
        if (isEmpty(stack)) {
            return true;
        }
        stack.setAmount(1);

        if (isShield(items, stack)) {
            if (isEmpty(player.getInventory().getItemInOffHand())) {
                player.getInventory().setItemInOffHand(stack);
                return true;
            }
            return false;
        }

        String type = getType(stack);
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

        int preferred = InventorySlots.preferredHotbarSlot(type, slotHint);
        PlayerInventory inv = player.getInventory();

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

    private void relocateOrDrop(Player player, ItemManager items, ItemStack stack) {
        if (isEmpty(stack)) {
            return;
        }
        if (isShield(items, stack)) {
            if (isEmpty(player.getInventory().getItemInOffHand())) {
                player.getInventory().setItemInOffHand(stack);
            }
            return;
        }
        if (!tryPlaceInLegalSlot(player, items, stack)) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
    }

    private void ensureUniqueMeta(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.setMaxStackSize(1);
        if (!meta.getPersistentDataContainer().has(ItemKeys.instanceId(), PersistentDataType.STRING)) {
            meta.getPersistentDataContainer().set(
                    ItemKeys.instanceId(),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );
        }
        stack.setItemMeta(meta);
    }

    private void rekeyInstance(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(
                ItemKeys.instanceId(),
                PersistentDataType.STRING,
                UUID.randomUUID().toString()
        );
        stack.setItemMeta(meta);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private boolean allowOffhandClick(ItemManager items, ItemStack cursor, ItemStack current, InventoryAction action) {
        switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP,
                 SWAP_WITH_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT,
                 DROP_ALL_CURSOR, DROP_ONE_CURSOR -> {
                if (!isEmpty(current) && (isShield(items, current) || items.isUndroppable(current))) {
                    return false;
                }
            }
            default -> {
            }
        }
        if (!isEmpty(cursor) && !isShield(items, cursor)) {
            return false;
        }
        return true;
    }

    private boolean canPlaceInHotbar(ItemManager items, ItemStack stack, int hotbarSlot) {
        if (isShield(items, stack)) {
            return false;
        }
        String type = getType(stack);
        String id = items.getItemId(stack);
        String slotHint = "";
        if (id != null) {
            var gi = items.getGameItem(id);
            if (gi != null) {
                if (gi.getSlot() != null) {
                    slotHint = gi.getSlot();
                }
                if (type == null || type.isEmpty()) {
                    type = gi.getType();
                }
            }
        }
        if (id == null) {
            return !InventorySlots.isLockedSkillSlot(hotbarSlot);
        }
        return InventorySlots.canPlaceInHotbar(type, slotHint, hotbarSlot);
    }

    private boolean isCustomItem(ItemManager items, ItemStack stack) {
        if (isEmpty(stack)) {
            return false;
        }
        return items.getItemId(stack) != null;
    }

    private boolean isShield(ItemManager items, ItemStack stack) {
        return items.isShield(stack);
    }

    private boolean isSkillItem(ItemManager items, ItemStack stack) {
        String type = getType(stack);
        if (type == null) {
            return false;
        }
        String t = type.toLowerCase(Locale.ROOT);
        return t.contains("skill") || t.equals("signature") || t.equals("ultimate");
    }

    private String getType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.type(), PersistentDataType.STRING);
    }

    private boolean isStorageSlot(int slot) {
        return slot >= 9 && slot <= 35;
    }

    private boolean isPlacingOrSwap(InventoryAction action) {
        return action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    private void deny(Player player, String msg) {
        player.sendMessage("§c[DFS] " + msg);
    }
}
