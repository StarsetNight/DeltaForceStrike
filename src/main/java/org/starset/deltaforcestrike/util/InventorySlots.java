package org.starset.deltaforcestrike.util;

/**
 * 热键 0-8 对应玩家看到的 1-9 格。
 * 第7格=招牌 第8格=购买技能 第9格=大招
 */
public final class InventorySlots {

    private InventorySlots() {}

    public static final int MELEE = 0;
    public static final int RANGED = 1;
    /** 第 3 格：C4 / 拆除钳 */
    public static final int BOMB = 2;
    public static final int UTIL_1 = 3;
    public static final int UTIL_2 = 4;
    public static final int UTIL_3 = 5;
    /** 第 7 格：招牌技能 */
    public static final int SIGNATURE = 6;
    /** 第 8 格：购买技能 */
    public static final int PURCHASABLE = 7;
    /** 第 9 格：终极技能 */
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
                case "purchasable", "skill_buy", "skill_purchasable" -> PURCHASABLE;
                case "ultimate", "skill_ult", "skill_ultimate" -> ULTIMATE;
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
            // 技能充能不是热键道具
            case "skill_charge", "skill-charge" -> -1;
            case "skill", "signature" -> SIGNATURE;
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
            return isHotbar(hotbarSlot) && !isLockedSkillSlot(hotbarSlot);
        }
        return hotbarSlot == preferred;
    }
}
