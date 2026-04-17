package com.sapjco.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for managing file-based storage of all MCP tool responses.
 *
 * <p>Implements a unified file-based pattern where all substantial responses
 * (source code, XML results, diffs, etc.) are stored on disk and only
 * metadata summaries + file paths are returned in MCP responses.
 *
 * <p>Path structure:
 * {@code /tmp/sap-mcp/{system_id}_{client}/{category}/{filename}.{ext}}
 *
 * <p>Supported categories:
 * <ul>
 *   <li>Source: class, interface, program, function_group, include, class_include</li>
 *   <li>CDS: cds_view</li>
 *   <li>Version: version, diff, version_history</li>
 *   <li>Analysis: where_used, usage_snippets, search, package</li>
 *   <li>Testing: abap_unit, atc, coverage</li>
 *   <li>Data: data (table/CDS previews, SQL queries)</li>
 *   <li>BOPF: bopf</li>
 *   <li>Transport: transport</li>
 *   <li>Dictionary: dictionary/table, dictionary/structure, dictionary/data_element, etc.</li>
 *   <li>Documentation: documentation</li>
 *   <li>Debug: debug</li>
 *   <li>Runtime Errors: runtime_errors</li>
 * </ul>
 */
@Slf4j
@Service
public class FileStorageService {

    private static final String BASE_DIR = Paths.get(
            System.getProperty("java.io.tmpdir"), "sap-mcp").toString();
    private static final String FILE_EXTENSION = ".abap";

    // File extensions by category
    public static final String EXT_ABAP = ".abap";
    public static final String EXT_XML = ".xml";
    public static final String EXT_DDL = ".ddl";
    public static final String EXT_DIFF = ".diff";
    public static final String EXT_HTML = ".html";
    public static final String EXT_JSON = ".json";
    public static final String EXT_PDF = ".pdf";
    public static final String EXT_DOCX = ".docx";
    public static final String EXT_PPTX = ".pptx";
    public static final String EXT_XLSX = ".xlsx";

    // Category constants for source code
    public static final String CAT_CLASS = "class";
    public static final String CAT_INTERFACE = "interface";
    public static final String CAT_PROGRAM = "program";
    public static final String CAT_FUNCTION_GROUP = "function_group";
    public static final String CAT_INCLUDE = "include";
    public static final String CAT_CLASS_INCLUDE = "class_include";

    // Category constants for CDS
    public static final String CAT_CDS_VIEW = "cds_view";

    // Category constants for versioning
    public static final String CAT_VERSION = "version";
    public static final String CAT_DIFF = "diff";
    public static final String CAT_VERSION_HISTORY = "version_history";

    // Category constants for analysis
    public static final String CAT_WHERE_USED = "where_used";
    public static final String CAT_USAGE_SNIPPETS = "usage_snippets";
    public static final String CAT_SEARCH = "search";
    public static final String CAT_PACKAGE = "package";

    // Category constants for testing
    public static final String CAT_ABAP_UNIT = "abap_unit";
    public static final String CAT_ATC = "atc";
    public static final String CAT_COVERAGE = "coverage";

    // Category constants for syntax check
    public static final String CAT_SYNTAX = "syntax";

    // Category constants for data preview
    public static final String CAT_DATA = "data";

    // Category constants for BOPF
    public static final String CAT_BOPF = "bopf";

    // Category constants for RAP objects
    public static final String CAT_BEHAVIOR_DEFINITION = "behavior_definition";
    public static final String CAT_SERVICE_DEFINITION = "service_definition";
    public static final String CAT_SERVICE_BINDING = "service_binding";

    // Category constants for transport
    public static final String CAT_TRANSPORT = "transport";

    // Category constants for dictionary
    public static final String CAT_DICT_TABLE = "dictionary/table";
    public static final String CAT_DICT_TABLE_FIELDS = "dictionary/table_fields";
    public static final String CAT_DICT_STRUCTURE = "dictionary/structure";
    public static final String CAT_DICT_STRUCTURE_FIELDS = "dictionary/structure_fields";
    public static final String CAT_DICT_DATA_ELEMENT = "dictionary/data_element";
    public static final String CAT_DICT_DOMAIN = "dictionary/domain";

    // Category constants for documentation
    public static final String CAT_DOCUMENTATION = "documentation";

    // Category constants for debugging
    public static final String CAT_DEBUG = "debug";

    // Category constants for runtime errors
    public static final String CAT_RUNTIME_ERRORS = "runtime_errors";

    // Category constants for Wiki
    public static final String CAT_WIKI = "wiki";

    // Category constants for generated documents
    public static final String CAT_DOCUMENTS = "documents";

    // Excerpt configuration
    public static final int DEFAULT_EXCERPT_CHARS = 2000;
    public static final String TRUNCATION_INDICATOR = "\n... (truncated)";

    /**
     * Get a truncated excerpt of content for inclusion in MCP responses.
     *
     * <p>Uses the default character limit of {@link #DEFAULT_EXCERPT_CHARS}.
     *
     * @param content Full content
     * @return Truncated excerpt with indicator if truncated, or full content if shorter
     */
    public String getExcerpt(String content) {
        return getExcerpt(content, DEFAULT_EXCERPT_CHARS);
    }

    /**
     * Get a truncated excerpt of content with a custom character limit.
     *
     * <p>If the content is shorter than maxChars, returns the full content without
     * any truncation indicator. If longer, returns the first maxChars characters
     * followed by a truncation indicator.
     *
     * @param content  Full content
     * @param maxChars Maximum characters to include before truncation
     * @return Truncated excerpt with indicator if truncated, or full content if shorter
     */
    public String getExcerpt(String content, int maxChars) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;  // No truncation, no indicator
        }
        return content.substring(0, maxChars) + TRUNCATION_INDICATOR;
    }

    /**
     * Format the excerpt section for MCP responses.
     *
     * <p>Creates a formatted excerpt block with header showing character counts.
     * Skips excerpt for HTML content (not useful in raw form).
     *
     * @param content   Full content
     * @param extension File extension (e.g., ".xml", ".abap", ".html")
     * @return Formatted excerpt section, or empty string for HTML
     */
    public String formatExcerptSection(String content, String extension) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // Skip excerpt for HTML - raw HTML is not useful
        if (EXT_HTML.equals(extension)) {
            return "";
        }

        String excerpt = getExcerpt(content);
        int excerptChars = Math.min(content.length(), DEFAULT_EXCERPT_CHARS);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.ROOT, "\n--- Excerpt (%,d of %,d chars) ---\n", excerptChars, content.length()));
        sb.append(excerpt);

        return sb.toString();
    }

    /**
     * Get the file path for an ABAP object.
     *
     * @param systemId   System ID (e.g., "erx_001")
     * @param objectType Object type: class, interface, program, function_group, include
     * @param objectName ABAP object name (will be uppercased)
     * @return Path to the file (may not exist yet)
     */
    public Path getFilePath(String systemId, String objectType, String objectName) {
        validateParameters(systemId, objectType, objectName);

        String normalizedType = normalizeObjectType(objectType);
        String normalizedName = objectName.toUpperCase();

        return Paths.get(BASE_DIR, systemId, normalizedType, normalizedName + FILE_EXTENSION);
    }

    /**
     * Get the file path for an ABAP class include.
     *
     * <p>Class includes use a special naming pattern with the include type as suffix:
     * {@code /tmp/sap-mcp/{system_id}/class_include/{CLASS_NAME}.{type}.abap}
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code /tmp/sap-mcp/erx_001/class_include/ZCL_TEST.definitions.abap}</li>
     *   <li>{@code /tmp/sap-mcp/erx_001/class_include/ZCL_TEST.implementations.abap}</li>
     *   <li>{@code /tmp/sap-mcp/erx_001/class_include/ZCL_TEST.testclasses.abap}</li>
     *   <li>{@code /tmp/sap-mcp/erx_001/class_include/ZCL_TEST.macros.abap}</li>
     * </ul>
     *
     * @param systemId    System ID (e.g., "erx_001")
     * @param className   ABAP class name (will be uppercased)
     * @param includeType Include type: definitions, implementations, macros, testClasses
     * @return Path to the file (may not exist yet)
     */
    public Path getClassIncludeFilePath(String systemId, String className, String includeType) {
        if (systemId == null || systemId.isBlank()) {
            throw new IllegalArgumentException("systemId is required");
        }
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className is required");
        }
        if (includeType == null || includeType.isBlank()) {
            throw new IllegalArgumentException("includeType is required");
        }

        String normalizedClass = className.toUpperCase();
        String normalizedType = includeType.toLowerCase();
        String fileName = normalizedClass + "." + normalizedType + FILE_EXTENSION;

        return Paths.get(BASE_DIR, systemId, "class_include", fileName);
    }

    /**
     * Write source code to a class include file.
     *
     * <p>Creates parent directories if they don't exist.
     *
     * @param systemId    System ID
     * @param className   ABAP class name
     * @param includeType Include type: definitions, implementations, macros, testClasses
     * @param source      Source code content
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeClassIncludeSource(String systemId, String className, String includeType, String source)
            throws IOException {
        Path filePath = getClassIncludeFilePath(systemId, className, includeType);

        // Create parent directories
        Files.createDirectories(filePath.getParent());

        // Write source to file
        Files.writeString(filePath, source, StandardCharsets.UTF_8);

        log.debug("Wrote class include source to file: {} ({} bytes)", filePath, source.length());
        return filePath;
    }

    /**
     * Write source code to a file.
     *
     * <p>Creates parent directories if they don't exist.
     *
     * @param systemId   System ID
     * @param objectType Object type
     * @param objectName ABAP object name
     * @param source     Source code content
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeSource(String systemId, String objectType, String objectName, String source)
            throws IOException {
        Path filePath = getFilePath(systemId, objectType, objectName);

        // Create parent directories
        Files.createDirectories(filePath.getParent());

        // Write source to file
        Files.writeString(filePath, source, StandardCharsets.UTF_8);

        log.debug("Wrote source to file: {} ({} bytes)", filePath, source.length());
        return filePath;
    }

    /**
     * Read source code from a file path.
     *
     * @param filePath Path to the file
     * @return Source code content
     * @throws IOException              if reading fails
     * @throws IllegalArgumentException if file doesn't exist or is empty
     */
    public String readSource(Path filePath) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("File path is required");
        }

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        if (!Files.isReadable(filePath)) {
            throw new IllegalArgumentException("Cannot read file: " + filePath);
        }

        String content = Files.readString(filePath, StandardCharsets.UTF_8);

        if (content.isEmpty()) {
            throw new IllegalArgumentException("File is empty: " + filePath);
        }

        log.debug("Read source from file: {} ({} bytes)", filePath, content.length());
        return content;
    }

    /**
     * Read source code from a file path string.
     *
     * @param filePathString Path string to the file
     * @return Source code content
     * @throws IOException              if reading fails
     * @throws IllegalArgumentException if file doesn't exist or is empty
     */
    public String readSource(String filePathString) throws IOException {
        if (filePathString == null || filePathString.isBlank()) {
            throw new IllegalArgumentException("File path is required");
        }
        return readSource(Paths.get(filePathString));
    }

    /**
     * Read source code using object coordinates.
     *
     * @param systemId   System ID
     * @param objectType Object type
     * @param objectName ABAP object name
     * @return Source code content
     * @throws IOException              if reading fails
     * @throws IllegalArgumentException if file doesn't exist or is empty
     */
    public String readSource(String systemId, String objectType, String objectName) throws IOException {
        return readSource(getFilePath(systemId, objectType, objectName));
    }

    /**
     * Get the line count of a file.
     *
     * @param filePath Path to the file
     * @return Number of lines
     * @throws IOException if reading fails
     */
    public long getLineCount(Path filePath) throws IOException {
        if (filePath == null || !Files.exists(filePath)) {
            return 0;
        }
        return Files.lines(filePath, StandardCharsets.UTF_8).count();
    }

    /**
     * Get the line count of a file.
     *
     * @param filePathString Path string to the file
     * @return Number of lines
     * @throws IOException if reading fails
     */
    public long getLineCount(String filePathString) throws IOException {
        if (filePathString == null || filePathString.isBlank()) {
            return 0;
        }
        return getLineCount(Paths.get(filePathString));
    }

    /**
     * Get the byte size of a file.
     *
     * @param filePath Path to the file
     * @return File size in bytes
     * @throws IOException if reading fails
     */
    public long getByteSize(Path filePath) throws IOException {
        if (filePath == null || !Files.exists(filePath)) {
            return 0;
        }
        return Files.size(filePath);
    }

    /**
     * Get the byte size of a file.
     *
     * @param filePathString Path string to the file
     * @return File size in bytes
     * @throws IOException if reading fails
     */
    public long getByteSize(String filePathString) throws IOException {
        if (filePathString == null || filePathString.isBlank()) {
            return 0;
        }
        return getByteSize(Paths.get(filePathString));
    }

    /**
     * Check if a file exists.
     *
     * @param filePath Path to check
     * @return true if file exists
     */
    public boolean exists(Path filePath) {
        return filePath != null && Files.exists(filePath);
    }

    /**
     * Check if a file exists.
     *
     * @param filePathString Path string to check
     * @return true if file exists
     */
    public boolean exists(String filePathString) {
        if (filePathString == null || filePathString.isBlank()) {
            return false;
        }
        return exists(Paths.get(filePathString));
    }

    // ========================= Generic File Operations =========================

    /**
     * Write content to a file with a specific category and extension.
     *
     * <p>This is the generic method for writing any type of file.
     * Creates parent directories if they don't exist.
     *
     * <p>Example paths:
     * <ul>
     *   <li>{@code /tmp/sap-mcp/erx_001/where_used/ZCL_MY_CLASS.xml}</li>
     *   <li>{@code /tmp/sap-mcp/erx_001/cds_view/I_FLIGHT.ddl}</li>
     *   <li>{@code /tmp/sap-mcp/erx_001/diff/ZCL_TEST_v123_v456.diff}</li>
     * </ul>
     *
     * @param systemId  System ID (e.g., "erx_001")
     * @param category  File category (use CAT_* constants, can include subdirs like "dictionary/table")
     * @param filename  Filename without extension (will be uppercased for ABAP objects)
     * @param content   File content
     * @param extension File extension including dot (use EXT_* constants)
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeFile(String systemId, String category, String filename, String content, String extension)
            throws IOException {
        if (systemId == null || systemId.isBlank()) {
            throw new IllegalArgumentException("systemId is required");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("extension is required");
        }

        Path filePath = Paths.get(BASE_DIR, systemId, category, filename + extension);

        // Create parent directories
        Files.createDirectories(filePath.getParent());

        // Write content to file
        Files.writeString(filePath, content, StandardCharsets.UTF_8);

        log.debug("Wrote file: {} ({} bytes)", filePath, content.length());
        return filePath;
    }

    /**
     * Write XML content to a file.
     *
     * <p>Convenience method for XML output (most common format for ADT responses).
     *
     * @param systemId System ID (e.g., "erx_001")
     * @param category File category (use CAT_* constants)
     * @param filename Filename without extension
     * @param content  XML content
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeXml(String systemId, String category, String filename, String content) throws IOException {
        return writeFile(systemId, category, filename, content, EXT_XML);
    }

    /**
     * Write unified diff content to a file.
     *
     * <p>Creates a diff file with a standardized naming pattern:
     * {@code {OBJECT_NAME}_v{versionA}_v{versionB}.diff}
     *
     * @param systemId   System ID (e.g., "erx_001")
     * @param objectName ABAP object name
     * @param versionA   First version ID
     * @param versionB   Second version ID
     * @param content    Unified diff content
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeDiff(String systemId, String objectName, String versionA, String versionB, String content)
            throws IOException {
        String filename = objectName.toUpperCase() + "_v" + sanitizeVersionId(versionA) + "_v" + sanitizeVersionId(versionB);
        return writeFile(systemId, CAT_DIFF, filename, content, EXT_DIFF);
    }

    /**
     * Write DDL source content to a file.
     *
     * <p>Used for CDS view DDL source code.
     *
     * @param systemId System ID (e.g., "erx_001")
     * @param viewName CDS view name
     * @param content  DDL source content
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeDdl(String systemId, String viewName, String content) throws IOException {
        return writeFile(systemId, CAT_CDS_VIEW, viewName.toUpperCase(), content, EXT_DDL);
    }

    /**
     * Write HTML documentation content to a file.
     *
     * @param systemId   System ID (e.g., "erx_001")
     * @param objectName Object name for documentation
     * @param content    HTML content
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeHtml(String systemId, String objectName, String content) throws IOException {
        return writeFile(systemId, CAT_DOCUMENTATION, objectName.toUpperCase() + "_doc", content, EXT_HTML);
    }

    /**
     * Get the file path for a generic file (without writing).
     *
     * @param systemId  System ID (e.g., "erx_001")
     * @param category  File category
     * @param filename  Filename without extension
     * @param extension File extension including dot
     * @return Path to the file (may not exist)
     */
    public Path getGenericFilePath(String systemId, String category, String filename, String extension) {
        return Paths.get(BASE_DIR, systemId, category, filename + extension);
    }

    /**
     * Sanitize a filename for safe file system use.
     *
     * <p>Replaces problematic characters like / and \ with underscores.
     *
     * @param name Name to sanitize
     * @return Sanitized name safe for filenames
     */
    public String sanitizeFilename(String name) {
        if (name == null) {
            return "";
        }
        return name.replace("/", "_")
                   .replace("\\", "_")
                   .replace(":", "_")
                   .replace("*", "_")
                   .replace("?", "_")
                   .replace("\"", "_")
                   .replace("<", "_")
                   .replace(">", "_")
                   .replace("|", "_");
    }

    /**
     * Sanitize version ID for use in filenames.
     */
    private String sanitizeVersionId(String versionId) {
        if (versionId == null) {
            return "unknown";
        }
        // Version IDs should be safe, but just in case
        return versionId.replace("/", "_").replace("\\", "_");
    }

    /**
     * Write JSON content to a file.
     *
     * @param systemId System ID (e.g., "wiki")
     * @param category File category (use CAT_* constants)
     * @param filename Filename without extension
     * @param content  JSON content
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeJson(String systemId, String category, String filename, String content) throws IOException {
        return writeFile(systemId, category, filename, content, EXT_JSON);
    }

    /**
     * Write binary content (PDF, DOCX, PPTX, etc.) to a file.
     *
     * <p>Creates parent directories if they don't exist.
     *
     * @param systemId  System ID (e.g., "documents")
     * @param category  File category (use CAT_* constants)
     * @param filename  Filename without extension
     * @param content   Binary content
     * @param extension File extension including dot (e.g., ".pdf", ".docx", ".pptx")
     * @return Path to the written file
     * @throws IOException if writing fails
     */
    public Path writeBinary(String systemId, String category, String filename, byte[] content, String extension)
            throws IOException {
        if (systemId == null || systemId.isBlank()) {
            throw new IllegalArgumentException("systemId is required");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }

        Path filePath = Paths.get(BASE_DIR, systemId, category, filename + extension);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content);

        log.debug("Wrote binary file: {} ({} bytes)", filePath, content.length);
        return filePath;
    }

    /**
     * Get the base directory for file storage.
     *
     * @return Base directory path
     */
    public String getBaseDir() {
        return BASE_DIR;
    }

    /**
     * Normalize object type to lowercase directory name.
     */
    private String normalizeObjectType(String objectType) {
        return objectType.toLowerCase().replace(" ", "_");
    }

    /**
     * Validate required parameters.
     */
    private void validateParameters(String systemId, String objectType, String objectName) {
        if (systemId == null || systemId.isBlank()) {
            throw new IllegalArgumentException("systemId is required");
        }
        if (objectType == null || objectType.isBlank()) {
            throw new IllegalArgumentException("objectType is required");
        }
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("objectName is required");
        }
    }
}
