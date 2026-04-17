package com.sapjco.mcp.handlers.system;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests for GetLandscapeSystemsHandler.
 * Tests parsing of SAP GUI landscape file.
 */
class GetLandscapeSystemsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetLandscapeSystemsHandler handler;

    @TempDir
    Path tempDir;

    private Path landscapeFile;

    private static final String LANDSCAPE_XML_WITH_SNC = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Landscape>
                <Messageservers>
                    <Messageserver uuid="ms1" host="dev-sap.example.com"/>
                    <Messageserver uuid="ms2" host="prod-sap.example.com"/>
                </Messageservers>
                <Services>
                    <Service type="SAPGUI" systemid="DEV" msid="ms1"
                             sncname="p/secude:CN=DEV, O=SAP-AG, C=DE" sncop="9"
                             name="Development" description="Development system"/>
                    <Service type="SAPGUI" systemid="PRD" msid="ms2"
                             sncname="p/secude:CN=PRD, O=SAP-AG, C=DE" sncop="3"
                             name="Production" description="Production system"/>
                </Services>
            </Landscape>
            """;

    private static final String LANDSCAPE_XML_MIXED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Landscape>
                <Messageservers>
                    <Messageserver uuid="ms1" host="dev-sap.example.com"/>
                    <Messageserver uuid="ms2" host="test-sap.example.com"/>
                </Messageservers>
                <Services>
                    <Service type="SAPGUI" systemid="DEV" msid="ms1"
                             sncname="p/secude:CN=DEV" sncop="9"
                             name="Development"/>
                    <Service type="SAPGUI" systemid="TST" msid="ms2"
                             name="Test"/>
                </Services>
            </Landscape>
            """;

    private static final String LANDSCAPE_XML_NO_SNC = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Landscape>
                <Messageservers>
                    <Messageserver uuid="ms1" host="test-sap.example.com"/>
                </Messageservers>
                <Services>
                    <Service type="SAPGUI" systemid="TST" msid="ms1" name="Test"/>
                </Services>
            </Landscape>
            """;

    private static final String LANDSCAPE_XML_EMPTY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Landscape>
                <Messageservers/>
                <Services/>
            </Landscape>
            """;

    @BeforeEach
    void setUp() throws Exception {
        landscapeFile = tempDir.resolve("SAPGUILandscape.xml");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Success Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getLandscapeSystems_withSncSystems() throws Exception {
        // Arrange
        Files.writeString(landscapeFile, LANDSCAPE_XML_WITH_SNC);

        CallToolRequest request = requestBuilder()
                .arg("landscape_path", landscapeFile.toString())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Found 2 SNC-enabled system(s)");
        assertContains(result, "dev");
        assertContains(result, "prd");
        assertContains(result, "p/secude:CN=DEV");
        assertContains(result, "p/secude:CN=PRD");
    }

    @Test
    void getLandscapeSystems_showsSystemDetails() throws Exception {
        // Arrange
        Files.writeString(landscapeFile, LANDSCAPE_XML_WITH_SNC);

        CallToolRequest request = requestBuilder()
                .arg("landscape_path", landscapeFile.toString())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "URL:");
        assertContains(result, "Client:");
        assertContains(result, "SNC Partner:");
        assertContains(result, "SNC QoP:");
        assertContains(result, "Description:");
    }

    @Test
    void getLandscapeSystems_filtersBySnc() throws Exception {
        // Arrange - Mixed landscape with one SNC and one non-SNC system
        Files.writeString(landscapeFile, LANDSCAPE_XML_MIXED);

        CallToolRequest request = requestBuilder()
                .arg("landscape_path", landscapeFile.toString())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Found 1 SNC-enabled system(s)");
        assertContains(result, "dev");
        assertNotContains(result, "tst");  // Non-SNC system should be filtered out
    }

    @Test
    void getLandscapeSystems_noSncSystems() throws Exception {
        // Arrange
        Files.writeString(landscapeFile, LANDSCAPE_XML_NO_SNC);

        CallToolRequest request = requestBuilder()
                .arg("landscape_path", landscapeFile.toString())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Found 0 SNC-enabled system(s)");
        assertContains(result, "No SNC-enabled systems found");
        assertContains(result, "basic auth is deprecated");
    }

    @Test
    void getLandscapeSystems_emptyLandscape() throws Exception {
        // Arrange
        Files.writeString(landscapeFile, LANDSCAPE_XML_EMPTY);

        CallToolRequest request = requestBuilder()
                .arg("landscape_path", landscapeFile.toString())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Found 0 SNC-enabled system(s)");
    }

    @Test
    void getLandscapeSystems_showsAddSystemHint() throws Exception {
        // Arrange
        Files.writeString(landscapeFile, LANDSCAPE_XML_WITH_SNC);

        CallToolRequest request = requestBuilder()
                .arg("landscape_path", landscapeFile.toString())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Use AddSystem to add");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getLandscapeSystems_fileNotFound_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("landscape_path", "/nonexistent/path/SAPGUILandscape.xml")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "not found");
    }

    @Test
    void getLandscapeSystems_invalidXml_returnsError() throws Exception {
        // Arrange
        Files.writeString(landscapeFile, "not valid xml content <>");

        CallToolRequest request = requestBuilder()
                .arg("landscape_path", landscapeFile.toString())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private void assertNotContains(CallToolResult result, String expected) {
        String text = getResultText(result);
        if (text.toLowerCase().contains(expected.toLowerCase())) {
            throw new AssertionError("Expected result NOT to contain: " + expected + ", but found in: " + text);
        }
    }
}
