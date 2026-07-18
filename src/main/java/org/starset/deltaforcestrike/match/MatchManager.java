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
        // TODO: 接入正式 Match 队列
        player.sendMessage("§a[DFS] 队列系统已挂载（待实现完整 Match）。");
    }

    public void leave(Player player) {
        playerMatch.remove(player.getUniqueId());
        player.sendMessage("§e[DFS] 已离开队列。");
    }

    public void shutdown() {
        playerMatch.clear();
    }

    public boolean isInMatch(Player player) {
        return playerMatch.containsKey(player.getUniqueId());
    }
}
