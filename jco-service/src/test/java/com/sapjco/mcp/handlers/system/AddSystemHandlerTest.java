package com.sapjco.mcp.handlers.system;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AddSystemHandler.
 * Tests system addition with SNC authentication configuration.
 */
class AddSystemHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private AddSystemHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        // No special setup needed - systemConfigLoader is already mocked
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Success Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void addSystem_success() throws Exception {
        // Arrange
        when(systemConfigLoader.listSystems()).thenReturn(java.util.List.of());
        when(systemConfigLoader.getConfigPath()).thenReturn("/path/to/.sap-systems.json");

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV, O=SAP-AG, C=DE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully added system");
        assertContains(result, "dev");
        verify(systemConfigLoader).addSystem(eq("dev"), any());
    }

    @Test
    void addSystem_withDescription() throws Exception {
        // Arrange
        when(systemConfigLoader.listSystems()).thenReturn(java.util.List.of());
        when(systemConfigLoader.getConfigPath()).thenReturn("/path/to/.sap-systems.json");

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV, O=SAP-AG, C=DE")
                .arg("description", "Development system")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Development system");
    }

    @Test
    void addSystem_withCustomSncQop() throws Exception {
        // Arrange
        when(systemConfigLoader.listSystems()).thenReturn(java.util.List.of());
        when(systemConfigLoader.getConfigPath()).thenReturn("/path/to/.sap-systems.json");

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV, O=SAP-AG, C=DE")
                .arg("snc_qop", 3)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "SNC QoP:       3");
    }

    @Test
    void addSystem_withSetAsDefault() throws Exception {
        // Arrange
        when(systemConfigLoader.listSystems()).thenReturn(java.util.List.of());
        when(systemConfigLoader.getConfigPath()).thenReturn("/path/to/.sap-systems.json");

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV, O=SAP-AG, C=DE")
                .arg("set_as_default", true)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(systemConfigLoader).updateSystem(eq("dev"), any(), eq(true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases - Missing Required Fields
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void addSystem_missingSystemId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "system_id");
    }

    @Test
    void addSystem_missingUrl_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "url");
    }

    @Test
    void addSystem_missingClient_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("snc_partnername", "p/secude:CN=DEV")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "client");
    }

    @Test
    void addSystem_missingSncPartnername_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "snc_partnername");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases - Invalid Formats
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void addSystem_invalidSystemId_uppercase_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "DEV")  // uppercase not allowed
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "system_id");
        assertContains(result, "lowercase");
    }

    @Test
    void addSystem_invalidSystemId_startsWithNumber_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "1dev")  // can't start with number
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "system_id");
        assertContains(result, "letter");
    }

    @Test
    void addSystem_invalidUrl_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "not-a-valid-url")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Invalid URL");
    }

    @Test
    void addSystem_invalidClient_tooShort_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "01")  // should be 3 digits
                .arg("snc_partnername", "p/secude:CN=DEV")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "client");
        assertContains(result, "3-digit");
    }

    @Test
    void addSystem_invalidClient_notNumeric_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "ABC")  // should be numeric
                .arg("snc_partnername", "p/secude:CN=DEV")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "client");
    }

    @Test
    void addSystem_invalidSncQop_tooLow_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV")
                .arg("snc_qop", 0)  // must be 1-9
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "snc_qop");
    }

    @Test
    void addSystem_invalidSncQop_tooHigh_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://dev-sap.example.com:44300")
                .arg("client", "001")
                .arg("snc_partnername", "p/secude:CN=DEV")
                .arg("snc_qop", 10)  // must be 1-9
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "snc_qop");
    }
}
