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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetObjectDocumentation tool.
 * Retrieves documentation for ABAP objects via RFC proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetObjectDocumentationHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name",
                        "Name of the ABAP object or document (e.g., \"ZCL_MY_CLASS\", \"ZMSGCLASS\")")
                .requiredEnum("doc_type",
                        "Type of documentation: sapscript (SE61 docs), message_longtext (message long texts), " +
                        "language_help (keyword help)",
                        List.of("sapscript", "message_longtext", "language_help"))
                .optionalString("object_type",
                        "For sapscript: Object type to auto-infer doc_class (class, interface, program, " +
                        "function_module, function_group, table, structure, data_element, domain, type_group). " +
                        "If not provided, infers from object_name pattern.")
                .optionalString("doc_class",
                        "For sapscript: Document class override. Auto-inferred if not provided " +
                        "(cl=class, if=interface, re=program, fu=function_module, fg=function_group, " +
                        "tb=table/structure, de=data_element, do=domain, ty=type_group).")
                .optionalString("message_class",
                        "For message_longtext: Message class name")
                .optionalString("message_number",
                        "For message_longtext: 3-digit message number (e.g., \"001\")")
                .optionalString("source_uri",
                        "For language_help: Source URI with position fragment " +
                        "(e.g., \"/sap/bc/adt/oo/classes/ZCL_TEST/source/main#start=10,5\")")
                .optionalString("source_code",
                        "For language_help: ABAP source code to analyze (sent as POST body)")
                .optionalString("language",
                        "Language code. Accepts both ISO (EN, DE) and SAP format (e, d). Default: EN")
                .optionalEnum("mode",
                        "For sapscript: Output mode. adteleminfo=shorter element info, nostyle=raw HTML without styling.",
                        List.of("adteleminfo", "nostyle"))
                .optionalString("session_id", "Optional session ID for connection affinity")
                .optionalString("system_id",
                        "Optional SAP system ID (e.g., \"dev\", \"prod\")")
                .buildTool(
                        "GetObjectDocumentation",
                        "Get documentation for ABAP objects. Supports multiple documentation types: " +
                        "sapscript (SE61-style technical docs), message_longtext (message class long texts), " +
                        "and language_help (ABAP keyword documentation with source context)."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String docType = (String) args.get("doc_type");
        String objectType = (String) args.get("object_type");
        String docClass = (String) args.get("doc_class");
        String messageClass = (String) args.get("message_class");
        String messageNumber = (String) args.get("message_number");
        String sourceUri = (String) args.get("source_uri");
        String sourceCode = (String) args.get("source_code");
        String language = args.get("language") != null ? (String) args.get("language") : "EN";
        String mode = (String) args.get("mode");
        String systemId = (String) args.get("system_id");
        String sessionId = (String) args.get("session_id");

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (docType == null || docType.isEmpty()) {
            return McpResponseFormatter.error("doc_type is required");
        }

        log.info("GetObjectDocumentation called: {} (type: {}, docType: {}, session: {})",
                objectName, objectType, docType, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;
            String response;

            switch (docType.toLowerCase()) {
                case "sapscript":
                    response = getSapscriptDoc(finalSessionId, resolved, objectName, objectType, docClass, language, mode);
                    break;
                case "message_longtext":
                    if (messageClass == null || messageNumber == null) {
                        return McpResponseFormatter.error(
                                "message_class and message_number are required for message_longtext doc_type");
                    }
                    response = getMessageLongtext(finalSessionId, resolved, messageClass, messageNumber, language);
                    break;
                case "language_help":
                    if (sourceUri == null && sourceCode == null) {
                        return McpResponseFormatter.error(
                                "Either source_uri or source_code is required for language_help doc_type");
                    }
                    response = getLanguageHelp(finalSessionId, resolved, sourceUri, sourceCode);
                    break;
                default:
                    return McpResponseFormatter.error("Invalid doc_type: " + docType);
            }

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            String sanitizedName = fileStorageService.sanitizeFilename(objectName.toUpperCase());
            Path filePath = fileStorageService.writeHtml(systemFileId, sanitizedName + "_" + docType, response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote documentation to file: {} ({} bytes)", filePath, byteSize);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Documentation: %s (%s)\n", objectName.toUpperCase(), docType));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetObjectDocumentation failed for {}", objectName, e);
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

    private String getSapscriptDoc(String sessionId, ResolvedSystem resolved, String objectName, String objectType,
                                    String docClass, String language, String mode) throws Exception {
        // Infer doc_class if not provided
        String inferredDocClass = docClass;
        if (inferredDocClass == null && objectType != null) {
            inferredDocClass = inferDocClass(objectType);
        }
        if (inferredDocClass == null) {
            inferredDocClass = "cl"; // Default to class
        }

        // Correct endpoint: /sap/bc/adt/documentation/sapscript/documents/{doc_class}/{object_name}
        String encodedName = URLEncoder.encode(objectName.toLowerCase(), StandardCharsets.UTF_8);
        String path = "/sap/bc/adt/documentation/sapscript/documents/" + inferredDocClass + "/" + encodedName;

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("language", normalizeLanguage(language));
        if (mode != null) {
            queryParams.put("mode", mode);
        }

        return jcoSessionManager.executeInContext(sessionId, (dest, session) -> {
            return adtClient.getSourceCodeViaRfc(dest, session, path, queryParams,
                    "application/vnd.sap.adt.docu.v1+html, text/html");
        });
    }

    private String getMessageLongtext(String sessionId, ResolvedSystem resolved, String messageClass,
                                       String messageNumber, String language) throws Exception {
        // Correct endpoint: /sap/bc/adt/messageclass/{class}/messages/{number}/longtext
        String paddedNumber = String.format("%03d", Integer.parseInt(messageNumber));
        String path = "/sap/bc/adt/messageclass/" +
                URLEncoder.encode(messageClass.toUpperCase(), StandardCharsets.UTF_8) +
                "/messages/" + paddedNumber + "/longtext";

        return jcoSessionManager.executeInContext(sessionId, (dest, session) -> {
            return adtClient.getSourceCodeViaRfc(dest, session, path, null,
                    "application/vnd.sap.adt.docu.v1+html, text/html");
        });
    }

    private String getLanguageHelp(String sessionId, ResolvedSystem resolved, String sourceUri, String sourceCode)
            throws Exception {
        // Correct endpoint: /sap/bc/adt/docu/abap/langu
        String path = "/sap/bc/adt/docu/abap/langu";

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("format", "eclipse");
        queryParams.put("language", "EN");

        if (sourceUri != null) {
            queryParams.put("uri", sourceUri);
            return jcoSessionManager.executeInContext(sessionId, (dest, session) -> {
                return adtClient.postTextViaRfc(dest, session, path, queryParams,
                        sourceCode != null ? sourceCode : "", "text/plain",
                        "application/vnd.sap.adt.docu.v1+html, text/html");
            });
        }
        throw new IllegalArgumentException("source_uri is required for language_help");
    }

    private String inferDocClass(String objectType) {
        switch (objectType.toLowerCase()) {
            case "class": return "cl";
            case "interface": return "if";
            case "program": return "re";
            case "function_module": return "fu";
            case "function_group": return "fg";
            case "table":
            case "structure": return "tb";
            case "data_element": return "de";
            case "domain": return "do";
            case "type_group": return "ty";
            default: return null;
        }
    }

    private String normalizeLanguage(String language) {
        // Convert ISO codes to SAP internal codes if needed
        if ("EN".equalsIgnoreCase(language)) return "E";
        if ("DE".equalsIgnoreCase(language)) return "D";
        return language.toUpperCase();
    }
}
