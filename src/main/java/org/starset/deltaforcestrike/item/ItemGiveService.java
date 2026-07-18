package org.starset.deltaforcestrike.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.match.PlayerSession;
import org.starset.deltaforcestrike.match.Team;
import org.starset.deltaforcestrike.util.ConfigKeys;
import org.starset.deltaforcestrike.util.InventorySlots;
import org.starset.deltaforcestrike.util.ItemPlacement;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 统一发放：
 * - 槽 0 近战 / 1 远程 / 2 C4 或拆除钳 / 3-5 道具 / 6-8 技能
 * - 远程补箭；技能充能进槽 7；盾受配置控制
 * - T 禁止获得拆除钳；CT 不应通过本服务拿系统 C4（商店另拦）
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

    /**
     * @param replaceWeapons true=可覆盖近战/远程/槽2（商店/指令/系统发放）
     */
    public boolean give(Player player, String id, boolean replaceWeapons) {
        if (player == null || id == null) {
            return false;
        }

        GameItem def = itemManager.getGameItem(id);
        if (def == null) {
            return false;
        }

        // ---------- 盾 ----------
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

        // ---------- 技能充能 → 槽 7（不给实物充能道具）----------
        if (isSkillCharge(def, id)) {
            return grantPurchasableSkillCharge(player);
        }

        // ---------- 拆除钳：仅 CT ----------
        if (isDefuseKitDef(def, id)) {
            if (!isPlayerCt(player)) {
                player.sendMessage("§c[DFS] 拆除钳仅防守方 CT 可购买/使用。");
                return false;
            }
            return giveToBombSlot(player, id, replaceWeapons);
        }

        // ---------- 改造 TNT（系统/指令；商店应不上架）----------
        if (isPlantBombDef(def, id)) {
            // 允许 T 系统发放；非 T 拒绝（防误 give）
            if (!isPlayerT(player) && !player.hasPermission("deltaforcestrike.admin")) {
                player.sendMessage("§c[DFS] 改造TNT仅进攻方持有。");
                return false;
            }
            return giveToBombSlot(player, id, replaceWeapons);
        }

        // ---------- 护甲套装 ----------
        if (def.isArmorSet()) {
            boolean ok = itemManager.giveArmorSet(player, id);
            if (ok && ConfigKeys.shieldEnabled() && isIronArmorId(id, def)) {
                giveShieldToOffhand(player);
            }
            return ok;
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

        int preferred = InventorySlots.preferredHotbarSlot(type, slotHint);

        // ---------- 槽 2：bomb / defuse（双保险，上面已处理 kit/plant）----------
        if (preferred == InventorySlots.BOMB
                || "bomb".equals(type)
                || "defuse".equals(type)
                || "plant".equals(action)
                || "defuse".equals(action)
                || "bomb".equals(slotHint)
                || "defuse".equals(slotHint)) {
            return putBombSlot(inv, stack, replaceWeapons);
        }

        // ---------- 道具 3–5 ----------
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

        // ---------- 其它精确热键 ----------
        if (preferred >= 0 && preferred <= 8) {
            if (!replaceWeapons) {
                ItemStack cur = inv.getItem(preferred);
                if (cur != null && !cur.getType().isAir()) {
                    return false;
                }
            }
            inv.setItem(preferred, stack);

            if (isRangedWeapon(type, slotHint, stack.getType())) {
                refillArrowsTo(player, ConfigKeys.arrowsPerRanged());
            }
            return true;
        }

        return false;
    }

    // ------------------------------------------------------------------
    // 槽 2 发放
    // ------------------------------------------------------------------

    private boolean giveToBombSlot(Player player, String id, boolean replaceWeapons) {
        ItemStack stack = itemManager.createItem(id);
        if (stack == null) {
            // 尝试短 id
            return false;
        }
        stack.setAmount(1);
        return putBombSlot(player.getInventory(), stack, replaceWeapons);
    }

    private boolean putBombSlot(PlayerInventory inv, ItemStack stack, boolean replaceWeapons) {
        if (!replaceWeapons) {
            ItemStack cur = inv.getItem(InventorySlots.BOMB);
            if (cur != null && !cur.getType().isAir()) {
                return false;
            }
        }
        inv.setItem(InventorySlots.BOMB, stack);
        return true;
    }

    // ------------------------------------------------------------------
    // 类型判断
    // ------------------------------------------------------------------

    private boolean isDefuseKitDef(GameItem def, String id) {
        if (def == null) {
            return false;
        }
        String action = def.getAction() == null ? "" : def.getAction().toLowerCase(Locale.ROOT);
        String type = def.getType() == null ? "" : def.getType().toLowerCase(Locale.ROOT);
        String check = ((id == null ? "" : id) + " " + def.getId()).toLowerCase(Locale.ROOT);
        return "defuse".equals(action)
                || "defuse".equals(type)
                || check.contains("defuse");
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
        return "plant".equals(action)
                || "bomb".equals(type)
                || check.contains("plant-bomb");
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

    // ------------------------------------------------------------------
    // 技能充能占位
    // ------------------------------------------------------------------

    private boolean grantPurchasableSkillCharge(Player player) {
        ItemStack skillItem = createPurchasableSkillPlaceholder(player);
        player.getInventory().setItem(InventorySlots.PURCHASABLE, skillItem);
        player.sendMessage("§d[DFS] 已充能购买技能 §7(槽位 7) §8— 干员技能效果开发中");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.4f);
        return true;
    }

    private ItemStack createPurchasableSkillPlaceholder(Player player) {
        String opId = null;
        PlayerSession s = sessionOf(player);
        if (s != null) {
            opId = s.getOperatorId();
        }

        String name = "§d购买技能";
        String desc = "§7干员技能系统开发中";
        if (opId != null && !opId.isEmpty()) {
            name = "§d购买技能 §8(" + opId + ")";
            desc = "§7所属干员: §f" + opId + " §8(TODO)";
        }

        ItemStack stack = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            meta.lore(List.of(
                    LegacyComponentSerializer.legacySection().deserialize(desc),
                    Component.text("右键释放 · 尚未实装")
            ));
            meta.getPersistentDataContainer().set(ItemKeys.id(), PersistentDataType.STRING, "skill.purchasable");
            meta.getPersistentDataContainer().set(ItemKeys.type(), PersistentDataType.STRING, "skill");
            meta.getPersistentDataContainer().set(ItemKeys.action(), PersistentDataType.STRING, "skill_purchasable");
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(
                    ItemKeys.instanceId(), PersistentDataType.STRING, UUID.randomUUID().toString());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // 护甲 / 远程
    // ------------------------------------------------------------------

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
            if (stack != null && stack.getType() == Material.ARROW) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    public void refillArrowsTo(Player player, int targetTotal) {
        int have = countArrows(player);
        if (have >= targetTotal) {
            return;
        }
        giveArrows(player, targetTotal - have);
    }

    /**
     * 箭优先进主背包 9–35，再 3–8；不占 0/1/2。
     */
    public void giveArrows(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        int remain = amount;

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

        if (remain > 0) {
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.ARROW, remain));
        }
    }
}
