package org.starset.deltaforcestrike.match;

import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MatchManager {

    private final DeltaForceStrike plugin;
    private final Map<UUID, UUID> playerMatch = new HashMap<>();

    public MatchManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
    }

    public void join(Player player) {
        if (playerMatch.containsKey(player.getUniqueId())) {
            player.sendMessage("§c[DFS] 你已在队列/比赛中。");
            return;
        }

        int max = plugin.getConfig().getInt("game.max-player", 6);
        if (playerMatch.size() >= max) {
            player.sendMessage("§c[DFS] 队列已满（" + max + "）。");
            return;
        }

        // 占位：正式 Match 接入前用「自引用」标记在队
        playerMatch.put(player.getUniqueId(), player.getUniqueId());

        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Match] " + player.getName() + " 加入队列 (" + playerMatch.size() + "/" + max + ")");
        }
        player.sendMessage("§a[DFS] 已加入队列 (" + playerMatch.size() + "/" + max + ")");
    }

    public void leave(Player player) {
        UUID removed = playerMatch.remove(player.getUniqueId());
        if (removed == null) {
            player.sendMessage("§7[DFS] 你不在队列中。");
            return;
        }
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[Match] " + player.getName() + " 离开队列");
        }
        player.sendMessage("§e[DFS] 已离开队列。");
    }

    public boolean isInMatch(Player player) {
        return playerMatch.containsKey(player.getUniqueId());
    }

    public void shutdown() {
        playerMatch.clear();
        plugin.getLogger().info("[Match] MatchManager 已关闭，队列已清空");
    }
}
