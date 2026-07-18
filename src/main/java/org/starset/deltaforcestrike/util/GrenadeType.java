package org.starset.deltaforcestrike.util;

import org.bukkit.inventory.ItemStack;
import org.starset.deltaforcestrike.item.ItemKeys;
import org.starset.deltaforcestrike.item.ItemManager;
import org.bukkit.persistence.PersistentDataType;

public enum GrenadeType {
    SMOKE,
    WITHER,
    INCENDIARY;

    public static GrenadeType fromItem(ItemManager items, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String action = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.action(), PersistentDataType.STRING);
        if (action == null) {
            // 兼容 type=utility + id
            String id = items.getItemId(stack);
            if (id == null) {
                return null;
            }
            String low = id.toLowerCase();
            if (low.contains("smoke")) return SMOKE;
            if (low.contains("wither")) return WITHER;
            if (low.contains("incendiary") || low.contains("flame")) return INCENDIARY;
            return null;
        }
        return switch (action.toLowerCase()) {
            case "smoke" -> SMOKE;
            case "wither" -> WITHER;
            case "explosion_fire", "incendiary", "fireball" -> INCENDIARY;
            default -> null;
        };
    }
}
