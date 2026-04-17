package com.sapjco.mcp.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileStorageService.
 */
class FileStorageServiceTest {

    private FileStorageService service;

    @TempDir
    Path tempDir;

    // Test fixtures
    private static final String TEST_SYSTEM_ID = "erx_001";

    // Cross-platform base directory (matches FileStorageService.BASE_DIR)
    private static final String BASE_DIR = Path.of(
            System.getProperty("java.io.tmpdir"), "sap-mcp").toString();
    private static final String TEST_OBJECT_TYPE = "class";
    private static final String TEST_OBJECT_NAME = "ZCL_TEST_CLASS";
    private static final String TEST_SOURCE = """
            CLASS zcl_test_class DEFINITION PUBLIC FINAL CREATE PUBLIC.
              PUBLIC SECTION.
                METHODS: do_something.
            ENDCLASS.

            CLASS zcl_test_class IMPLEMENTATION.
              METHOD do_something.
                " Implementation
              ENDMETHOD.
            ENDCLASS.
            """;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup test files
        Path testPath = service.getFilePath(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME);
        if (Files.exists(testPath)) {
            Files.deleteIfExists(testPath);
            // Try to remove empty parent directories
            Path parent = testPath.getParent();
            while (parent != null && !parent.toString().equals(BASE_DIR)) {
                try {
                    Files.deleteIfExists(parent);
                    parent = parent.getParent();
                } catch (IOException e) {
                    break; // Directory not empty
                }
            }
        }
    }

    // ========================= Path Generation Tests =========================

    @Test
    void getFilePath_generatesCorrectPath() {
        Path path = service.getFilePath(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME);

        assertEquals(Path.of(BASE_DIR, "erx_001/class/ZCL_TEST_CLASS.abap").toString(), path.toString());
    }

    @Test
    void getFilePath_uppercasesObjectName() {
        Path path = service.getFilePath(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, "zcl_lowercase_class");

        assertEquals(Path.of(BASE_DIR, "erx_001/class/ZCL_LOWERCASE_CLASS.abap").toString(), path.toString());
    }

    @Test
    void getFilePath_lowercasesObjectType() {
        Path path = service.getFilePath(TEST_SYSTEM_ID, "CLASS", TEST_OBJECT_NAME);

        assertEquals(Path.of(BASE_DIR, "erx_001/class/ZCL_TEST_CLASS.abap").toString(), path.toString());
    }

    @Test
    void getFilePath_handlesInterface() {
        Path path = service.getFilePath(TEST_SYSTEM_ID, "interface", "ZIF_MY_INTERFACE");

        assertEquals(Path.of(BASE_DIR, "erx_001/interface/ZIF_MY_INTERFACE.abap").toString(), path.toString());
    }

    @Test
    void getFilePath_handlesProgram() {
        Path path = service.getFilePath(TEST_SYSTEM_ID, "program", "ZTEST_PROGRAM");

        assertEquals(Path.of(BASE_DIR, "erx_001/program/ZTEST_PROGRAM.abap").toString(), path.toString());
    }

    @Test
    void getFilePath_handlesFunctionGroup() {
        Path path = service.getFilePath(TEST_SYSTEM_ID, "function_group", "ZTEST_FG");

        assertEquals(Path.of(BASE_DIR, "erx_001/function_group/ZTEST_FG.abap").toString(), path.toString());
    }

    @Test
    void getFilePath_handlesInclude() {
        Path path = service.getFilePath(TEST_SYSTEM_ID, "include", "ZTEST_INCLUDE");

        assertEquals(Path.of(BASE_DIR, "erx_001/include/ZTEST_INCLUDE.abap").toString(), path.toString());
    }

    @Test
    void getFilePath_nullSystemId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getFilePath(null, TEST_OBJECT_TYPE, TEST_OBJECT_NAME));
    }

    @Test
    void getFilePath_emptySystemId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getFilePath("", TEST_OBJECT_TYPE, TEST_OBJECT_NAME));
    }

    @Test
    void getFilePath_nullObjectType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getFilePath(TEST_SYSTEM_ID, null, TEST_OBJECT_NAME));
    }

    @Test
    void getFilePath_nullObjectName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getFilePath(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, null));
    }

    // ========================= Write Operations Tests =========================

    @Test
    void writeSource_createsFileWithContent() throws IOException {
        Path path = service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);

        assertTrue(Files.exists(path));
        assertEquals(TEST_SOURCE, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void writeSource_createsParentDirectories() throws IOException {
        Path path = service.writeSource("new_system_123", "class", "ZCL_NEW_CLASS", TEST_SOURCE);

        try {
            assertTrue(Files.exists(path.getParent()));
            assertTrue(Files.isDirectory(path.getParent()));
        } finally {
            // Cleanup
            Files.deleteIfExists(path);
            Files.deleteIfExists(path.getParent());
            Files.deleteIfExists(path.getParent().getParent());
        }
    }

    @Test
    void writeSource_overwritesExistingFile() throws IOException {
        String originalContent = "* Original content";
        Path path = service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, originalContent);
        assertEquals(originalContent, Files.readString(path, StandardCharsets.UTF_8));

        String newContent = "* New content";
        service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, newContent);

        assertEquals(newContent, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void writeSource_returnsCorrectPath() throws IOException {
        Path path = service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);

        assertEquals(Path.of(BASE_DIR, "erx_001/class/ZCL_TEST_CLASS.abap").toString(), path.toString());
    }

    // ========================= Read Operations Tests =========================

    @Test
    void readSource_pathObject_readsFileContent() throws IOException {
        Path path = service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);

        String content = service.readSource(path);

        assertEquals(TEST_SOURCE, content);
    }

    @Test
    void readSource_pathString_readsFileContent() throws IOException {
        Path path = service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);

        String content = service.readSource(path.toString());

        assertEquals(TEST_SOURCE, content);
    }

    @Test
    void readSource_coordinates_readsFileContent() throws IOException {
        service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);

        String content = service.readSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME);

        assertEquals(TEST_SOURCE, content);
    }

    @Test
    void readSource_fileNotFound_throwsException() {
        Path nonExistent = Path.of(BASE_DIR, "nonexistent/class/ZNOEXIST.abap");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.readSource(nonExistent));

        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    void readSource_emptyFile_throwsException() throws IOException {
        Path path = service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, "");
        // Write empty content directly to bypass writeSource validation
        Files.writeString(path, "", StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.readSource(path));

        assertTrue(ex.getMessage().contains("File is empty"));
    }

    @Test
    void readSource_nullPath_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.readSource((Path) null));
    }

    @Test
    void readSource_nullPathString_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.readSource((String) null));
    }

    @Test
    void readSource_blankPathString_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.readSource("   "));
    }

    // ========================= Metadata Tests =========================

    @Test
    void getLineCount_countsLines() throws IOException {
        service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);
        Path path = service.getFilePath(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME);

        long lineCount = service.getLineCount(path);

        // TEST_SOURCE has 10 non-empty trailing lines (text block ending with \n has no trailing empty line)
        assertEquals(10, lineCount);
    }

    @Test
    void getLineCount_stringPath_countsLines() throws IOException {
        service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);
        Path path = service.getFilePath(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME);

        long lineCount = service.getLineCount(path.toString());

        assertEquals(10, lineCount);
    }

    @Test
    void getLineCount_nonExistentFile_returnsZero() throws IOException {
        long lineCount = service.getLineCount(Path.of(System.getProperty("java.io.tmpdir"), "nonexistent.abap"));

        assertEquals(0, lineCount);
    }

    @Test
    void getLineCount_nullPath_returnsZero() throws IOException {
        long lineCount = service.getLineCount((Path) null);

        assertEquals(0, lineCount);
    }

    @Test
    void getByteSize_returnsFileSize() throws IOException {
        service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);
        Path path = service.getFilePath(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME);

        long byteSize = service.getByteSize(path);

        assertEquals(TEST_SOURCE.getBytes(StandardCharsets.UTF_8).length, byteSize);
    }

    @Test
    void getByteSize_stringPath_returnsFileSize() throws IOException {
        service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);
        Path path = service.getFilePath(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME);

        long byteSize = service.getByteSize(path.toString());

        assertEquals(TEST_SOURCE.getBytes(StandardCharsets.UTF_8).length, byteSize);
    }

    @Test
    void getByteSize_nonExistentFile_returnsZero() throws IOException {
        long byteSize = service.getByteSize(Path.of(System.getProperty("java.io.tmpdir"), "nonexistent.abap"));

        assertEquals(0, byteSize);
    }

    @Test
    void getByteSize_nullPath_returnsZero() throws IOException {
        long byteSize = service.getByteSize((Path) null);

        assertEquals(0, byteSize);
    }

    // ========================= Exists Tests =========================

    @Test
    void exists_existingFile_returnsTrue() throws IOException {
        Path path = service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);

        assertTrue(service.exists(path));
    }

    @Test
    void exists_stringPath_existingFile_returnsTrue() throws IOException {
        Path path = service.writeSource(TEST_SYSTEM_ID, TEST_OBJECT_TYPE, TEST_OBJECT_NAME, TEST_SOURCE);

        assertTrue(service.exists(path.toString()));
    }

    @Test
    void exists_nonExistentFile_returnsFalse() {
        assertFalse(service.exists(Path.of(System.getProperty("java.io.tmpdir"), "nonexistent.abap")));
    }

    @Test
    void exists_nullPath_returnsFalse() {
        assertFalse(service.exists((Path) null));
    }

    @Test
    void exists_nullString_returnsFalse() {
        assertFalse(service.exists((String) null));
    }

    @Test
    void exists_blankString_returnsFalse() {
        assertFalse(service.exists("   "));
    }

    // ========================= Base Directory Tests =========================

    @Test
    void getBaseDir_returnsExpectedPath() {
        assertEquals(BASE_DIR, service.getBaseDir());
    }

    // ========================= Class Include Path Tests =========================

    @Test
    void getClassIncludeFilePath_definitions() {
        Path path = service.getClassIncludeFilePath(TEST_SYSTEM_ID, "ZCL_TEST", "definitions");

        assertEquals(Path.of(BASE_DIR, "erx_001/class_include/ZCL_TEST.definitions.abap").toString(), path.toString());
    }

    @Test
    void getClassIncludeFilePath_implementations() {
        Path path = service.getClassIncludeFilePath(TEST_SYSTEM_ID, "ZCL_TEST", "implementations");

        assertEquals(Path.of(BASE_DIR, "erx_001/class_include/ZCL_TEST.implementations.abap").toString(), path.toString());
    }

    @Test
    void getClassIncludeFilePath_testClasses() {
        Path path = service.getClassIncludeFilePath(TEST_SYSTEM_ID, "ZCL_TEST", "testClasses");

        assertEquals(Path.of(BASE_DIR, "erx_001/class_include/ZCL_TEST.testclasses.abap").toString(), path.toString());
    }

    @Test
    void getClassIncludeFilePath_macros() {
        Path path = service.getClassIncludeFilePath(TEST_SYSTEM_ID, "ZCL_TEST", "macros");

        assertEquals(Path.of(BASE_DIR, "erx_001/class_include/ZCL_TEST.macros.abap").toString(), path.toString());
    }

    @Test
    void getClassIncludeFilePath_uppercasesClassName() {
        Path path = service.getClassIncludeFilePath(TEST_SYSTEM_ID, "zcl_lowercase", "definitions");

        assertEquals(Path.of(BASE_DIR, "erx_001/class_include/ZCL_LOWERCASE.definitions.abap").toString(), path.toString());
    }

    @Test
    void getClassIncludeFilePath_lowercasesIncludeType() {
        Path path = service.getClassIncludeFilePath(TEST_SYSTEM_ID, "ZCL_TEST", "DEFINITIONS");

        assertEquals(Path.of(BASE_DIR, "erx_001/class_include/ZCL_TEST.definitions.abap").toString(), path.toString());
    }

    @Test
    void getClassIncludeFilePath_nullSystemId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getClassIncludeFilePath(null, "ZCL_TEST", "definitions"));
    }

    @Test
    void getClassIncludeFilePath_nullClassName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getClassIncludeFilePath(TEST_SYSTEM_ID, null, "definitions"));
    }

    @Test
    void getClassIncludeFilePath_nullIncludeType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getClassIncludeFilePath(TEST_SYSTEM_ID, "ZCL_TEST", null));
    }

    @Test
    void getClassIncludeFilePath_emptyClassName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getClassIncludeFilePath(TEST_SYSTEM_ID, "", "definitions"));
    }

    @Test
    void getClassIncludeFilePath_emptyIncludeType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getClassIncludeFilePath(TEST_SYSTEM_ID, "ZCL_TEST", ""));
    }

    @Test
    void writeClassIncludeSource_createsFileWithContent() throws IOException {
        Path path = service.writeClassIncludeSource(TEST_SYSTEM_ID, "ZCL_TEST", "definitions", TEST_SOURCE);

        try {
            assertTrue(Files.exists(path));
            assertEquals(TEST_SOURCE, Files.readString(path, StandardCharsets.UTF_8));
            assertEquals(Path.of(BASE_DIR, "erx_001/class_include/ZCL_TEST.definitions.abap").toString(), path.toString());
        } finally {
            // Cleanup
            Files.deleteIfExists(path);
            Files.deleteIfExists(path.getParent());
        }
    }

    // ========================= Excerpt Tests =========================

    @Test
    void getExcerpt_shortContent_returnsFullContent() {
        String shortContent = "Short content";

        String excerpt = service.getExcerpt(shortContent);

        assertEquals(shortContent, excerpt);
        assertFalse(excerpt.contains("truncated"));
    }

    @Test
    void getExcerpt_exactLimit_returnsFullContent() {
        String exactContent = "x".repeat(FileStorageService.DEFAULT_EXCERPT_CHARS);

        String excerpt = service.getExcerpt(exactContent);

        assertEquals(exactContent, excerpt);
        assertFalse(excerpt.contains("truncated"));
    }

    @Test
    void getExcerpt_longContent_truncatesWithIndicator() {
        String longContent = "x".repeat(FileStorageService.DEFAULT_EXCERPT_CHARS + 500);

        String excerpt = service.getExcerpt(longContent);

        assertTrue(excerpt.endsWith(FileStorageService.TRUNCATION_INDICATOR));
        assertEquals(FileStorageService.DEFAULT_EXCERPT_CHARS + FileStorageService.TRUNCATION_INDICATOR.length(),
                excerpt.length());
    }

    @Test
    void getExcerpt_customLimit_respectsLimit() {
        String content = "Hello World!";

        String excerpt = service.getExcerpt(content, 5);

        assertEquals("Hello" + FileStorageService.TRUNCATION_INDICATOR, excerpt);
    }

    @Test
    void getExcerpt_nullContent_returnsEmptyString() {
        String excerpt = service.getExcerpt(null);

        assertEquals("", excerpt);
    }

    @Test
    void getExcerpt_emptyContent_returnsEmptyString() {
        String excerpt = service.getExcerpt("");

        assertEquals("", excerpt);
    }

    @Test
    void formatExcerptSection_shortContent_includesHeader() {
        String content = "Short XML content";

        String section = service.formatExcerptSection(content, FileStorageService.EXT_XML);

        assertTrue(section.contains("--- Excerpt"));
        assertTrue(section.contains("of " + content.length() + " chars"));
        assertTrue(section.contains(content));
        assertFalse(section.contains("truncated"));
    }

    @Test
    void formatExcerptSection_longContent_showsTruncation() {
        String content = "x".repeat(3000);

        String section = service.formatExcerptSection(content, FileStorageService.EXT_XML);

        assertTrue(section.contains("--- Excerpt (2,000 of 3,000 chars) ---"));
        assertTrue(section.contains("truncated"));
    }

    @Test
    void formatExcerptSection_htmlContent_returnsEmpty() {
        String content = "<html><body>HTML content</body></html>";

        String section = service.formatExcerptSection(content, FileStorageService.EXT_HTML);

        assertEquals("", section);
    }

    @Test
    void formatExcerptSection_nullContent_returnsEmpty() {
        String section = service.formatExcerptSection(null, FileStorageService.EXT_XML);

        assertEquals("", section);
    }

    @Test
    void formatExcerptSection_emptyContent_returnsEmpty() {
        String section = service.formatExcerptSection("", FileStorageService.EXT_XML);

        assertEquals("", section);
    }
}
