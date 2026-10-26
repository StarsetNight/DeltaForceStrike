package org.starset.deltaforcestrike.match.rules;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EconomyRulesTest {
    private static YamlConfiguration config() throws Exception {
        try (var in = EconomyRulesTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private EconomyRules.Settlement settle(boolean won, boolean attacking, boolean planted,
                                            int round, boolean overtime, int losses, int winReward) throws Exception {
        var c = config();
        return EconomyRules.settle(won, attacking, planted, round, overtime, losses, winReward,
                c.getIntegerList("economy.defeat-bonus"), c.getInt("economy.pistol-round-bonus"),
                c.getInt("economy.lose-reward-planted-bonus"));
    }

    @ParameterizedTest
    @CsvSource({"start-money,800", "max-money,16000", "win-reward-elimination,3250",
            "win-reward-bomb,3500", "lose-reward-planted-bonus,800", "pistol-round-bonus,2550",
            "kill-reward,300", "kill-reward-melee,1500", "plant-reward,300", "defuse-reward,200"})
    void shippedEconomyValues(String key, int expected) throws Exception {
        // Contract for configured values, not a Bukkit-backed credit/debit integration test.
        assertEquals(expected, config().getInt("economy." + key));
    }

    @ParameterizedTest
    @CsvSource({"win-reward-elimination,3250", "win-reward-bomb,3500"})
    void victoryResetsLossStreakAndDoesNotAddPlantBonus(String key, int expected) throws Exception {
        var c = config();
        assertEquals(new EconomyRules.Settlement(expected, 0, 0),
                settle(true, true, true, 1, false, 5, c.getInt("economy." + key)));
    }

    @ParameterizedTest
    @CsvSource({"0,2000", "1,2550", "2,3100", "3,3100", "99,3100"})
    void defeatStepsAndSaturates(int priorLosses, int expected) throws Exception {
        var result = settle(false, false, false, 2, false, priorLosses, 3250);
        assertEquals(expected, result.reward());
        assertEquals(priorLosses + 1, result.consecutiveLosses());
        assertEquals(0, result.plantedReward());
    }

    @ParameterizedTest
    @CsvSource({"true,true,800", "true,false,0", "false,true,0", "false,false,0"})
    void onlyPlantedAttackerDefeatAddsSeparateBonus(boolean attacking, boolean planted, int bonus) throws Exception {
        var result = settle(false, attacking, planted, 2, false, 0, 3250);
        assertEquals(2000, result.reward());
        assertEquals(bonus, result.plantedReward());
    }

    @Test
    void pistolRewardReplacesDefeatTierButStillAdvancesStreak() throws Exception {
        assertEquals(new EconomyRules.Settlement(2550, 1, 800),
                settle(false, true, true, 1, false, 0, 3250));
        assertEquals(2550, settle(false, false, false, 1, false, 4, 3250).reward());
        assertEquals(2000, settle(false, false, false, 13, false, 0, 3250).reward());
    }

    @Test
    void overtimeStartsWithTenThousandAndNeverUsesPistolCompensation() throws Exception {
        assertTrue(config().getBoolean("overtime.reset-economy"));
        assertEquals(10000, config().getInt("overtime.start-money"));
        assertEquals(new EconomyRules.Settlement(2000, 1, 0),
                settle(false, false, false, 1, true, 0, 3250));
    }

    @Test
    void emptyDefeatConfigurationUsesExistingFallbackAndCustomTiersRemainEffective() {
        assertEquals(3100, EconomyRules.settle(false, false, false, 2, false, 8,
                123, List.of(), 456, 789).reward());
        assertEquals(new EconomyRules.Settlement(77, 3, -10),
                EconomyRules.settle(false, true, true, 2, false, 2, 123, List.of(77), 456, -10));
    }
}
