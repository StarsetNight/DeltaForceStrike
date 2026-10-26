package org.starset.deltaforcestrike.tournament;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TournamentServiceTest {
    @ParameterizedTest
    @CsvSource({"1.2.3,1.2.3,0", "1.2,1.2.0,0", "1.10,1.9,1", "2.0,10.0,-1",
            "1.2.3-beta,1.2.3,0", "1.2.3-rc1,1.2.4,-1", "1.2.4-beta,1.2.3,1",
            "1.2.3+build,1.2.3,0", "1.2.3-beta,1.2.2,1", "1.bad,1.0,0",
            "1.999999999999,1.0,0", "1.2.0.1,1.2,1"})
    void comparesNumericSegmentsAndStripsSegmentSuffixes(String a, String b, int expected) {
        assertEquals(expected, TournamentService.compareVer(a, b));
        assertEquals(-expected, TournamentService.compareVer(b, a));
    }
}
