package com.sapjco.mcp.util;

import java.util.Set;

/**
 * Constants and utilities for alert severity checking in SAP ADT responses.
 *
 * <p>SAP ADT uses different severity levels for alerts in ABAP Unit and ATC results:
 * <ul>
 *   <li>{@code critical} - Test assertion failures, critical errors</li>
 *   <li>{@code fatal} - Fatal errors that prevent execution</li>
 *   <li>{@code tolerant} - Warnings that don't fail the test</li>
 *   <li>{@code warning} - Informational warnings</li>
 * </ul>
 *
 * <p>IMPORTANT: Real SAP responses typically use {@code severity="critical"} for
 * failed assertions, not {@code severity="fatal"}. Both should be treated as failures.
 */
public final class AlertSeverity {

    private AlertSeverity() {
        // Utility class - no instantiation
    }

    /** Severity for test assertion failures and critical errors */
    public static final String CRITICAL = "critical";

    /** Severity for fatal errors that prevent execution */
    public static final String FATAL = "fatal";

    /** Severity for tolerant warnings (don't fail the test) */
    public static final String TOLERANT = "tolerant";

    /** Severity for informational warnings */
    public static final String WARNING = "warning";

    /** Set of severity values that indicate test failures */
    private static final Set<String> FAILURE_SEVERITIES = Set.of(CRITICAL, FATAL);

    /** Set of severity values that indicate warnings (non-failures) */
    private static final Set<String> WARNING_SEVERITIES = Set.of(TOLERANT, WARNING);

    /**
     * Check if a severity indicates a failure (critical or fatal).
     *
     * @param severity the severity string from SAP ADT response
     * @return true if the severity indicates a failure
     */
    public static boolean isFailure(String severity) {
        return severity != null && FAILURE_SEVERITIES.contains(severity.toLowerCase());
    }

    /**
     * Check if a severity indicates a warning (tolerant or warning).
     *
     * @param severity the severity string from SAP ADT response
     * @return true if the severity indicates a warning
     */
    public static boolean isWarning(String severity) {
        return severity != null && WARNING_SEVERITIES.contains(severity.toLowerCase());
    }
}
