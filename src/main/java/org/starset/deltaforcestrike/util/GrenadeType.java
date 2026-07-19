package org.starset.deltaforcestrike.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;

public enum GrenadeType {
    SMOKE,
    WITHER,
    INCENDIARY;

    public static GrenadeType fromItem(ItemManager items, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();

        // 干员技能（含 aier_smoke 等 id）绝不是战术道具
        String type = pdc.get(ItemKeys.type(), PersistentDataType.STRING);
        if (type != null && "skill".equalsIgnoreCase(type)) {
            return null;
        }
        String id = pdc.get(ItemKeys.id(), PersistentDataType.STRING);
        if (id != null && id.toLowerCase().startsWith("skill.")) {
            return null;
        }

        String action = pdc.get(ItemKeys.action(), PersistentDataType.STRING);
        if (action != null) {
            return switch (action.toLowerCase()) {
                case "smoke" -> SMOKE;
                case "wither" -> WITHER;
                case "explosion_fire", "incendiary", "fireball" -> INCENDIARY;
                default -> null;
            };
        }
        String itemId = items.getItemId(stack);
        if (itemId == null) {
            return null;
        }
        String low = itemId.toLowerCase();
        // 仅匹配商店道具 id，避免 skill.*.*smoke* 被误判
        if (low.equals("smoke") || low.endsWith(".smoke") || low.contains("grenade.smoke")) {
            return SMOKE;
        }
        if (low.contains("wither")) {
            return WITHER;
        }
        if (low.contains("incendiary") || low.contains("flame")) {
            return INCENDIARY;
        }
        return null;
    }
}
