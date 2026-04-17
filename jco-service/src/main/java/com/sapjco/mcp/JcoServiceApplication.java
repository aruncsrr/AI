package com.sapjco.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SAP JCo MCP Service - Main Application.
 *
 * This Spring Boot application provides stateful JCo session management
 * for the Node.js MCP server. It enables lock handles to work correctly
 * by maintaining JCoContext across operations.
 */
@Slf4j
@SpringBootApplication
public class JcoServiceApplication {

    public static void main(String[] args) {
        log.info("=".repeat(80));
        log.info("SAP JCo MCP Service Starting");
        log.info("=".repeat(80));

        SpringApplication.run(JcoServiceApplication.class, args);

        log.info("✓ JCo Service is ready on port 8090");
        log.info("  - Health check: http://localhost:8090/actuator/health");
        log.info("  - Session API: http://localhost:8090/jco/session/create");
    }
}
