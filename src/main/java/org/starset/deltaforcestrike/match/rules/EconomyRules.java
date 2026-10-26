package org.starset.deltaforcestrike.match.rules;

import java.util.List;

/** Pure round settlement; configuration lookup and capped balances remain with the caller. */
public final class EconomyRules {
    private EconomyRules() {}

    public record Settlement(int reward, int consecutiveLosses, int plantedReward) {}

    public static Settlement settle(boolean won, boolean attacking, boolean bombPlanted,
                                    int currentRound, boolean overtime, int previousLosses,
                                    int winReward, List<Integer> defeatRewards,
                                    int pistolReward, int plantedBonus) {
        if (won) return new Settlement(winReward, 0, 0);
        List<Integer> defeat = defeatRewards.isEmpty() ? List.of(2000, 2550, 3100) : defeatRewards;
        int losses = previousLosses + 1;
        boolean pistolRound = currentRound == 1 && !overtime;
        int reward = pistolRound ? pistolReward : defeat.get(Math.min(losses - 1, defeat.size() - 1));
        return new Settlement(reward, losses, bombPlanted && attacking ? plantedBonus : 0);
    }
}
