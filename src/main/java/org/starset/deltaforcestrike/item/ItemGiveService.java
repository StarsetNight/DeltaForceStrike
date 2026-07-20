package org.starset.deltaforcestrike.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.util.ConfigKeys;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.ItemPlacement;

import java.util.Locale;

/**
 * 统一发放：
 * 0近战 1远程 2=C4/拆除钳 3-5道具 6招牌 7购买技能 8大招
 * 技能充能 → OperatorService.grantPurchasableFromShop
 * T 禁止拆除钳
 */
public final class ItemGiveService {

    public static final int ARROWS_WITH_RANGED = 15;

    private final ItemManager itemManager;

    public ItemGiveService(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    public boolean give(Player player, String id) {
        return give(player, id, true);
    }

    public boolean give(Player player, String id, boolean replaceWeapons) {
        if (player == null || id == null) {
            return false;
        }

        GameItem def = itemManager.getGameItem(id);
        if (def == null) {
            return false;
        }

        // 盾
        if (isShieldDef(def, id)) {
            if (!ConfigKeys.shieldEnabled()) {
                return false;
            }
            ItemStack shield = itemManager.createItem(id);
            if (shield == null) {
                return false;
            }
            shield.setAmount(1);
            player.getInventory().setItemInOffHand(shield);
            return true;
        }

        // 技能充能 → 仅通过 OperatorService 写入第 8 格购买技能
        if (isSkillCharge(def, id)) {
            var ops = DeltaForceStrike.getInstance().getOperatorService();
            if (ops == null) {
                player.sendMessage("§c干员系统未加载");
                return false;
            }
            return ops.grantPurchasableFromShop(player);
        }

        // 拆除钳：仅 CT → 槽 2
        if (isDefuseKitDef(def, id)) {
            if (!isPlayerCt(player)) {
                player.sendMessage("§c[DFS] 拆除钳仅防守方 CT 可购买/使用。");
                return false;
            }
            return giveToBombSlot(player, id, replaceWeapons);
        }

        // 改造 TNT
        if (isPlantBombDef(def, id)) {
            if (!isPlayerT(player) && !player.hasPermission("deltaforcestrike.admin")) {
                player.sendMessage("§c[DFS] 改造TNT仅进攻方持有。");
                return false;
            }
            return giveToBombSlot(player, id, replaceWeapons);
        }

        // 护甲：只允许升级，覆盖旧套；同级/降级拒绝
        if (def.isArmorSet()) {
            return giveArmorUpgrade(player, id, def);
        }

        ItemStack stack = itemManager.createItem(id);
        if (stack == null) {
            return false;
        }
        stack.setAmount(1);

        String type = def.getType() == null ? "" : def.getType().toLowerCase(Locale.ROOT);
        String slotHint = def.getSlot() == null ? "" : def.getSlot().toLowerCase(Locale.ROOT);
        String action = def.getAction() == null ? "" : def.getAction().toLowerCase(Locale.ROOT);
        PlayerInventory inv = player.getInventory();

        // 禁止商店/give 把东西塞进技能三格
        int preferred = InventorySlots.preferredHotbarSlot(type, slotHint);
        if (preferred == InventorySlots.SIGNATURE
                || preferred == InventorySlots.PURCHASABLE
                || preferred == InventorySlots.ULTIMATE) {
            return false;
        }

        if (preferred == InventorySlots.BOMB
                || "bomb".equals(type)
                || "defuse".equals(type)
                || "plant".equals(action)
                || "defuse".equals(action)
                || "bomb".equals(slotHint)
                || "defuse".equals(slotHint)) {
            return putBombSlot(player, inv, stack, replaceWeapons);
        }

        if (preferred == InventorySlots.UTIL_1
                || "utility".equals(type)
                || "utility".equals(slotHint)
                || "grenade".equals(type)) {
            int empty = ItemPlacement.findEmptyUtility(inv);
            if (empty < 0) {
                player.sendMessage("§c[DFS] 战术道具槽已满（最多3个）。");
                return false;
            }
            return ItemPlacement.putInSlot(inv, empty, stack, itemManager, false);
        }

        if (preferred >= 0 && preferred <= 5) {
            // 槽位冲突：旧装备掉地上，再放入新装备
            if (!placeOrDropConflict(player, inv, preferred, stack, replaceWeapons)) {
                return false;
            }
            if (isRangedWeapon(type, slotHint, stack.getType())) {
                refillArrowsTo(player, ConfigKeys.arrowsPerRanged());
            }
            return true;
        }

        return false;
    }

    /**
     * 放入指定热键槽；若有旧物且允许替换 → 旧物掉落。
     */
    private boolean placeOrDropConflict(Player player, PlayerInventory inv,
                                        int slot, ItemStack stack, boolean replaceWeapons) {
        ItemStack cur = inv.getItem(slot);
        boolean empty = cur == null || cur.getType().isAir() || cur.getAmount() <= 0;
        if (!empty) {
            if (!replaceWeapons) {
                return false;
            }
            // 同 id 不重复给
            String curId = itemManager.getItemId(cur);
            String newId = itemManager.getItemId(stack);
            if (curId != null && curId.equalsIgnoreCase(newId)) {
                player.sendMessage("§7你已持有该物品。");
                return false;
            }
            dropAtFeet(player, cur);
            inv.setItem(slot, null);
        }
        inv.setItem(slot, stack);
        return true;
    }

    private void dropAtFeet(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return;
        }
        ItemStack drop = stack.clone();
        player.getWorld().dropItemNaturally(player.getLocation(), drop);
    }

    private boolean giveToBombSlot(Player player, String id, boolean replaceWeapons) {
        ItemStack stack = itemManager.createItem(id);
        if (stack == null) {
            return false;
        }
        stack.setAmount(1);
        return putBombSlot(player, player.getInventory(), stack, replaceWeapons);
    }

    private boolean putBombSlot(Player player, PlayerInventory inv, ItemStack stack, boolean replaceWeapons) {
        return placeOrDropConflict(player, inv, InventorySlots.BOMB, stack, replaceWeapons);
    }

    /**
     * 护甲：只能升级（轻→重），同级/降级拒绝；覆盖不掉落。
     * tier: 0无 1轻(锁链) 2重(铁)
     */
    private boolean giveArmorUpgrade(Player player, String id, GameItem def) {
        int newTier = armorTierOf(id, def);
        int curTier = currentArmorTier(player);
        if (newTier <= curTier) {
            if (newTier == curTier && curTier > 0) {
                player.sendMessage("§c你已拥有同级护甲，无法重复购买。");
            } else if (newTier < curTier) {
                player.sendMessage("§c不能购买更低级的护甲。");
            } else {
                player.sendMessage("§c无法购买该护甲。");
            }
            return false;
        }
        boolean ok = itemManager.giveArmorSet(player, id);
        if (ok && ConfigKeys.shieldEnabled() && isIronArmorId(id, def)) {
            giveShieldToOffhand(player);
        }
        if (ok) {
            player.sendMessage("§a护甲已升级。");
        }
        return ok;
    }

    private int armorTierOf(String id, GameItem def) {
        String check = ((id == null ? "" : id) + " " + (def == null ? "" : def.getId()))
                .toLowerCase(Locale.ROOT);
        if (check.contains("iron")) {
            return 2;
        }
        if (check.contains("chain") || check.contains("leather") || check.contains("light")) {
            return 1;
        }
        // 默认按价格粗分
        if (def != null && def.getPrice() >= 900) {
            return 2;
        }
        return 1;
    }

    private int currentArmorTier(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType().isAir()) {
            // 看头盔/靴
            ItemStack helm = player.getInventory().getHelmet();
            if (helm == null || helm.getType().isAir()) {
                return 0;
            }
            chest = helm;
        }
        Material m = chest.getType();
        String n = m.name();
        if (n.startsWith("IRON_") || n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_")) {
            return 2;
        }
        if (n.startsWith("CHAINMAIL_") || n.startsWith("LEATHER_") || n.startsWith("GOLDEN_")) {
            return 1;
        }
        // 自定义 id
        String id = itemManager.getItemId(chest);
        if (id != null) {
            String low = id.toLowerCase(Locale.ROOT);
            if (low.contains("iron")) {
                return 2;
            }
            if (low.contains("chain") || low.contains("leather")) {
                return 1;
            }
        }
        return 1;
    }

    private boolean isDefuseKitDef(GameItem def, String id) {
        if (def == null) {
            return false;
        }
        String action = def.getAction() == null ? "" : def.getAction().toLowerCase(Locale.ROOT);
        String type = def.getType() == null ? "" : def.getType().toLowerCase(Locale.ROOT);
        String check = ((id == null ? "" : id) + " " + def.getId()).toLowerCase(Locale.ROOT);
        return "defuse".equals(action) || "defuse".equals(type) || check.contains("defuse");
    }

    private boolean isPlantBombDef(GameItem def, String id) {
        if (def == null) {
            return false;
        }
        String action = def.getAction() == null ? "" : def.getAction().toLowerCase(Locale.ROOT);
        String type = def.getType() == null ? "" : def.getType().toLowerCase(Locale.ROOT);
        String check = ((id == null ? "" : id) + " " + def.getId()).toLowerCase(Locale.ROOT);
        if (check.contains("defuse")) {
            return false;
        }
        return "plant".equals(action) || "bomb".equals(type) || check.contains("plant-bomb");
    }

    private boolean isShieldDef(GameItem def, String id) {
        if (def == null) {
            return false;
        }
        String type = def.getType() == null ? "" : def.getType().toLowerCase(Locale.ROOT);
        String slot = def.getSlot() == null ? "" : def.getSlot().toLowerCase(Locale.ROOT);
        if (type.equals("shield") || slot.equals("shield") || slot.equals("offhand")) {
            return true;
        }
        if (def.getMaterial() == Material.SHIELD) {
            return true;
        }
        String check = ((id == null ? "" : id) + " " + def.getId()).toLowerCase(Locale.ROOT);
        return check.contains("shield");
    }

    private boolean isSkillCharge(GameItem def, String id) {
        if (def == null) {
            return false;
        }
        String type = def.getType() == null ? "" : def.getType().toLowerCase(Locale.ROOT);
        if (type.equals("skill_charge") || type.equals("skill-charge")) {
            return true;
        }
        String action = def.getAction() == null ? "" : def.getAction().toLowerCase(Locale.ROOT);
        if (action.contains("skill_charge") || action.contains("skill-charge")) {
            return true;
        }
        String check = ((id == null ? "" : id) + " " + def.getId()).toLowerCase(Locale.ROOT);
        return check.contains("skill-charge")
                || check.contains("skill_charge")
                || check.equals("charge")
                || check.endsWith(".charge");
    }

    private boolean isPlayerCt(Player player) {
        PlayerSession s = sessionOf(player);
        return s != null && s.getTeam() == Team.CT;
    }

    private boolean isPlayerT(Player player) {
        PlayerSession s = sessionOf(player);
        return s != null && s.getTeam() == Team.T;
    }

    private PlayerSession sessionOf(Player player) {
        try {
            Match match = DeltaForceStrike.getInstance().getMatchManager().getMatch();
            if (match == null) {
                return null;
            }
            return match.getSession(player.getUniqueId());
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isIronArmorId(String id, GameItem def) {
        String check = ((id == null ? "" : id) + " " + (def.getId() == null ? "" : def.getId()))
                .toLowerCase(Locale.ROOT);
        return check.contains("iron-armor") || check.contains("iron_armor");
    }

    private void giveShieldToOffhand(Player player) {
        if (!ConfigKeys.shieldEnabled()) {
            return;
        }
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
            if (stack != null && isArrowMaterial(stack.getType())) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    public static boolean isArrowMaterial(Material mat) {
        return mat == Material.ARROW
                || mat == Material.SPECTRAL_ARROW
                || mat == Material.TIPPED_ARROW;
    }

    public void refillArrowsTo(Player player, int targetTotal) {
        if (player == null) {
            return;
        }
        int cap = Math.max(1, targetTotal);
        int have = countArrows(player);
        if (have >= cap) {
            if (have > cap) {
                trimArrowsTo(player, cap);
            }
            return;
        }
        giveArrows(player, cap - have);
    }

    /** 背包箭超过上限时裁剪（优先从后槽裁） */
    public void trimArrowsTo(Player player, int max) {
        if (player == null || max < 0) {
            return;
        }
        int have = countArrows(player);
        if (have <= max) {
            return;
        }
        int needRemove = have - max;
        PlayerInventory inv = player.getInventory();
        for (int i = inv.getSize() - 1; i >= 0 && needRemove > 0; i--) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || !isArrowMaterial(cur.getType())) {
                continue;
            }
            int amt = cur.getAmount();
            if (amt <= needRemove) {
                inv.setItem(i, null);
                needRemove -= amt;
            } else {
                cur.setAmount(amt - needRemove);
                inv.setItem(i, cur);
                needRemove = 0;
            }
        }
    }

    public void giveArrows(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        int cap = ConfigKeys.arrowsPerRanged();
        int have = countArrows(player);
        int room = Math.max(0, cap - have);
        int remain = Math.min(amount, room);
        if (remain <= 0) {
            return;
        }
        PlayerInventory inv = player.getInventory();

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
        for (int i = 9; i <= 35 && remain > 0; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur != null && !cur.getType().isAir()) {
                continue;
            }
            int put = Math.min(64, remain);
            inv.setItem(i, new ItemStack(Material.ARROW, put));
            remain -= put;
        }
        for (int i = 3; i <= 8 && remain > 0; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur != null && !cur.getType().isAir()) {
                continue;
            }
            int put = Math.min(64, remain);
            inv.setItem(i, new ItemStack(Material.ARROW, put));
            remain -= put;
        }
        // 超上限部分不掉落刷箭
    }
}
