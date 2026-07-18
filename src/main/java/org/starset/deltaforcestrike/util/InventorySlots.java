package org.starset.deltaforcestrike.util;

public final class InventorySlots {

    private InventorySlots() {}

    public static final int MELEE = 0;
    public static final int RANGED = 1;
    /** 热键第 3 格：T=改造TNT / CT=拆除钳 */
    public static final int BOMB = 2;
    public static final int UTIL_1 = 3;
    public static final int UTIL_2 = 4;
    public static final int UTIL_3 = 5;
    public static final int SIGNATURE = 6;
    public static final int PURCHASABLE = 7;
    public static final int ULTIMATE = 8;

    public static boolean isHotbar(int slot) {
        return slot >= 0 && slot <= 8;
    }

    public static boolean isUtility(int slot) {
        return slot >= UTIL_1 && slot <= UTIL_3;
    }

    public static boolean isSkill(int slot) {
        return slot == SIGNATURE || slot == PURCHASABLE || slot == ULTIMATE;
    }

    public static boolean isLockedSkillSlot(int slot) {
        return isSkill(slot);
    }

    public static int preferredHotbarSlot(String type, String slotHint) {
        if (slotHint != null && !slotHint.isEmpty()) {
            return switch (slotHint.toLowerCase()) {
                case "melee" -> MELEE;
                case "ranged" -> RANGED;
                case "bomb", "defuse", "defuse-kit", "c4" -> BOMB;
                case "utility" -> UTIL_1;
                case "signature", "skill_signature" -> SIGNATURE;
                case "purchasable", "skill_buy" -> PURCHASABLE;
                case "ultimate", "skill_ult" -> ULTIMATE;
                case "shield", "offhand" -> -2;
                case "armor" -> -3;
                default -> preferredByType(type);
            };
        }
        return preferredByType(type);
    }

    private static int preferredByType(String type) {
        if (type == null) {
            return MELEE;
        }
        return switch (type.toLowerCase()) {
            case "melee" -> MELEE;
            case "ranged" -> RANGED;
            case "bomb", "defuse" -> BOMB;
            case "utility", "grenade" -> UTIL_1;
            case "skill", "skill_charge" -> PURCHASABLE;
            case "shield" -> -2;
            case "armor" -> -3;
            default -> -1;
        };
    }

    public static boolean canPlaceInHotbar(String type, String slotHint, int hotbarSlot) {
        int preferred = preferredHotbarSlot(type, slotHint);
        if (preferred == -2 || preferred == -3) {
            return false;
        }
        if (preferred == UTIL_1) {
            return isUtility(hotbarSlot);
        }
        if (preferred < 0) {
            return isHotbar(hotbarSlot);
        }
        return hotbarSlot == preferred;
    }
}
