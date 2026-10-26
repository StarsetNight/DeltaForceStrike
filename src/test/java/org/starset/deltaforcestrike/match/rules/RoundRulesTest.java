package org.starset.deltaforcestrike.match.rules;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.starset.deltaforcestrike.match.MatchState;
import org.starset.deltaforcestrike.match.RoundState;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RoundRulesTest {
    @Test
    void shippedRegulationAndOvertimeLimits() throws Exception {
        try (var in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            var c = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(12, c.getInt("match.half-round"));
            assertEquals(13, c.getInt("match.win-target"));
            assertEquals(24, c.getInt("match.max-rounds"));
            assertEquals(3, c.getInt("overtime.half-round"));
            assertEquals(4, c.getInt("overtime.win-target"));
            assertEquals(6, c.getInt("overtime.max-rounds"));
        }
    }

    @ParameterizedTest
    @CsvSource({"12,11,23,false", "13,0,13,true", "0,13,13,true", "12,12,24,true", "14,0,14,true"})
    void regulationEndsAtThirteenWinsOrTwentyFourRounds(int t, int ct, int round, boolean expected) {
        assertEquals(expected, RoundRules.matchFinished(t, ct, round, 13, 24));
    }

    @ParameterizedTest
    @CsvSource({"false,11,6,5,false", "false,12,6,6,true", "true,12,6,6,false",
            "false,24,12,12,false", "false,13,13,0,false"})
    void swapAfterTwelfthCompletedRoundOnceAndNeverAfterMatchEnd(boolean swapped, int round, int t, int ct, boolean expected) {
        assertEquals(expected, RoundRules.shouldSwap(swapped, round, 12, 24, t, ct, 13));
    }

    @Test
    void overtimeInheritsTwelveAllAndSwapsAfterThreeCompletedRounds() {
        int target = 12 + Math.max(1, 4); // Match.overtimeWinTarget uses the inherited score baseline.
        assertFalse(RoundRules.matchFinished(15, 12, 3, target, 6));
        assertTrue(RoundRules.shouldSwap(false, 3, 3, 6, 15, 12, target));
        assertTrue(RoundRules.matchFinished(16, 12, 4, target, 6));
        assertTrue(RoundRules.matchFinished(15, 15, 6, target, 6));
        assertFalse(RoundRules.shouldSwap(true, 4, 3, 6, 13, 15, target));
    }

    @ParameterizedTest
    @EnumSource(MatchState.class)
    void countdownOnlyStartsOrRestartsFromQueueStates(MatchState state) {
        assertEquals(state == MatchState.WAITING || state == MatchState.COUNTDOWN,
                RoundRules.canStartCountdown(state));
    }

    @ParameterizedTest
    @EnumSource(MatchState.class)
    void overtimeOnlyReentersPlayFromTiedActiveEndWindow(MatchState state) {
        assertEquals(state == MatchState.ENDING, RoundRules.canEnterOvertime(true, state, 12, 12));
        assertFalse(RoundRules.canEnterOvertime(false, state, 12, 12));
        assertFalse(RoundRules.canEnterOvertime(true, state, 13, 11));
    }

    @ParameterizedTest
    @EnumSource(RoundState.class)
    void bombPlantAndRoundEndStateGuards(RoundState state) {
        assertEquals(state == RoundState.COMBAT, RoundRules.canPlant(state));
        // Existing behavior permits ending during BUY as well as COMBAT/BOMB_PLANTED.
        assertEquals(state == RoundState.BUY || state == RoundState.COMBAT || state == RoundState.BOMB_PLANTED,
                RoundRules.canEndRound(state));
    }
}
