package org.starset.deltaforcestrike.live;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LiveJsonTest {
    @Test
    void escapesQuotesBackslashesControlsAndPreservesUnicode() {
        assertEquals("\\\"\\\\\\n\\r\\t\\u0000\\u0008\\u000c中文", LiveJson.escape("\"\\\n\r\t\u0000\b\f中文"));
        assertEquals("", LiveJson.escape(null));
        assertEquals("\"\"", LiveJson.str(null));
        assertEquals("\"hello\"", LiveJson.str("hello"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void nonFiniteNumbersBecomeValidJsonZero(double value) {
        assertEquals("0", LiveJson.num(value));
    }

    @Test
    void finiteNumbersAreLocaleIndependentAndCompact() {
        assertEquals("42", LiveJson.num(42));
        assertEquals("-42", LiveJson.num(-42));
        assertEquals("0", LiveJson.num(-0.0));
        assertEquals("1.25", LiveJson.num(1.25));
        assertEquals(Double.toString(Double.MAX_VALUE), LiveJson.num(Double.MAX_VALUE));
    }

    @Test
    void assemblesNestedObjectsArraysAndLiteralsWithoutQuotingValues() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("na\"me", LiveJson.str("T"));
        fields.put("alive", LiveJson.bool(true));
        fields.put("score", LiveJson.num(13));
        fields.put("data", LiveJson.arr(List.of(LiveJson.nul(), LiveJson.bool(false))));
        assertEquals("{\"na\\\"me\":\"T\",\"alive\":true,\"score\":13,\"data\":[null,false]}", LiveJson.obj(fields));
        assertEquals("{}", LiveJson.obj(Map.of()));
        assertEquals("[]", LiveJson.arr(List.of()));
    }
}
