package com.sapjco.mcp.handlers.read;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.JcoSessionManager;
import com.sapjco.mcp.service.MetadataExtractorService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for CompareVersions tool.
 * Compares two versions of an ABAP object and returns a unified diff.
 * Writes results to file and returns metadata summary.
 *
 * This tool fetches the version history first to obtain content links,
 * then retrieves both version contents using those links and performs
 * client-side diff computation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareVersionsHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name", "Name of the ABAP object (e.g., \"ZCL_MY_CLASS\", \"ZTEST_PROGRAM\")")
                .requiredEnum("object_type", "Type of object: class, interface, program, include, function_group",
                        List.of("class", "interface", "program", "include", "function_group"))
                .requiredString("version_a",
                        "The \"from\" version ID: \"00000\" or \"active\" for active, \"99999\" or \"inactive\" " +
                        "for inactive, or a historical version ID")
                .requiredString("version_b",
                        "The \"to\" version ID: \"00000\" or \"active\" for active, \"99999\" or \"inactive\" " +
                        "for inactive, or a historical version ID")
                .optionalEnum("include_type",
                        "For classes only: main, definitions, implementations, testClasses (default: main)",
                        List.of("main", "definitions", "implementations", "testClasses"), "main")
                .optionalNumber("context_lines",
                        "Number of context lines in the diff output (default: 3, max: 20)")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "CompareVersions",
                        "Compare two versions of an ABAP object and return a unified diff. Use GetVersionHistory " +
                        "first to find available version IDs. Performs client-side diff computation (SAP ADT " +
                        "has no server-side diff endpoint). " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        String versionA = (String) args.get("version_a");
        String versionB = (String) args.get("version_b");
        String includeType = args.get("include_type") != null ? (String) args.get("include_type") : "main";
        int contextLines = args.get("context_lines") != null
                ? Math.min(((Number) args.get("context_lines")).intValue(), 20)
                : 3;
        String systemId = (String) args.get("system_id");
        String sessionId = (String) args.get("session_id");

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }
        if (versionA == null || versionA.isEmpty()) {
            return McpResponseFormatter.error("version_a is required");
        }
        if (versionB == null || versionB.isEmpty()) {
            return McpResponseFormatter.error("version_b is required");
        }

        String normalizedVersionA = normalizeVersionId(versionA);
        String normalizedVersionB = normalizeVersionId(versionB);

        log.info("CompareVersions called: {} (type: {}, {} vs {}, session: {})",
                objectName, objectType, normalizedVersionA, normalizedVersionB,
                sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Step 1: Fetch version history to get content links
            String versionsUri = buildVersionsUri(objectType, objectName, includeType);
            log.debug("Fetching version history from: {}", versionsUri);

            String versionHistoryXml = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, versionsUri, null, "application/atom+xml;type=feed");
            });

            // Step 2: Extract content links for both versions
            String contentLinkA = extractContentLinkForVersion(versionHistoryXml, normalizedVersionA);
            if (contentLinkA == null) {
                throw new RuntimeException("Version " + normalizedVersionA + " not found in version history for " + objectName);
            }

            String contentLinkB = extractContentLinkForVersion(versionHistoryXml, normalizedVersionB);
            if (contentLinkB == null) {
                throw new RuntimeException("Version " + normalizedVersionB + " not found in version history for " + objectName);
            }

            log.debug("Found content links - version A ({}): {}, version B ({}): {}",
                    normalizedVersionA, contentLinkA, normalizedVersionB, contentLinkB);

            // Step 3: Fetch both versions via RFC
            String sourceA = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.getSourceCodeViaRfc(dest, session, contentLinkA, null, "text/plain");
            });

            String sourceB = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.getSourceCodeViaRfc(dest, session, contentLinkB, null, "text/plain");
            });

            // Generate unified diff
            List<String> linesA = Arrays.asList(sourceA.split("\n", -1));
            List<String> linesB = Arrays.asList(sourceB.split("\n", -1));

            Patch<String> patch = DiffUtils.diff(linesA, linesB);
            List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                    objectName + " (version " + formatVersionId(normalizedVersionA) + ")",
                    objectName + " (version " + formatVersionId(normalizedVersionB) + ")",
                    linesA,
                    patch,
                    contextLines
            );

            // Build diff content
            StringBuilder diffContent = new StringBuilder();
            if (unifiedDiff.isEmpty()) {
                diffContent.append("No differences found between versions.\n");
            } else {
                for (String line : unifiedDiff) {
                    diffContent.append(line).append("\n");
                }
            }
            String diffText = diffContent.toString();

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write diff to file
            String sanitizedName = fileStorageService.sanitizeFilename(objectName.toUpperCase());
            Path filePath = fileStorageService.writeDiff(systemFileId, sanitizedName, normalizedVersionA, normalizedVersionB, diffText);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote version diff to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractDiffMetadata(diffText);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Comparing: %s (%s", objectName.toUpperCase(), objectType));
            if ("class".equals(objectType) && !"main".equals(includeType)) {
                sb.append("/").append(includeType);
            }
            sb.append(")\n");
            sb.append(String.format("Version A: %s\n", formatVersionId(normalizedVersionA)));
            sb.append(String.format("Version B: %s\n", formatVersionId(normalizedVersionB)));
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(diffText, FileStorageService.EXT_DIFF));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("CompareVersions failed for {} ({} vs {})", objectName, versionA, versionB, e);
            return McpResponseFormatter.error(e);
        } finally {
            if (tempSessionId != null) {
                try {
                    jcoSessionManager.destroySession(tempSessionId);
                } catch (Exception e) {
                    log.warn("Failed to destroy temp session: {}", e.getMessage());
                }
            }
        }
    }

    private String normalizeVersionId(String versionId) {
        if ("active".equalsIgnoreCase(versionId)) {
            return "00000";
        } else if ("inactive".equalsIgnoreCase(versionId)) {
            return "99999";
        }
        return versionId;
    }

    private String formatVersionId(String versionId) {
        if ("00000".equals(versionId)) {
            return "Active (00000)";
        } else if ("99999".equals(versionId)) {
            return "Inactive (99999)";
        }
        return versionId;
    }

    /**
     * Build the ADT versions endpoint URI for an ABAP object.
     * This is used to fetch the version history, which contains content links.
     */
    private String buildVersionsUri(String objectType, String objectName, String includeType) {
        String encodedName = encodeObjectName(objectName.toUpperCase());
        switch (objectType.toLowerCase()) {
            case "class":
                String incType = "main".equals(includeType) ? "main" : includeType.toLowerCase();
                return "/sap/bc/adt/oo/classes/" + encodedName + "/includes/" + incType + "/versions";
            case "interface":
                return "/sap/bc/adt/oo/interfaces/" + encodedName + "/source/main/versions";
            case "program":
                return "/sap/bc/adt/programs/programs/" + encodedName + "/source/main/versions";
            case "include":
                return "/sap/bc/adt/programs/includes/" + encodedName + "/source/main/versions";
            case "function_group":
                return "/sap/bc/adt/functions/groups/" + encodedName + "/source/main/versions";
            default:
                throw new IllegalArgumentException("Unsupported object type: " + objectType);
        }
    }

    /**
     * Extract the content link for a specific version from the Atom XML feed.
     */
    private String extractContentLinkForVersion(String atomXml, String targetVersionId) {
        Pattern entryPattern = Pattern.compile("<atom:entry[^>]*>([\\s\\S]*?)</atom:entry>");
        Matcher entryMatcher = entryPattern.matcher(atomXml);

        while (entryMatcher.find()) {
            String entry = entryMatcher.group(1);

            // Extract version ID from <atom:id>
            Pattern idPattern = Pattern.compile("<atom:id>([^<]*)</atom:id>");
            Matcher idMatcher = idPattern.matcher(entry);
            if (idMatcher.find() && targetVersionId.equals(idMatcher.group(1).trim())) {
                // Extract content src attribute
                Pattern srcPattern = Pattern.compile("<atom:content[^>]*src=\"([^\"]*)\"");
                Matcher srcMatcher = srcPattern.matcher(entry);
                if (srcMatcher.find()) {
                    return srcMatcher.group(1);
                }
            }
        }
        return null;
    }

    private String encodeObjectName(String name) {
        if (name.startsWith("/")) {
            return name.replace("/", "%2F");
        }
        return name;
    }
}
