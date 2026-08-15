package org.starset.deltaforcestrike.util;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 玩家状态重置的公共入口。
 * 此前「清空物品栏 + 状态」的逻辑散落在 MatchManager / RoundManager /
 * SpectatorRoleManager 等多处逐字复制，统一收敛到这里。
 */
public final class PlayerState {

    private PlayerState() {}

    /** 清空物品栏主体：主背包 + 护甲 + 副手（不动光标与手持槽） */
    public static void clearInventory(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
    }

    /** 清除全部药水效果 */
    public static void clearPotionEffects(Player player) {
        if (player == null) {
            return;
        }
        for (var pe : player.getActivePotionEffects()) {
            player.removePotionEffect(pe.getType());
        }
    }

    /** 重置杂项状态：光标、手持槽、火/冰冻/摔落、速度、飞行、经验、吸收 */
    public static void resetExtras(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setItemOnCursor(null);
        player.getInventory().setHeldItemSlot(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setGliding(false);
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        try {
            player.setAbsorptionAmount(0);
        } catch (Throwable ignored) {
        }
    }

    /** 全量重置：物品栏 + 药水 + 杂项（入场 / 离场 / 换边等场景） */
    public static void resetAll(Player player) {
        clearInventory(player);
        clearPotionEffects(player);
        resetExtras(player);
    }
}
