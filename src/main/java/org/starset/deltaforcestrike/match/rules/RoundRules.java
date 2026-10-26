package org.starset.deltaforcestrike.match.rules;

import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.RoundState;

/** Decisions only: scheduling and state mutation stay in the managers. */
public final class RoundRules {
    private RoundRules() {}

    public static boolean matchFinished(int scoreT, int scoreCT, int completedRounds,
                                        int winTarget, int maxRounds) {
        return scoreT >= winTarget || scoreCT >= winTarget || completedRounds >= maxRounds;
    }

    public static boolean shouldSwap(boolean alreadySwapped, int completedRounds,
                                     int halfRound, int maxRounds, int scoreT, int scoreCT, int winTarget) {
        return !alreadySwapped && completedRounds >= halfRound && completedRounds < maxRounds
                && scoreT < winTarget && scoreCT < winTarget;
    }

    public static boolean canStartCountdown(MatchState state) {
        return state == MatchState.WAITING || state == MatchState.COUNTDOWN;
    }

    public static boolean canEnterOvertime(boolean endWindowActive, MatchState state, int scoreT, int scoreCT) {
        return endWindowActive && state == MatchState.ENDING && scoreT == scoreCT;
    }

    public static boolean canPlant(RoundState state) { return state == RoundState.COMBAT; }
    public static boolean canEndRound(RoundState state) {
        return state != RoundState.ROUND_END && state != RoundState.IDLE;
    }
}
