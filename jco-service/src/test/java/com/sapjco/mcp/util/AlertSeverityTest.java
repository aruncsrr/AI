package com.sapjco.mcp.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AlertSeverity.
 */
class AlertSeverityTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // isFailure() tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void isFailure_critical_returnsTrue() {
        assertTrue(AlertSeverity.isFailure("critical"));
    }

    @Test
    void isFailure_fatal_returnsTrue() {
        assertTrue(AlertSeverity.isFailure("fatal"));
    }

    @Test
    void isFailure_caseInsensitive() {
        assertTrue(AlertSeverity.isFailure("CRITICAL"));
        assertTrue(AlertSeverity.isFailure("Critical"));
        assertTrue(AlertSeverity.isFailure("FATAL"));
        assertTrue(AlertSeverity.isFailure("Fatal"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tolerant", "warning", "info", "unknown"})
    void isFailure_nonFailureSeverities_returnsFalse(String severity) {
        assertFalse(AlertSeverity.isFailure(severity));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isFailure_nullOrEmpty_returnsFalse(String severity) {
        assertFalse(AlertSeverity.isFailure(severity));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // isWarning() tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void isWarning_tolerant_returnsTrue() {
        assertTrue(AlertSeverity.isWarning("tolerant"));
    }

    @Test
    void isWarning_warning_returnsTrue() {
        assertTrue(AlertSeverity.isWarning("warning"));
    }

    @Test
    void isWarning_caseInsensitive() {
        assertTrue(AlertSeverity.isWarning("TOLERANT"));
        assertTrue(AlertSeverity.isWarning("Tolerant"));
        assertTrue(AlertSeverity.isWarning("WARNING"));
        assertTrue(AlertSeverity.isWarning("Warning"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"critical", "fatal", "info", "unknown"})
    void isWarning_nonWarningSeverities_returnsFalse(String severity) {
        assertFalse(AlertSeverity.isWarning(severity));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isWarning_nullOrEmpty_returnsFalse(String severity) {
        assertFalse(AlertSeverity.isWarning(severity));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Constants tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void constants_haveExpectedValues() {
        assertEquals("critical", AlertSeverity.CRITICAL);
        assertEquals("fatal", AlertSeverity.FATAL);
        assertEquals("tolerant", AlertSeverity.TOLERANT);
        assertEquals("warning", AlertSeverity.WARNING);
    }
}
