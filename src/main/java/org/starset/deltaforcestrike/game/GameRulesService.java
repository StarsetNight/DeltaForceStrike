package org.starset.deltaforcestrike.game;

import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.util.Worlds;

/**
 * 竞技世界规则：EASY、饱食拉满。
 * 自然回血由 GameRulesListener 拦截。
 * 注意：不要在 tick 里打 log，否则会刷屏。
 */
public final class GameRulesService {

    private final DeltaForceStrike plugin;
    /** 仅首次/世界变化时打一次日志 */
    private boolean loggedArenaRules = false;

    public GameRulesService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void applyToArenaWorld() {
        World world = Worlds.arenaWorld();
        if (world != null) {
            applyToWorld(world, true);
        }
    }

    public void applyToWorld(World world) {
        applyToWorld(world, false);
    }

    /**
     * @param logOnce 是否允许打日志（仅启动/世界加载时 true）
     */
    public void applyToWorld(World world, boolean logOnce) {
        if (world == null || !Worlds.isArena(world)) {
            return;
        }
        // 已是 EASY 则不再 set，减少无意义写入
        if (world.getDifficulty() != Difficulty.EASY) {
            world.setDifficulty(Difficulty.EASY);
        }

        if (logOnce && !loggedArenaRules && plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Rules] 竞技世界 " + world.getName() + " → EASY（仅提示一次）");
            loggedArenaRules = true;
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
        }
        player.setHealth(Math.max(1.0, maxHealth));
    }

    /**
     * 定时：静默维持 EASY + 饱食，禁止任何周期性 log。
     */
    public void tick() {
        World world = Worlds.arenaWorld();
        if (world != null) {
            applyToWorld(world, false);
        }
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (plugin.getMatchManager().isInMatch(player)) {
                fillFood(player);
            }
        }
    }
}
