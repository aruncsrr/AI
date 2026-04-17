package com.sapjco.mcp.handlers.system;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.config.SystemConfigLoader.AuthType;
import com.sapjco.mcp.config.SystemConfigLoader.SystemInfo;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for ListSystemsHandler.
 */
class ListSystemsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private ListSystemsHandler handler;

    @Test
    void listSystems_success_multipleSystems() {
        // Arrange
        SystemInfo dev = createSystemInfo("dev", "https://dev.sap.com:443", "100", AuthType.snc, "Development", true);
        SystemInfo prod = createSystemInfo("prod", "https://prod.sap.com:443", "200", AuthType.snc, "Production", false);
        List<SystemInfo> systems = Arrays.asList(dev, prod);

        when(systemConfigLoader.listSystems()).thenReturn(systems);
        when(systemConfigLoader.getConfigPath()).thenReturn("/path/to/.sap-systems.json");

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Configured SAP Systems:");
        assertContains(result, "dev");
        assertContains(result, "(default)");
        assertContains(result, "prod");
        assertContains(result, "Total: 2 system(s)");
        assertContains(result, ".sap-systems.json");
    }

    @Test
    void listSystems_success_singleSystem() {
        // Arrange
        SystemInfo dev = createSystemInfo("dev", "https://dev.sap.com:443", "100", AuthType.snc, "Development", true);
        List<SystemInfo> systems = Collections.singletonList(dev);

        when(systemConfigLoader.listSystems()).thenReturn(systems);
        when(systemConfigLoader.getConfigPath()).thenReturn("/path/to/.sap-systems.json");

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "dev");
        assertContains(result, "Total: 1 system(s)");
    }

    @Test
    void listSystems_noSystemsConfigured_returnsError() {
        // Arrange
        when(systemConfigLoader.listSystems()).thenReturn(Collections.emptyList());

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "No SAP systems configured");
    }

    @Test
    void listSystems_withDescription() {
        // Arrange
        SystemInfo dev = createSystemInfo("dev", "https://dev.sap.com:443", "100", AuthType.snc, "Development System for Testing", true);
        List<SystemInfo> systems = Collections.singletonList(dev);

        when(systemConfigLoader.listSystems()).thenReturn(systems);
        when(systemConfigLoader.getConfigPath()).thenReturn("/path/to/.sap-systems.json");

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Description: Development System for Testing");
    }

    @Test
    void listSystems_exception_returnsError() {
        // Arrange
        when(systemConfigLoader.listSystems()).thenThrow(new RuntimeException("Config file not found"));

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Config file not found");
    }

    private SystemInfo createSystemInfo(String systemId, String url, String client,
                                         AuthType authType, String description, boolean isDefault) {
        SystemInfo info = mock(SystemInfo.class);
        lenient().when(info.getSystemId()).thenReturn(systemId);
        lenient().when(info.getUrl()).thenReturn(url);
        lenient().when(info.getHost()).thenReturn(url.replace("https://", "").split(":")[0]);
        lenient().when(info.getClient()).thenReturn(client);
        lenient().when(info.getAuthType()).thenReturn(authType);
        lenient().when(info.getDescription()).thenReturn(description);
        lenient().when(info.isDefault()).thenReturn(isDefault);
        return info;
    }
}
