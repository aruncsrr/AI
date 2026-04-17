package com.sapjco.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting metadata from ADT tool responses.
 *
 * <p>Uses lightweight regex-based extraction rather than full XML parsing
 * for performance. Each tool type has specific extraction patterns tuned
 * to its XML structure.
 *
 * <p>This service is used by handlers to generate summary metadata
 * when writing large payloads to files.
 */
@Slf4j
@Service
public class MetadataExtractorService {

    // ========================= Syntax Check Patterns =========================
    private static final Pattern SYNTAX_CHECK_EXECUTED = Pattern.compile("checkExecuted=\"(true|false)\"");
    private static final Pattern SYNTAX_ACTIVATION_EXECUTED = Pattern.compile("activationExecuted=\"(true|false)\"");
    private static final Pattern SYNTAX_HAS_ERRORS = Pattern.compile("hasErrors=\"(true|false)\"");
    private static final Pattern SYNTAX_HAS_WARNINGS = Pattern.compile("hasWarnings=\"(true|false)\"");
    private static final Pattern SYNTAX_MESSAGE = Pattern.compile("<chkrun:checkMessage[^>]*>");
    private static final Pattern SYNTAX_MESSAGE_TYPE = Pattern.compile("type=\"([EWI])\"");
    private static final Pattern SYNTAX_MESSAGE_SEVERITY = Pattern.compile("severity=\"([^\"]+)\"");
    private static final Pattern SYNTAX_SHORT_TEXT = Pattern.compile("<chkrun:shortText>([^<]+)</chkrun:shortText>");
    private static final Pattern SYNTAX_LINE = Pattern.compile("line=\"(\\d+)\"");

    // ========================= Where-Used Patterns =========================
    private static final Pattern WHERE_USED_RESULT_COUNT = Pattern.compile("numberOfResults=\"(\\d+)\"");
    private static final Pattern WHERE_USED_REFERENCE = Pattern.compile("<usagereferences:referencedObject[^>]+>");
    private static final Pattern WHERE_USED_TYPE = Pattern.compile("adtcore:type=\"([^\"]+)\"");
    private static final Pattern WHERE_USED_PACKAGE = Pattern.compile("<adtcore:packageRef[^>]+adtcore:name=\"([^\"]+)\"");
    private static final Pattern WHERE_USED_OBJ_IDENTIFIER = Pattern.compile("<objectIdentifier>([^<]+)</objectIdentifier>");

    // ========================= ABAP Unit Patterns =========================
    private static final Pattern AUNIT_PROGRAM = Pattern.compile("<program[^>]+adtcore:name=\"([^\"]+)\"");
    private static final Pattern AUNIT_TEST_CLASS = Pattern.compile("<testClass[^>]+adtcore:name=\"([^\"]+)\"");
    private static final Pattern AUNIT_TEST_METHOD = Pattern.compile("<testMethod[^>]+>");
    private static final Pattern AUNIT_EXECUTION_TIME = Pattern.compile("executionTime=\"([\\d.]+)\"");
    private static final Pattern AUNIT_ALERT = Pattern.compile("<alert[^>]+>");
    private static final Pattern AUNIT_SEVERITY = Pattern.compile("severity=\"([^\"]+)\"");

    // ========================= ATC Patterns =========================
    private static final Pattern ATC_OBJECT = Pattern.compile("<atcobject:object[^>]+adtcore:name=\"([^\"]+)\"");
    private static final Pattern ATC_FINDING = Pattern.compile("<atcfinding:finding[^>]+>");
    private static final Pattern ATC_PRIORITY = Pattern.compile("atcfinding:priority=\"(\\d)\"");
    private static final Pattern ATC_CHECK_TITLE = Pattern.compile("atcfinding:checkTitle=\"([^\"]+)\"");
    private static final Pattern ATC_MESSAGE = Pattern.compile("atcfinding:messageTitle=\"([^\"]+)\"");

    // ========================= Package Contents Patterns =========================
    private static final Pattern PKG_OBJECT = Pattern.compile("<SEU_ADT_REPOSITORY_OBJ_NODE>");
    private static final Pattern PKG_OBJECT_TYPE = Pattern.compile("<OBJECT_TYPE>([^<]+)</OBJECT_TYPE>");
    private static final Pattern PKG_OBJECT_NAME = Pattern.compile("<OBJECT_NAME>([^<]+)</OBJECT_NAME>");

    // ========================= Search Patterns =========================
    private static final Pattern SEARCH_OBJECT_REF = Pattern.compile("<adtcore:objectReference[^>]+>");
    private static final Pattern SEARCH_TYPE = Pattern.compile("adtcore:type=\"([^\"]+)\"");
    private static final Pattern SEARCH_NAME = Pattern.compile("adtcore:name=\"([^\"]+)\"");

    // ========================= Data Preview Patterns =========================
    private static final Pattern DATA_TOTAL_ROWS = Pattern.compile("<dataPreview:totalRows>(\\d+)</dataPreview:totalRows>");
    private static final Pattern DATA_EXEC_TIME = Pattern.compile("<dataPreview:queryExecutionTime>([\\d.]+)</dataPreview:queryExecutionTime>");
    private static final Pattern DATA_COLUMN_NAME = Pattern.compile("<dataPreview:metadata[^>]+dataPreview:name=\"([^\"]+)\"");
    private static final Pattern DATA_ROW = Pattern.compile("<dataPreview:data>");

    // ========================= Transport Patterns =========================
    private static final Pattern TRANSPORT_TASK = Pattern.compile("<tm:task[^>]+>");
    private static final Pattern TRANSPORT_OBJECT = Pattern.compile("<tm:abap_object[^>]+>");
    private static final Pattern TRANSPORT_PGMID = Pattern.compile("tm:pgmid=\"([^\"]+)\"");
    private static final Pattern TRANSPORT_TYPE = Pattern.compile("tm:type=\"([^\"]+)\"");
    private static final Pattern TRANSPORT_OWNER = Pattern.compile("tm:owner=\"([^\"]+)\"");
    private static final Pattern TRANSPORT_STATUS = Pattern.compile("tm:status=\"([^\"]+)\"");

    // ========================= Diff Patterns =========================
    private static final Pattern DIFF_ADDITION = Pattern.compile("^\\+[^+]", Pattern.MULTILINE);
    private static final Pattern DIFF_DELETION = Pattern.compile("^-[^-]", Pattern.MULTILINE);
    private static final Pattern DIFF_HUNK = Pattern.compile("^@@", Pattern.MULTILINE);

    // ========================= ATC Result List Patterns =========================
    private static final Pattern ATC_RESULT = Pattern.compile("<atcresult:result>");
    private static final Pattern ATC_RESULT_DISPLAY_ID = Pattern.compile("<atcresult:displayId>([^<]+)</atcresult:displayId>");
    private static final Pattern ATC_RESULT_TITLE = Pattern.compile("<atcresult:title>([^<]+)</atcresult:title>");
    private static final Pattern ATC_RESULT_PRIO1 = Pattern.compile("<atcresult:numPrio1>(\\d+)</atcresult:numPrio1>");
    private static final Pattern ATC_RESULT_PRIO2 = Pattern.compile("<atcresult:numPrio2>(\\d+)</atcresult:numPrio2>");
    private static final Pattern ATC_RESULT_PRIO3 = Pattern.compile("<atcresult:numPrio3>(\\d+)</atcresult:numPrio3>");

    // ========================= Named Item Patterns =========================
    private static final Pattern NAMED_ITEM = Pattern.compile("<nameditem:namedItem>");
    private static final Pattern NAMED_ITEM_NAME = Pattern.compile("<nameditem:name>([^<]+)</nameditem:name>");

    // ========================= Coverage Patterns =========================
    private static final Pattern COVERAGE_STATEMENT = Pattern.compile("statementCoverage=\"([\\d.]+)\"");
    private static final Pattern COVERAGE_BRANCH = Pattern.compile("branchCoverage=\"([\\d.]+)\"");
    private static final Pattern COVERAGE_PROCEDURE = Pattern.compile("procedureCoverage=\"([\\d.]+)\"");

    // ========================= Version History Patterns =========================
    private static final Pattern VERSION_ENTRY = Pattern.compile("<atom:entry>");
    private static final Pattern VERSION_AUTHOR = Pattern.compile("<atom:name>([^<]+)</atom:name>");
    private static final Pattern VERSION_UPDATED = Pattern.compile("<atom:updated>([^<]+)</atom:updated>");

    // ========================= BOPF Patterns =========================
    private static final Pattern BOPF_NODES = Pattern.compile("<bo:nodes[^>]*bo:name=\"[^\"]+\"");
    private static final Pattern BOPF_ACTIONS = Pattern.compile("<bo:actions[^>]*bo:name=\"[^\"]+\"");
    private static final Pattern BOPF_ASSOCIATIONS = Pattern.compile("<bo:associations[^>]*bo:name=\"[^\"]+\"");
    private static final Pattern BOPF_QUERIES = Pattern.compile("<bo:queries[^>]*bo:name=\"[^\"]+\"");
    private static final Pattern BOPF_DETERMINATIONS = Pattern.compile("<bo:determinations[^>]*bo:name=\"[^\"]+\"");
    private static final Pattern BOPF_VALIDATIONS = Pattern.compile("<bo:validations[^>]*bo:name=\"[^\"]+\"");
    private static final Pattern BOPF_NAME = Pattern.compile("adtcore:name=\"([^\"]+)\"");
    private static final Pattern BOPF_TYPE = Pattern.compile("adtcore:type=\"([^\"]+)\"");
    private static final Pattern BOPF_MODEL = Pattern.compile("bo:programmingModel=\"([^\"]+)\"");
    private static final Pattern BOPF_CATEGORY = Pattern.compile("bo:objectCategory=\"([^\"]+)\"");

    /**
     * Extract metadata from tool response content.
     *
     * @param toolName Tool name (e.g., "GetWhereUsed", "RunAbapUnit")
     * @param content  Response content (XML or text)
     * @return Map of metadata key-value pairs
     */
    public Map<String, Object> extract(String toolName, String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }

        return switch (toolName) {
            case "CheckSyntax" -> extractSyntaxCheckMetadata(content);
            case "GetWhereUsed" -> extractWhereUsedMetadata(content);
            case "RunAbapUnit" -> extractAbapUnitMetadata(content);
            case "RunATC", "GetATCFindings", "GetATCResult" -> extractAtcMetadata(content);
            case "ListATCResults" -> extractAtcResultsMetadata(content);
            case "ListATCCheckVariants" -> extractNamedItemMetadata(content);
            case "GetPackageContents" -> extractPackageMetadata(content);
            case "Search" -> extractSearchMetadata(content);
            case "PreviewTableData", "PreviewCDSView", "SelectSQLQuery" -> extractDataMetadata(content);
            case "GetTransportContents" -> extractTransportMetadata(content);
            case "CompareVersions" -> extractDiffMetadata(content);
            case "GetCoverageResult" -> extractCoverageMetadata(content);
            case "GetVersionHistory" -> extractVersionHistoryMetadata(content);
            case "GetBopfBusinessObject" -> extractBopfMetadata(content);
            default -> Map.of();
        };
    }

    /**
     * Extract metadata from CheckSyntax response.
     */
    public Map<String, Object> extractSyntaxCheckMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Extract checkExecuted flag
        Matcher checkExecMatcher = SYNTAX_CHECK_EXECUTED.matcher(xml);
        if (checkExecMatcher.find()) {
            meta.put("checkExecuted", "true".equals(checkExecMatcher.group(1)));
        }

        // Extract activationExecuted flag
        Matcher actExecMatcher = SYNTAX_ACTIVATION_EXECUTED.matcher(xml);
        if (actExecMatcher.find()) {
            meta.put("activationExecuted", "true".equals(actExecMatcher.group(1)));
        }

        // Extract hasErrors flag (quick indicator)
        Matcher hasErrorsMatcher = SYNTAX_HAS_ERRORS.matcher(xml);
        if (hasErrorsMatcher.find()) {
            meta.put("hasErrors", "true".equals(hasErrorsMatcher.group(1)));
        }

        // Extract hasWarnings flag (quick indicator)
        Matcher hasWarningsMatcher = SYNTAX_HAS_WARNINGS.matcher(xml);
        if (hasWarningsMatcher.find()) {
            meta.put("hasWarnings", "true".equals(hasWarningsMatcher.group(1)));
        }

        // Count messages by type
        int errorCount = 0;
        int warningCount = 0;
        int infoCount = 0;
        java.util.List<String> topErrors = new java.util.ArrayList<>();

        // Find all message blocks
        Matcher msgMatcher = SYNTAX_MESSAGE.matcher(xml);
        int lastEnd = 0;
        while (msgMatcher.find()) {
            int msgStart = msgMatcher.start();
            // Find closing tag (either /> or </chkrun:checkMessage>)
            int closeTag = xml.indexOf("</chkrun:checkMessage>", msgStart);
            int selfClose = xml.indexOf("/>", msgStart);
            int msgEnd;
            if (closeTag >= 0 && (selfClose < 0 || closeTag < selfClose)) {
                msgEnd = closeTag + "</chkrun:checkMessage>".length();
            } else if (selfClose >= 0) {
                msgEnd = selfClose + 2;
            } else {
                msgEnd = xml.length();
            }

            String msgBlock = xml.substring(msgStart, Math.min(msgEnd, xml.length()));

            // Determine message type
            Matcher typeMatcher = SYNTAX_MESSAGE_TYPE.matcher(msgBlock);
            String type = "I";  // default to Info
            if (typeMatcher.find()) {
                type = typeMatcher.group(1);
            } else {
                // Try severity attribute as fallback
                Matcher sevMatcher = SYNTAX_MESSAGE_SEVERITY.matcher(msgBlock);
                if (sevMatcher.find()) {
                    String sev = sevMatcher.group(1).toLowerCase();
                    type = sev.contains("error") ? "E" : sev.contains("warn") ? "W" : "I";
                }
            }

            // Count by type
            switch (type) {
                case "E" -> errorCount++;
                case "W" -> warningCount++;
                default -> infoCount++;
            }

            // Extract shortText for top errors (first 5 errors)
            if ("E".equals(type) && topErrors.size() < 5) {
                Matcher textMatcher = SYNTAX_SHORT_TEXT.matcher(msgBlock);
                if (textMatcher.find()) {
                    String errorText = textMatcher.group(1);
                    // Try to extract line number
                    Matcher lineMatcher = SYNTAX_LINE.matcher(msgBlock);
                    if (lineMatcher.find()) {
                        errorText += " (line " + lineMatcher.group(1) + ")";
                    }
                    topErrors.add(errorText);
                }
            }

            lastEnd = msgEnd;
        }

        // Add counts
        meta.put("errors", errorCount);
        meta.put("warnings", warningCount);
        if (infoCount > 0) {
            meta.put("infos", infoCount);
        }

        // Determine overall status
        if (errorCount > 0) {
            meta.put("status", "INVALID");
        } else if (warningCount > 0) {
            meta.put("status", "VALID (with warnings)");
        } else {
            meta.put("status", "VALID");
        }

        // Add top errors for quick overview
        if (!topErrors.isEmpty()) {
            meta.put("topErrors", topErrors);
        }

        return meta;
    }

    /**
     * Extract metadata from GetWhereUsed response.
     */
    public Map<String, Object> extractWhereUsedMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Extract numberOfResults from root attribute
        Matcher resultMatcher = WHERE_USED_RESULT_COUNT.matcher(xml);
        if (resultMatcher.find()) {
            meta.put("numberOfResults", Integer.parseInt(resultMatcher.group(1)));
        }

        // Count actual reference objects
        int referenceCount = countMatches(xml, WHERE_USED_REFERENCE);
        meta.put("references", referenceCount);

        // Group by type
        Map<String, Integer> typeBreakdown = groupByPattern(xml, WHERE_USED_REFERENCE, WHERE_USED_TYPE);
        if (!typeBreakdown.isEmpty()) {
            meta.put("types", typeBreakdown);
        }

        // Extract unique packages
        java.util.Set<String> packages = extractUniqueMatches(xml, WHERE_USED_PACKAGE);
        if (!packages.isEmpty()) {
            meta.put("packages", packages);
        }

        // Count objectIdentifiers for lazy loading
        int identifierCount = countMatches(xml, WHERE_USED_OBJ_IDENTIFIER);
        if (identifierCount > 0) {
            meta.put("objectIdentifiers", identifierCount);
        }

        return meta;
    }

    /**
     * Extract metadata from RunAbapUnit response.
     */
    public Map<String, Object> extractAbapUnitMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Count programs (classes being tested)
        int programCount = countMatches(xml, AUNIT_PROGRAM);
        meta.put("programs", programCount);

        // Count test classes
        int classCount = countMatches(xml, AUNIT_TEST_CLASS);
        meta.put("testClasses", classCount);

        // Count test methods
        int methodCount = countMatches(xml, AUNIT_TEST_METHOD);
        meta.put("testMethods", methodCount);

        // Sum execution time
        double totalTime = 0;
        Matcher timeMatcher = AUNIT_EXECUTION_TIME.matcher(xml);
        while (timeMatcher.find()) {
            try {
                totalTime += Double.parseDouble(timeMatcher.group(1));
            } catch (NumberFormatException e) {
                // Skip malformed values
            }
        }
        meta.put("totalExecutionTime", String.format(java.util.Locale.ROOT, "%.3f", totalTime));

        // Count alerts (failures)
        int alertCount = countMatches(xml, AUNIT_ALERT);
        meta.put("alerts", alertCount);

        // Group alerts by severity
        if (alertCount > 0) {
            Map<String, Integer> severities = groupByPattern(xml, AUNIT_ALERT, AUNIT_SEVERITY);
            meta.put("severities", severities);
        }

        // Determine pass/fail status
        meta.put("status", alertCount == 0 ? "PASSED" : "FAILED");

        return meta;
    }

    /**
     * Extract metadata from ATC response.
     */
    public Map<String, Object> extractAtcMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Count objects checked
        java.util.Set<String> objects = extractUniqueMatches(xml, ATC_OBJECT);
        meta.put("objectsChecked", objects.size());
        if (!objects.isEmpty()) {
            meta.put("objects", objects);
        }

        // Count findings
        int findingCount = countMatches(xml, ATC_FINDING);
        meta.put("findings", findingCount);

        // Group by priority
        Map<String, Integer> priorities = new LinkedHashMap<>();
        Matcher findingMatcher = ATC_FINDING.matcher(xml);
        while (findingMatcher.find()) {
            int end = xml.indexOf("/>", findingMatcher.start());
            if (end > 0) {
                String findingXml = xml.substring(findingMatcher.start(), end);
                Matcher priorityMatcher = ATC_PRIORITY.matcher(findingXml);
                if (priorityMatcher.find()) {
                    String priority = priorityMatcher.group(1);
                    String label = switch (priority) {
                        case "1" -> "Error";
                        case "2" -> "Warning";
                        case "3" -> "Info";
                        default -> "Priority " + priority;
                    };
                    priorities.merge(label, 1, Integer::sum);
                }
            }
        }
        if (!priorities.isEmpty()) {
            meta.put("byPriority", priorities);
        }

        // Extract top check titles
        Map<String, Integer> checkTitles = new LinkedHashMap<>();
        Matcher checkMatcher = ATC_CHECK_TITLE.matcher(xml);
        while (checkMatcher.find()) {
            checkTitles.merge(checkMatcher.group(1), 1, Integer::sum);
        }
        if (!checkTitles.isEmpty()) {
            // Get top 3 checks
            meta.put("topChecks", checkTitles.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(3)
                    .map(e -> e.getKey() + " (" + e.getValue() + ")")
                    .toList());
        }

        return meta;
    }

    /**
     * Extract metadata from GetPackageContents response.
     */
    public Map<String, Object> extractPackageMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Count objects
        int objectCount = countMatches(xml, PKG_OBJECT);
        meta.put("objects", objectCount);

        // Group by object type
        Map<String, Integer> typeBreakdown = new LinkedHashMap<>();
        Matcher typeMatcher = PKG_OBJECT_TYPE.matcher(xml);
        while (typeMatcher.find()) {
            typeBreakdown.merge(typeMatcher.group(1), 1, Integer::sum);
        }
        if (!typeBreakdown.isEmpty()) {
            meta.put("byType", typeBreakdown);
        }

        return meta;
    }

    /**
     * Extract metadata from Search response.
     */
    public Map<String, Object> extractSearchMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Count results
        int resultCount = countMatches(xml, SEARCH_OBJECT_REF);
        meta.put("results", resultCount);

        // Group by type
        Map<String, Integer> typeBreakdown = groupByPattern(xml, SEARCH_OBJECT_REF, SEARCH_TYPE);
        if (!typeBreakdown.isEmpty()) {
            meta.put("byType", typeBreakdown);
        }

        return meta;
    }

    /**
     * Extract metadata from data preview responses.
     */
    public Map<String, Object> extractDataMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Extract total rows (DB total, not returned)
        Matcher rowsMatcher = DATA_TOTAL_ROWS.matcher(xml);
        if (rowsMatcher.find()) {
            meta.put("totalRows", Integer.parseInt(rowsMatcher.group(1)));
        }

        // Count actual returned rows
        int returnedRows = countMatches(xml, DATA_ROW);
        meta.put("returnedRows", returnedRows);

        // Extract execution time
        Matcher execMatcher = DATA_EXEC_TIME.matcher(xml);
        if (execMatcher.find()) {
            meta.put("executionTime", execMatcher.group(1) + " ms");
        }

        // Extract column names
        java.util.List<String> columns = new java.util.ArrayList<>();
        Matcher colMatcher = DATA_COLUMN_NAME.matcher(xml);
        while (colMatcher.find() && columns.size() < 10) {
            columns.add(colMatcher.group(1));
        }
        if (!columns.isEmpty()) {
            meta.put("columns", columns);
            meta.put("columnCount", columns.size());
        }

        return meta;
    }

    /**
     * Extract metadata from GetTransportContents response.
     */
    public Map<String, Object> extractTransportMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Extract owner
        Matcher ownerMatcher = TRANSPORT_OWNER.matcher(xml);
        if (ownerMatcher.find()) {
            meta.put("owner", ownerMatcher.group(1));
        }

        // Extract status
        Matcher statusMatcher = TRANSPORT_STATUS.matcher(xml);
        if (statusMatcher.find()) {
            String status = statusMatcher.group(1);
            String statusLabel = switch (status) {
                case "D" -> "Modifiable";
                case "R" -> "Released";
                case "L" -> "Protected";
                default -> status;
            };
            meta.put("status", statusLabel);
        }

        // Count tasks
        int taskCount = countMatches(xml, TRANSPORT_TASK);
        meta.put("tasks", taskCount);

        // Count objects
        int objectCount = countMatches(xml, TRANSPORT_OBJECT);
        meta.put("objects", objectCount);

        // Group by PGMID/TYPE
        Map<String, Integer> pgmidBreakdown = new LinkedHashMap<>();
        Matcher objMatcher = TRANSPORT_OBJECT.matcher(xml);
        while (objMatcher.find()) {
            int end = xml.indexOf("/>", objMatcher.start());
            if (end > 0) {
                String objXml = xml.substring(objMatcher.start(), end);
                Matcher pgmidMatcher = TRANSPORT_PGMID.matcher(objXml);
                Matcher typeMatcher = TRANSPORT_TYPE.matcher(objXml);
                if (pgmidMatcher.find() && typeMatcher.find()) {
                    String key = pgmidMatcher.group(1) + " " + typeMatcher.group(1);
                    pgmidBreakdown.merge(key, 1, Integer::sum);
                }
            }
        }
        if (!pgmidBreakdown.isEmpty()) {
            meta.put("byPgmidType", pgmidBreakdown);
        }

        return meta;
    }

    /**
     * Extract metadata from unified diff content.
     */
    public Map<String, Object> extractDiffMetadata(String text) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Count additions
        int additions = countMatches(text, DIFF_ADDITION);
        meta.put("additions", additions);

        // Count deletions
        int deletions = countMatches(text, DIFF_DELETION);
        meta.put("deletions", deletions);

        // Count hunks (change blocks)
        int hunks = countMatches(text, DIFF_HUNK);
        meta.put("hunks", hunks);

        // Summary line
        meta.put("summary", "+" + additions + " / -" + deletions + " in " + hunks + " hunks");

        return meta;
    }

    /**
     * Extract metadata from coverage result response.
     */
    public Map<String, Object> extractCoverageMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Extract statement coverage
        Matcher stmtMatcher = COVERAGE_STATEMENT.matcher(xml);
        if (stmtMatcher.find()) {
            meta.put("statementCoverage", stmtMatcher.group(1) + "%");
        }

        // Extract branch coverage
        Matcher branchMatcher = COVERAGE_BRANCH.matcher(xml);
        if (branchMatcher.find()) {
            meta.put("branchCoverage", branchMatcher.group(1) + "%");
        }

        // Extract procedure coverage
        Matcher procMatcher = COVERAGE_PROCEDURE.matcher(xml);
        if (procMatcher.find()) {
            meta.put("procedureCoverage", procMatcher.group(1) + "%");
        }

        return meta;
    }

    /**
     * Extract metadata from version history response.
     */
    public Map<String, Object> extractVersionHistoryMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Count versions
        int versionCount = countMatches(xml, VERSION_ENTRY);
        meta.put("versions", versionCount);

        // Extract first author (latest version)
        Matcher authorMatcher = VERSION_AUTHOR.matcher(xml);
        if (authorMatcher.find()) {
            meta.put("latestAuthor", authorMatcher.group(1));
        }

        // Extract first date (latest version)
        Matcher dateMatcher = VERSION_UPDATED.matcher(xml);
        if (dateMatcher.find()) {
            meta.put("latestDate", dateMatcher.group(1));
        }

        return meta;
    }

    /**
     * Extract metadata from BOPF Business Object response.
     */
    public Map<String, Object> extractBopfMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Extract BO metadata
        Matcher nameMatcher = BOPF_NAME.matcher(xml);
        if (nameMatcher.find()) {
            meta.put("name", nameMatcher.group(1));
        }

        Matcher typeMatcher = BOPF_TYPE.matcher(xml);
        if (typeMatcher.find()) {
            meta.put("type", typeMatcher.group(1));
        }

        Matcher modelMatcher = BOPF_MODEL.matcher(xml);
        if (modelMatcher.find()) {
            meta.put("programmingModel", modelMatcher.group(1));
        }

        Matcher categoryMatcher = BOPF_CATEGORY.matcher(xml);
        if (categoryMatcher.find()) {
            meta.put("objectCategory", categoryMatcher.group(1));
        }

        // Count components
        Map<String, Integer> components = new LinkedHashMap<>();
        components.put("nodes", countMatches(xml, BOPF_NODES));
        components.put("actions", countMatches(xml, BOPF_ACTIONS));
        components.put("associations", countMatches(xml, BOPF_ASSOCIATIONS));
        components.put("queries", countMatches(xml, BOPF_QUERIES));
        components.put("determinations", countMatches(xml, BOPF_DETERMINATIONS));
        components.put("validations", countMatches(xml, BOPF_VALIDATIONS));
        meta.put("components", components);

        int total = components.values().stream().mapToInt(Integer::intValue).sum();
        meta.put("totalComponents", total);

        return meta;
    }

    /**
     * Extract metadata from ATC result list response (ListATCResults).
     */
    public Map<String, Object> extractAtcResultsMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        // Count results
        int resultCount = countMatches(xml, ATC_RESULT);
        meta.put("results", resultCount);

        // Sum findings across all results
        int totalPrio1 = sumMatches(xml, ATC_RESULT_PRIO1);
        int totalPrio2 = sumMatches(xml, ATC_RESULT_PRIO2);
        int totalPrio3 = sumMatches(xml, ATC_RESULT_PRIO3);

        if (resultCount > 0) {
            Map<String, Integer> totals = new LinkedHashMap<>();
            totals.put("Errors (P1)", totalPrio1);
            totals.put("Warnings (P2)", totalPrio2);
            totals.put("Info (P3)", totalPrio3);
            meta.put("aggregatedFindings", totals);
        }

        return meta;
    }

    /**
     * Extract metadata from named item list response (ListATCCheckVariants).
     */
    public Map<String, Object> extractNamedItemMetadata(String xml) {
        Map<String, Object> meta = new LinkedHashMap<>();

        int itemCount = countMatches(xml, NAMED_ITEM);
        meta.put("variants", itemCount);

        // Extract first few names
        java.util.List<String> names = new java.util.ArrayList<>();
        Matcher nameMatcher = NAMED_ITEM_NAME.matcher(xml);
        while (nameMatcher.find() && names.size() < 5) {
            names.add(nameMatcher.group(1));
        }
        if (!names.isEmpty()) {
            meta.put("names", names);
        }

        return meta;
    }

    // ========================= Utility Methods =========================

    /**
     * Count pattern matches in text.
     */
    public int countMatches(String text, Pattern pattern) {
        if (text == null || pattern == null) {
            return 0;
        }
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Sum integer values from pattern group(1) matches.
     */
    public int sumMatches(String text, Pattern pattern) {
        if (text == null || pattern == null) {
            return 0;
        }
        int sum = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            try {
                sum += Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Skip malformed values
            }
        }
        return sum;
    }

    /**
     * Extract unique matches for a pattern group.
     */
    public java.util.Set<String> extractUniqueMatches(String text, Pattern pattern) {
        java.util.Set<String> results = new java.util.LinkedHashSet<>();
        if (text == null || pattern == null) {
            return results;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            results.add(matcher.group(1));
        }
        return results;
    }

    /**
     * Group elements by an attribute value.
     *
     * <p>For each match of elementPattern, extract the value from attrPattern
     * and count occurrences.
     */
    public Map<String, Integer> groupByPattern(String text, Pattern elementPattern, Pattern attrPattern) {
        Map<String, Integer> groups = new LinkedHashMap<>();
        if (text == null || elementPattern == null || attrPattern == null) {
            return groups;
        }

        Matcher elementMatcher = elementPattern.matcher(text);
        while (elementMatcher.find()) {
            String element = elementMatcher.group();
            Matcher attrMatcher = attrPattern.matcher(element);
            if (attrMatcher.find()) {
                groups.merge(attrMatcher.group(1), 1, Integer::sum);
            }
        }
        return groups;
    }

    /**
     * Format metadata map as a readable string.
     *
     * @param metadata Metadata map from extract()
     * @return Formatted string for MCP response
     */
    public String formatMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                sb.append(capitalize(key)).append(":\n");
                @SuppressWarnings("unchecked")
                Map<String, ?> map = (Map<String, ?>) value;
                for (Map.Entry<String, ?> e : map.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                }
            } else if (value instanceof java.util.Collection) {
                sb.append(capitalize(key)).append(": ");
                @SuppressWarnings("unchecked")
                java.util.Collection<?> coll = (java.util.Collection<?>) value;
                sb.append(String.join(", ", coll.stream().map(Object::toString).toList())).append("\n");
            } else {
                sb.append(capitalize(key)).append(": ").append(value).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Capitalize the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // Convert camelCase to Title Case
        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(str.charAt(0)));
        for (int i = 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(c);
        }
        return result.toString();
    }
}
