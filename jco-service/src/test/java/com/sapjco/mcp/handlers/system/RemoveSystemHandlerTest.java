package com.sapjco.mcp.handlers.system;

import com.sapjco.mcp.config.SystemConfigLoader.SystemInfo;
import com.sapjco.mcp.handlers.BaseHandlerTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RemoveSystemHandler.
 * Tests system removal from configuration.
 */
class RemoveSystemHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private RemoveSystemHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        // No common setup - SystemInfo mocks are created per test
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Success Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void removeSystem_success() throws Exception {
        // Arrange
        SystemInfo devSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        SystemInfo prodSystem = createSystemInfo("prod", "https://prod-sap.example.com:44300", "100", false);
        when(systemConfigLoader.listSystems())
                .thenReturn(List.of(devSystem, prodSystem))  // Before removal
                .thenReturn(List.of(devSystem));  // After removal

        CallToolRequest request = requestBuilder()
                .arg("system_id", "prod")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully removed system");
        assertContains(result, "prod");
        verify(systemConfigLoader).removeSystem("prod");
    }

    @Test
    void removeSystem_lastSystem() throws Exception {
        // Arrange
        SystemInfo devSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        when(systemConfigLoader.listSystems())
                .thenReturn(List.of(devSystem))  // Before removal
                .thenReturn(List.of());  // After removal (empty)

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Successfully removed");
        assertContains(result, "Remaining configured systems: 0");
        assertContains(result, "No systems configured");
    }

    @Test
    void removeSystem_showsRemovedDetails() throws Exception {
        // Arrange
        SystemInfo devSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        SystemInfo prodSystem = createSystemInfo("prod", "https://prod-sap.example.com:44300", "100", false);
        when(systemConfigLoader.listSystems())
                .thenReturn(List.of(devSystem, prodSystem))
                .thenReturn(List.of(devSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "prod")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Removed URL:");
        assertContains(result, "prod-sap.example.com");
        assertContains(result, "Removed Client:");
        assertContains(result, "100");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void removeSystem_missingSystemId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "system_id");
    }

    @Test
    void removeSystem_emptySystemId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("system_id", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "system_id");
    }

    @Test
    void removeSystem_systemNotFound_returnsError() throws Exception {
        // Arrange
        SystemInfo devSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        SystemInfo prodSystem = createSystemInfo("prod", "https://prod-sap.example.com:44300", "100", false);
        when(systemConfigLoader.listSystems())
                .thenReturn(List.of(devSystem, prodSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", "nonexistent")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "not found");
        assertContains(result, "nonexistent");
        assertContains(result, "dev, prod");  // Available systems
    }

    @Test
    void removeSystem_systemNotFound_noSystems_returnsError() throws Exception {
        // Arrange
        when(systemConfigLoader.listSystems()).thenReturn(List.of());

        CallToolRequest request = requestBuilder()
                .arg("system_id", "nonexistent")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "not found");
        assertContains(result, "(none)");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void removeSystem_cannotRemoveDefault_returnsError() throws Exception {
        // Arrange
        SystemInfo devSystem = createSystemInfo("dev", "https://dev-sap.example.com:44300", "001", true);
        SystemInfo prodSystem = createSystemInfo("prod", "https://prod-sap.example.com:44300", "100", false);
        when(systemConfigLoader.listSystems())
                .thenReturn(List.of(devSystem, prodSystem));
        doThrow(new IllegalStateException("Cannot remove default system while other systems exist"))
                .when(systemConfigLoader).removeSystem("dev");

        CallToolRequest request = requestBuilder()
                .arg("system_id", "dev")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Cannot remove default");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private SystemInfo createSystemInfo(String systemId, String url, String client, boolean isDefault) {
        SystemInfo info = mock(SystemInfo.class);
        lenient().when(info.getSystemId()).thenReturn(systemId);
        lenient().when(info.getUrl()).thenReturn(url);
        lenient().when(info.getClient()).thenReturn(client);
        lenient().when(info.getHost()).thenReturn(url.replace("https://", "").replace(":44300", ""));
        lenient().when(info.isDefault()).thenReturn(isDefault);
        return info;
    }
}
