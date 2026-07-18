package org.starset.deltaforcestrike.manager;

import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.MatchManager;

/**
 * 总协调器：持有各子系统，后续 Match/Round/Shop 都从这里拿。
 */
public class GameManager {

    private final DeltaForceStrike plugin;
    private final MatchManager matchManager;

    public GameManager(DeltaForceStrike plugin) {
        this.plugin = plugin;
        this.matchManager = new MatchManager(plugin);
    }

    public void shutdown() {
        matchManager.shutdown();
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public DeltaForceStrike getPlugin() {
        return plugin;
    }
}
