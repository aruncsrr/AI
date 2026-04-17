package com.sapjco.mcp.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FormattingUtils.
 */
class FormattingUtilsTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // truncate() tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void truncate_nullInput_returnsEmptyString() {
        assertEquals("", FormattingUtils.truncate(null, 10));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void truncate_nullOrEmpty_returnsEmptyString(String input) {
        assertEquals("", FormattingUtils.truncate(input, 10));
    }

    @Test
    void truncate_shortText_returnsUnchanged() {
        assertEquals("Hello", FormattingUtils.truncate("Hello", 10));
    }

    @Test
    void truncate_exactLength_returnsUnchanged() {
        assertEquals("Hello", FormattingUtils.truncate("Hello", 5));
    }

    @Test
    void truncate_longText_truncatesWithEllipsis() {
        assertEquals("Hello W...", FormattingUtils.truncate("Hello World", 10));
    }

    @Test
    void truncate_trimsWhitespace() {
        assertEquals("Hello", FormattingUtils.truncate("  Hello  ", 10));
    }

    @Test
    void truncate_longTextWithWhitespace_truncatesAfterTrim() {
        assertEquals("Hello...", FormattingUtils.truncate("  Hello World  ", 8));
    }

    @ParameterizedTest
    @CsvSource({
        "ABCDEFGHIJ, 10, ABCDEFGHIJ",
        "ABCDEFGHIJK, 10, ABCDEFG...",
        "A, 5, A",
        "ABCDE, 5, ABCDE",
        "ABCDEF, 5, AB..."
    })
    void truncate_variousLengths(String input, int maxLength, String expected) {
        assertEquals(expected, FormattingUtils.truncate(input, maxLength));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // formatDuration() tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void formatDuration_seconds_formatsAsSeconds() {
        assertEquals("1.234s", FormattingUtils.formatDuration(1234, "s"));
    }

    @Test
    void formatDuration_milliseconds_formatsAsMilliseconds() {
        assertEquals("500ms", FormattingUtils.formatDuration(500, "ms"));
    }

    @Test
    void formatDuration_nullUnit_positiveTime_formatsAsMilliseconds() {
        assertEquals("100ms", FormattingUtils.formatDuration(100, null));
    }

    @Test
    void formatDuration_zeroTime_nullUnit_returnsDash() {
        assertEquals("-", FormattingUtils.formatDuration(0, null));
    }

    @Test
    void formatDuration_zeroTime_msUnit_returnsZeroMs() {
        // zero ms with explicit ms unit still shows as "0ms" (not dash)
        // because the condition checks "ms".equals(unit) || timeMs > 0
        assertEquals("0ms", FormattingUtils.formatDuration(0, "ms"));
    }

    @Test
    void formatDuration_zeroSeconds_formatsAsZeroSeconds() {
        assertEquals("0.000s", FormattingUtils.formatDuration(0, "s"));
    }

    @ParameterizedTest
    @CsvSource({
        "1000, s, 1.000s",
        "1500, s, 1.500s",
        "100, ms, 100ms",
        "1, ms, 1ms"
    })
    void formatDuration_variousInputs(long timeMs, String unit, String expected) {
        assertEquals(expected, FormattingUtils.formatDuration(timeMs, unit));
    }
}
