package com.sapjco.mcp.handlers.system;

import com.sapjco.mcp.config.SystemConfigLoader.SystemInfo;
import com.sapjco.mcp.handlers.BaseHandlerTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UpdateSystemHandler.
 * Tests system configuration updates.
 */
class UpdateSystemHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private UpdateSystemHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        // No common setup - SystemInfo mocks are created per test
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Success Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void updateSystem_updateUrl() throws Exception {
        // Arrange
        SystemInfo updatedSystem = createSystemInfo("dev", "https://new-sap.example.com:44300", "001", true);
        when(systemConfigLoader.listSystems()).thenReturn(List.of(updatedSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://new-sap.example.com:44300")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully updated system");
        assertContains(result, "dev");
        assertContains(result, "url");
        verify(systemConfigLoader).updateSystem(eq("dev"), any(), isNull());
    }

    @Test
    void updateSystem_updateClient() throws Exception {
        // Arrange
        SystemInfo updatedSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "200", true);
        when(systemConfigLoader.listSystems()).thenReturn(List.of(updatedSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("client", "200")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully updated");
        assertContains(result, "client");
    }

    @Test
    void updateSystem_updateDescription() throws Exception {
        // Arrange
        SystemInfo updatedSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        when(updatedSystem.getDescription()).thenReturn("Updated description");
        when(systemConfigLoader.listSystems()).thenReturn(List.of(updatedSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("description", "Updated description")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully updated");
        assertContains(result, "description");
    }

    @Test
    void updateSystem_updateSncPartnername() throws Exception {
        // Arrange
        SystemInfo devSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        when(systemConfigLoader.listSystems()).thenReturn(List.of(devSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("snc_partnername", "p/secude:CN=NEW, O=SAP-AG, C=DE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully updated");
        assertContains(result, "snc_partnername");
    }

    @Test
    void updateSystem_updateSncQop() throws Exception {
        // Arrange
        SystemInfo devSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        when(systemConfigLoader.listSystems()).thenReturn(List.of(devSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("snc_qop", 3)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully updated");
        assertContains(result, "snc_qop");
    }

    @Test
    void updateSystem_setAsDefault() throws Exception {
        // Arrange
        SystemInfo updatedSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        when(systemConfigLoader.listSystems()).thenReturn(List.of(updatedSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("set_as_default", true)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully updated");
        assertContains(result, "default");
        verify(systemConfigLoader).updateSystem(eq("dev"), any(), eq(true));
    }

    @Test
    void updateSystem_multipleFields() throws Exception {
        // Arrange
        SystemInfo updatedSystem = createSystemInfo("dev", "https://new-sap.example.com:44300", "200", true);
        when(systemConfigLoader.listSystems()).thenReturn(List.of(updatedSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "https://new-sap.example.com:44300")
                .arg("client", "200")
                .arg("description", "New description")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "url");
        assertContains(result, "client");
        assertContains(result, "description");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void updateSystem_missingSystemId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("url", "https://new-sap.example.com:44300")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "system_id");
    }

    @Test
    void updateSystem_emptySystemId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "")
                .arg("url", "https://new-sap.example.com:44300")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "system_id");
    }

    @Test
    void updateSystem_noUpdatesProvided_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "No updates provided");
    }

    @Test
    void updateSystem_invalidUrl_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("url", "not-a-valid-url")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Invalid URL");
    }

    @Test
    void updateSystem_invalidClient_tooShort_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("client", "01")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "client");
        assertContains(result, "3-digit");
    }

    @Test
    void updateSystem_invalidClient_notNumeric_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("client", "ABC")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "client");
    }

    @Test
    void updateSystem_invalidSncQop_tooLow_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("snc_qop", 0)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "snc_qop");
    }

    @Test
    void updateSystem_invalidSncQop_tooHigh_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .arg("snc_qop", 10)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "snc_qop");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void updateSystem_systemNotFound_returnsError() throws Exception {
        // Arrange
        doThrow(new IllegalArgumentException("System 'nonexistent' not found"))
                .when(systemConfigLoader).updateSystem(eq("nonexistent"), any(), any());

        CallToolRequest request = requestBuilder()
                .arg("system_id", "nonexistent")
                .arg("url", "https://new-sap.example.com:44300")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "not found");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private SystemInfo createSystemInfo(String systemId, String url, String client, boolean isDefault) {
        SystemInfo info = mock(SystemInfo.class);
        when(info.getSystemId()).thenReturn(systemId);
        when(info.getUrl()).thenReturn(url);
        when(info.getClient()).thenReturn(client);
        when(info.getHost()).thenReturn(url.replace("https://", "").replace(":44300", ""));
        when(info.isDefault()).thenReturn(isDefault);
        return info;
    }
}
