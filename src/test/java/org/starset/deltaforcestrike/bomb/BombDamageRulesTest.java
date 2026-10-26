package org.starset.deltaforcestrike.bomb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class BombDamageRulesTest {
    @ParameterizedTest
    @CsvSource({"0,50", "12,50", "12.00001,37.5", "24,37.5", "24.00001,25",
            "36,25", "36.00001,15", "48,15", "48.00001,7.5", "60,7.5", "60.00001,0"})
    void distanceBandsIncludeUpperBoundary(double distance, double expected) {
        assertEquals(expected, BombDamageRules.damageByDistance(distance, 60, 50, 2), 1e-9);
    }

    @Test
    void invalidRadiusAndOutsideRangeIgnoreMinimumDamage() {
        assertEquals(0, BombDamageRules.damageByDistance(0, 0, 50, 2));
        assertEquals(0, BombDamageRules.damageByDistance(0, -1, 50, 2));
        assertEquals(0, BombDamageRules.damageByDistance(61, 60, 50, 100));
    }

    @Test
    void minimumCanBeDisabledOrExceedScaledMaximumAsBefore() {
        assertEquals(2, BombDamageRules.damageByDistance(60, 60, 1, 2));
        assertEquals(0.15, BombDamageRules.damageByDistance(60, 60, 1, 0), 1e-9);
        assertEquals(100, BombDamageRules.damageByDistance(0, 60, 50, 100));
    }
}
