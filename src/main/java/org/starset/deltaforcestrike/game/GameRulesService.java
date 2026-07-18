package org.starset.deltaforcestrike.game;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;

/**
 * 全局玩法规则：难度 EASY、饱食拉满。
 * 自然回血：不使用已弃用/不存在的 GameRule API，由 GameRulesListener 事件拦截。
 */
public final class GameRulesService {

    private final DeltaForceStrike plugin;

    public GameRulesService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void applyToAllWorlds() {
        for (World world : Bukkit.getWorlds()) {
            applyToWorld(world);
        }
    }

    public void applyToWorld(World world) {
        world.setDifficulty(Difficulty.EASY);

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Rules] 世界 " + world.getName() + " → EASY");
        }
    }

    public void fillFood(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    public void fillHealth(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        double maxHealth = 20.0;
        try {
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                maxHealth = max.getValue();
            }
        } catch (Throwable ignored) {
            // Attribute 名随版本变化时保持 20
        }
        player.setHealth(Math.max(1.0, maxHealth));
    }

    public void tick() {
        applyToAllWorlds();
        for (Player player : Bukkit.getOnlinePlayers()) {
            fillFood(player);
        }
    }
}
