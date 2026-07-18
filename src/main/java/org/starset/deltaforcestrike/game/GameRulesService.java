package org.starset.deltaforcestrike.game;

import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.util.Worlds;

public final class GameRulesService {

    private final DeltaForceStrike plugin;

    public GameRulesService(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void applyToArenaWorld() {
        World world = Worlds.arenaWorld();
        if (world != null) {
            applyToWorld(world);
        }
    }

    public void applyToWorld(World world) {
        if (!Worlds.isArena(world)) return;
        world.setDifficulty(Difficulty.EASY);
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Rules] " + world.getName() + " → EASY");
        }
    }

    public void fillFood(Player player) {
        if (player == null || !player.isOnline()) return;
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    public void fillHealth(Player player) {
        if (player == null || !player.isOnline()) return;
        double maxHealth = 20.0;
        try {
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) maxHealth = max.getValue();
        } catch (Throwable ignored) {
        }
        player.setHealth(Math.max(1.0, maxHealth));
    }

    public void tick() {
        applyToArenaWorld();
        World arena = Worlds.arenaWorld();
        if (arena == null) return;
        for (Player player : arena.getPlayers()) {
            if (plugin.getMatchManager().isInMatch(player)) {
                fillFood(player);
            }
        }
    }
}
