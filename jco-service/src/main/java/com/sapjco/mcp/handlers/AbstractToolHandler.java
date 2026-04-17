package com.sapjco.mcp.handlers;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.JcoSessionManager;
import com.sapjco.mcp.service.MetadataExtractorService;
import com.sapjco.mcp.util.ObjectNameEncoder;
import com.sapjco.mcp.util.ParameterExtractor;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Abstract base class for all MCP tool handlers.
 * Provides common services and helper methods for handlers.
 *
 * <p>Benefits of extending this class:
 * <ul>
 *   <li>Common service injection (systemConfigLoader, jcoSessionManager, adtClient, fileStorageService)</li>
 *   <li>Helper methods for parameter extraction and validation</li>
 *   <li>Helper methods for system resolution and file path construction</li>
 *   <li>Consistent response formatting</li>
 * </ul>
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link ToolHandler#getToolDefinition()} - Define the tool schema</li>
 *   <li>{@link ToolHandler#handle} - Implement the tool logic</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractToolHandler implements ToolHandler {

    @Autowired
    protected SystemConfigLoader systemConfigLoader;

    @Autowired
    protected JcoSessionManager jcoSessionManager;

    @Autowired
    protected AdtClient adtClient;

    @Autowired
    protected FileStorageService fileStorageService;

    @Autowired
    protected MetadataExtractorService metadataExtractorService;

    // ==================== Parameter Extraction ====================

    /**
     * Extract a required string parameter. Returns an error result if missing.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return null if valid; a CallToolResult error otherwise
     */
    protected CallToolResult validateRequired(Map<String, Object> args, String name) {
        return ParameterExtractor.validateRequiredString(args, name);
    }

    /**
     * Extract a required string parameter. Throws if missing.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return The non-empty string value
     * @throws IllegalArgumentException if missing or empty
     */
    protected String requireString(Map<String, Object> args, String name) {
        return ParameterExtractor.requireString(args, name);
    }

    /**
     * Extract an optional string parameter.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return The string value, or null if not present
     */
    protected String optionalString(Map<String, Object> args, String name) {
        return ParameterExtractor.optionalString(args, name);
    }

    /**
     * Extract an optional string parameter with a default value.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @return The string value, or default if not present
     */
    protected String optionalString(Map<String, Object> args, String name, String defaultValue) {
        return ParameterExtractor.optionalString(args, name, defaultValue);
    }

    /**
     * Extract an optional boolean parameter with a default value.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @return The boolean value, or default if not present
     */
    protected boolean optionalBoolean(Map<String, Object> args, String name, boolean defaultValue) {
        return ParameterExtractor.optionalBoolean(args, name, defaultValue);
    }

    /**
     * Extract an optional integer parameter with a default value.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @return The integer value, or default if not present
     */
    protected int optionalInt(Map<String, Object> args, String name, int defaultValue) {
        return ParameterExtractor.optionalInt(args, name, defaultValue);
    }

    /**
     * Extract an optional integer parameter with a default value and maximum limit.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @param maxValue Maximum allowed value
     * @return The integer value clamped to maxValue, or default if not present
     */
    protected int optionalInt(Map<String, Object> args, String name, int defaultValue, int maxValue) {
        return ParameterExtractor.optionalInt(args, name, defaultValue, maxValue);
    }

    /**
     * Extract an optional Integer (nullable) parameter.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return The Integer value, or null if not present
     */
    protected Integer optionalInteger(Map<String, Object> args, String name) {
        return ParameterExtractor.optionalInteger(args, name);
    }

    /**
     * Check if a string parameter is present and non-empty.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return true if present and non-empty
     */
    protected boolean hasString(Map<String, Object> args, String name) {
        return ParameterExtractor.hasString(args, name);
    }

    /**
     * Validate that exactly one of two parameters is provided.
     *
     * @param args Map of request arguments
     * @param param1 First parameter name
     * @param param2 Second parameter name
     * @return null if exactly one is present; a CallToolResult error otherwise
     */
    protected CallToolResult validateExactlyOne(Map<String, Object> args, String param1, String param2) {
        return ParameterExtractor.validateExactlyOne(args, param1, param2);
    }

    // ==================== System Resolution ====================

    /**
     * Resolve the system ID, preferring session's system over explicit system_id.
     *
     * @param sessionId Session ID (may be null)
     * @param systemId Explicit system ID (may be null)
     * @return The resolved system ID
     * @throws Exception if neither session nor system can be resolved
     */
    protected String resolveSystemId(String sessionId, String systemId) throws Exception {
        if (sessionId != null) {
            JcoSession session = jcoSessionManager.getSession(sessionId);
            return session.getSystemId();
        }
        ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);
        return resolved.getSystemId();
    }

    /**
     * Resolve the system configuration.
     *
     * @param systemId System ID (may be null to use default)
     * @return The resolved system configuration
     * @throws Exception if system cannot be resolved
     */
    protected ResolvedSystem resolveSystem(String systemId) throws Exception {
        return systemConfigLoader.getSystem(systemId);
    }

    /**
     * Build the system file ID for file storage paths.
     * Format: {systemId}_{client} (e.g., "erx_001")
     *
     * @param resolved The resolved system
     * @return System file identifier
     */
    protected String getSystemFileId(ResolvedSystem resolved) {
        return resolved.getSystemId() + "_" + resolved.getConfig().getClient();
    }

    // ==================== Object Name Encoding ====================

    /**
     * Encode an object name for use in URL paths.
     * Handles namespaced objects (e.g., /SCMTMS/CL_TOR).
     *
     * @param name Object name
     * @return Encoded name suitable for URLs
     */
    protected String encodeObjectName(String name) {
        return ObjectNameEncoder.encode(name);
    }

    // ==================== Response Formatting ====================

    /**
     * Create a success response with system header.
     *
     * @param resolved The resolved system
     * @param content Response content
     * @return Success CallToolResult
     */
    protected CallToolResult success(ResolvedSystem resolved, String content) {
        String systemHeader = McpResponseFormatter.formatSystemHeader(
                resolved.getSystemId(),
                resolved.getConfig().getEffectiveHost(),
                resolved.getConfig().getClient()
        );
        return McpResponseFormatter.success(systemHeader, content);
    }

    /**
     * Create an error response.
     *
     * @param message Error message
     * @return Error CallToolResult
     */
    protected CallToolResult error(String message) {
        return McpResponseFormatter.error(message);
    }

    /**
     * Create an error response from an exception.
     *
     * @param e Exception
     * @return Error CallToolResult
     */
    protected CallToolResult error(Exception e) {
        return McpResponseFormatter.error(e);
    }

    /**
     * Format a file-based response with metadata.
     *
     * @param resolved The resolved system
     * @param filePath Path to the saved file
     * @param content Content (for excerpt generation)
     * @param extension File extension for excerpt formatting
     * @param metadataLines Additional metadata lines to include before file info
     * @return Formatted success response
     */
    protected CallToolResult formatFileResponse(ResolvedSystem resolved, Path filePath, String content,
                                                String extension, String... metadataLines) {
        StringBuilder sb = new StringBuilder();

        // Add custom metadata lines
        for (String line : metadataLines) {
            sb.append(line).append("\n");
        }

        // Add file info
        try {
            long byteSize = fileStorageService.getByteSize(filePath);
            sb.append(String.format(Locale.ROOT, "Size: %,d bytes\n", byteSize));
        } catch (Exception e) {
            // Ignore size errors
        }
        sb.append(String.format("File: %s\n", filePath));

        // Add content excerpt
        sb.append(fileStorageService.formatExcerptSection(content, extension));

        return success(resolved, sb.toString());
    }

    /**
     * Format a source code response with standard metadata.
     *
     * @param resolved The resolved system
     * @param objectType Object type (e.g., "Class", "Interface")
     * @param objectName Object name
     * @param version Version (e.g., "active", "inactive")
     * @param filePath Path to the saved file
     * @param content Source code content
     * @return Formatted success response
     */
    protected CallToolResult formatSourceResponse(ResolvedSystem resolved, String objectType, String objectName,
                                                  String version, Path filePath, String content) {
        try {
            long lineCount = fileStorageService.getLineCount(filePath);
            long byteSize = fileStorageService.getByteSize(filePath);

            return formatFileResponse(resolved, filePath, content, FileStorageService.EXT_ABAP,
                    String.format("%s: %s (version: %s)", objectType, objectName.toUpperCase(), version),
                    String.format(Locale.ROOT, "Lines: %,d", lineCount));
        } catch (Exception e) {
            return formatFileResponse(resolved, filePath, content, FileStorageService.EXT_ABAP,
                    String.format("%s: %s (version: %s)", objectType, objectName.toUpperCase(), version));
        }
    }

    /**
     * Format a class include source code response with standard metadata.
     *
     * @param resolved The resolved system
     * @param className Class name
     * @param includeType Include type (e.g., "definitions", "implementations")
     * @param version Version (e.g., "active", "inactive")
     * @param filePath Path to the saved file
     * @param content Source code content
     * @return Formatted success response
     */
    protected CallToolResult formatClassIncludeResponse(ResolvedSystem resolved, String className, String includeType,
                                                        String version, Path filePath, String content) {
        try {
            long lineCount = fileStorageService.getLineCount(filePath);
            long byteSize = fileStorageService.getByteSize(filePath);

            return formatFileResponse(resolved, filePath, content, FileStorageService.EXT_ABAP,
                    String.format("Class Include: %s/%s (version: %s)", className.toUpperCase(), includeType, version),
                    String.format(Locale.ROOT, "Lines: %,d", lineCount));
        } catch (Exception e) {
            return formatFileResponse(resolved, filePath, content, FileStorageService.EXT_ABAP,
                    String.format("Class Include: %s/%s (version: %s)", className.toUpperCase(), includeType, version));
        }
    }

    /**
     * Format a dictionary object response with metadata.
     *
     * @param resolved The resolved system
     * @param objectType Object type (e.g., "Table", "Domain", "Data Element")
     * @param objectName Object name
     * @param version Version (e.g., "active", "inactive")
     * @param filePath Path to the saved file
     * @param content XML content
     * @return Formatted success response
     */
    protected CallToolResult formatDictionaryResponse(ResolvedSystem resolved, String objectType, String objectName,
                                                       String version, Path filePath, String content) {
        try {
            long byteSize = fileStorageService.getByteSize(filePath);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s: %s\n", objectType, objectName.toUpperCase()));
            sb.append(String.format("Version: %s\n", version));
            sb.append(String.format(Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));
            sb.append(fileStorageService.formatExcerptSection(content, FileStorageService.EXT_XML));

            return success(resolved, sb.toString());
        } catch (Exception e) {
            log.warn("Error getting file size for {}", filePath, e);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s: %s\n", objectType, objectName.toUpperCase()));
            sb.append(String.format("Version: %s\n", version));
            sb.append(String.format("File: %s\n", filePath));
            sb.append(fileStorageService.formatExcerptSection(content, FileStorageService.EXT_XML));
            return success(resolved, sb.toString());
        }
    }

    /**
     * Format a CDS view source response with metadata.
     *
     * @param resolved The resolved system
     * @param cdsViewName CDS view name
     * @param version Version (e.g., "active", "inactive")
     * @param filePath Path to the saved file
     * @param content DDL source content
     * @return Formatted success response
     */
    protected CallToolResult formatCdsViewResponse(ResolvedSystem resolved, String cdsViewName,
                                                    String version, Path filePath, String content) {
        try {
            long lineCount = fileStorageService.getLineCount(filePath);
            long byteSize = fileStorageService.getByteSize(filePath);

            return formatFileResponse(resolved, filePath, content, FileStorageService.EXT_DDL,
                    String.format("CDS View: %s (version: %s)", cdsViewName.toUpperCase(), version),
                    String.format(Locale.ROOT, "Lines: %,d", lineCount));
        } catch (Exception e) {
            return formatFileResponse(resolved, filePath, content, FileStorageService.EXT_DDL,
                    String.format("CDS View: %s (version: %s)", cdsViewName.toUpperCase(), version));
        }
    }

    /**
     * Format an XML response with metadata.
     *
     * @param resolved The resolved system
     * @param filePath Path to the saved file
     * @param content XML content
     * @param metadata Extracted metadata map
     * @param headerLines Header lines to include before metadata
     * @return Formatted success response
     */
    protected CallToolResult formatXmlResponse(ResolvedSystem resolved, Path filePath, String content,
                                               Map<String, Object> metadata, String... headerLines) {
        StringBuilder sb = new StringBuilder();

        // Add header lines
        for (String line : headerLines) {
            sb.append(line).append("\n");
        }

        // Add extracted metadata
        if (metadata != null && !metadata.isEmpty()) {
            sb.append(metadataExtractorService.formatMetadata(metadata));
        }

        // Add file info
        try {
            long byteSize = fileStorageService.getByteSize(filePath);
            sb.append(String.format(Locale.ROOT, "Size: %,d bytes\n", byteSize));
        } catch (Exception e) {
            // Ignore size errors
        }
        sb.append(String.format("File: %s\n", filePath));

        // Add content excerpt
        sb.append(fileStorageService.formatExcerptSection(content, FileStorageService.EXT_XML));

        return success(resolved, sb.toString());
    }
}
