package com.sapjco.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapjco.mcp.mcp.McpToolRegistry;
import com.sapjco.mcp.mcp.ToolHandler;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * SAP MCP ADT Server - Main Application.
 *
 * This Spring Boot application provides a full MCP server for SAP ABAP development.
 * It can run in two modes:
 * - MCP mode (--mcp): STDIO transport for MCP client communication
 * - HTTP mode (default): REST API for the legacy Node.js proxy
 *
 * The MCP mode is the primary deployment model, replacing the dual-process
 * architecture with a single JVM.
 */
@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class McpServerApplication implements CommandLineRunner {

    private final McpToolRegistry toolRegistry;

    public static void main(String[] args) {
        // Check if running in MCP mode (STDIO)
        boolean mcpMode = false;
        for (String arg : args) {
            if ("--mcp".equals(arg) || "-m".equals(arg)) {
                mcpMode = true;
                break;
            }
        }

        if (mcpMode) {
            // MCP mode: Disable web server, use STDIO
            log.info("Starting SAP MCP ADT Server in MCP mode (STDIO)");
            new SpringApplicationBuilder(McpServerApplication.class)
                    .web(WebApplicationType.NONE)
                    .profiles("mcp")
                    .run(args);
        } else {
            // HTTP mode: Standard Spring Boot web server
            log.info("Starting SAP MCP ADT Server in HTTP mode");
            SpringApplication.run(McpServerApplication.class, args);
        }
    }

    @Override
    public void run(String... args) throws Exception {
        // Check if MCP mode
        boolean mcpMode = false;
        for (String arg : args) {
            if ("--mcp".equals(arg) || "-m".equals(arg)) {
                mcpMode = true;
                break;
            }
        }

        if (mcpMode) {
            startMcpServer();
        } else {
            // HTTP mode - Spring Boot handles the web server
            log.info("SAP JCo MCP Service ready on port 8090");
            log.info("  - Health check: http://localhost:8090/actuator/health");
            log.info("  - Session API: http://localhost:8090/jco/session/create");
            log.info("  - Use --mcp flag to run in MCP STDIO mode");
        }
    }

    /**
     * Start the MCP server with STDIO transport.
     */
    private void startMcpServer() {
        log.info("Initializing MCP STDIO server with {} tools", toolRegistry.getHandlerCount());

        // Create transport provider for STDIO communication
        McpServerTransportProvider transportProvider =
                new StdioServerTransportProvider(new ObjectMapper());

        // Build the server - ServerCapabilities constructor:
        // (completions, experimental, logging, prompts, resources, tools)
        McpServer.SyncSpecification serverSpec = McpServer.sync(transportProvider)
                .serverInfo("sap-mcp-adt", "2.0.0")
                .instructions("SAP ABAP Development Tools MCP Server. Provides tools for reading, writing, " +
                        "and managing ABAP code in SAP systems. Use CreateSession to establish a stateful " +
                        "session before write operations.")
                .capabilities(new ServerCapabilities(
                        null, // completions
                        null, // experimental
                        null, // logging
                        null, // prompts
                        null, // resources
                        new ServerCapabilities.ToolCapabilities(null) // tools
                ));

        // Register all tools from the registry
        for (String toolName : toolRegistry.getToolNames()) {
            ToolHandler handler = toolRegistry.getHandler(toolName).orElseThrow();
            Tool tool = handler.getToolDefinition();

            // SDK passes Map<String, Object> as arguments, we convert to CallToolRequest
            serverSpec = serverSpec.tool(tool, (exchange, arguments) -> {
                try {
                    // Create CallToolRequest from tool name and arguments Map
                    CallToolRequest request = new CallToolRequest(toolName, arguments);
                    return handler.handle(exchange, request);
                } catch (Exception e) {
                    log.error("Tool '{}' failed: {}", toolName, e.getMessage(), e);
                    return new CallToolResult(
                            java.util.List.of(new TextContent("Error: " + e.getMessage())),
                            true
                    );
                }
            });
        }

        // Build and start
        McpSyncServer server = serverSpec.build();

        log.info("SAP MCP ADT Server started on STDIO");
        log.info("  - Tools registered: {}", toolRegistry.getHandlerCount());
        log.info("  - Tools: {}", String.join(", ", toolRegistry.getToolNames()));

        // The server will block here, processing STDIO requests
        // Spring Boot lifecycle manages shutdown
    }
}
