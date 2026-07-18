package org.starset.deltaforcestrike.manager;

import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.MatchManager;

public class GameManager {

    private final MatchManager matchManager;

    public GameManager(DeltaForceStrike plugin) {
        this.matchManager = new MatchManager(plugin);
    }

    public void shutdown() {
        matchManager.shutdown();
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }
}
