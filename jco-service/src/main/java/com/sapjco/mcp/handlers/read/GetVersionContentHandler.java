package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.JcoSessionManager;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for GetVersionContent tool.
 * Retrieves source code for a specific version of an ABAP object via RFC proxy.
 * Writes results to file and returns metadata summary.
 *
 * This tool fetches the version history first to obtain the content link,
 * then retrieves the version content using that link. This matches the
 * Eclipse ADT client behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetVersionContentHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name", "Name of the ABAP object (e.g., \"ZCL_MY_CLASS\", \"ZTEST_PROGRAM\")")
                .requiredEnum("object_type", "Type of object: class, interface, program, include, function_group",
                        List.of("class", "interface", "program", "include", "function_group"))
                .requiredString("version_id",
                        "Version ID to retrieve: \"00000\" or \"active\" for active, \"99999\" or \"inactive\" " +
                        "for inactive, or a historical version ID from GetVersionHistory")
                .optionalEnum("include_type",
                        "For classes only: main, definitions, implementations, testClasses (default: main)",
                        List.of("main", "definitions", "implementations", "testClasses"), "main")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetVersionContent",
                        "Get the source code of a specific version of an ABAP object. Use GetVersionHistory " +
                        "first to find available version IDs. Version IDs: \"00000\" or \"active\" for active " +
                        "version, \"99999\" or \"inactive\" for inactive version, or historical version IDs. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        String versionId = (String) args.get("version_id");
        String includeType = args.get("include_type") != null ? (String) args.get("include_type") : "main";
        String systemId = (String) args.get("system_id");
        String sessionId = (String) args.get("session_id");

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }
        if (versionId == null || versionId.isEmpty()) {
            return McpResponseFormatter.error("version_id is required");
        }

        // Normalize version_id
        String normalizedVersionId = normalizeVersionId(versionId);

        log.info("GetVersionContent called: {} (type: {}, version: {}, session: {})",
                objectName, objectType, normalizedVersionId, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Step 1: Fetch version history to get the content link
            String versionsUri = buildVersionsUri(objectType, objectName, includeType);
            log.debug("Fetching version history from: {}", versionsUri);

            String versionHistoryXml = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, versionsUri, null, "application/atom+xml;type=feed");
            });

            // Step 2: Extract content link for the target version
            String contentLink = extractContentLinkForVersion(versionHistoryXml, normalizedVersionId);
            if (contentLink == null) {
                throw new RuntimeException("Version " + normalizedVersionId + " not found in version history for " + objectName);
            }

            log.debug("Found content link for version {}: {}", normalizedVersionId, contentLink);

            // Step 3: Fetch source from the content link
            String sourceCode = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.getSourceCodeViaRfc(dest, session, contentLink, null, "text/plain");
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Build filename with version info
            String sanitizedName = fileStorageService.sanitizeFilename(objectName.toUpperCase());
            String filename = sanitizedName + "_v" + normalizedVersionId;
            if ("class".equals(objectType) && !"main".equals(includeType)) {
                filename = sanitizedName + "." + includeType.toLowerCase() + "_v" + normalizedVersionId;
            }

            // Write version content to file
            Path filePath = fileStorageService.writeFile(systemFileId, FileStorageService.CAT_VERSION, filename, sourceCode, FileStorageService.EXT_ABAP);
            long lineCount = fileStorageService.getLineCount(filePath);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote version content to file: {} ({} bytes)", filePath, byteSize);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Version: %s (%s", objectName.toUpperCase(), objectType));
            if ("class".equals(objectType) && !"main".equals(includeType)) {
                sb.append("/").append(includeType);
            }
            sb.append(") - ").append(formatVersionId(normalizedVersionId)).append("\n");
            sb.append(String.format(java.util.Locale.ROOT, "Lines: %,d\n", lineCount));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(sourceCode, FileStorageService.EXT_ABAP));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetVersionContent failed for {} version {}", objectName, versionId, e);
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
