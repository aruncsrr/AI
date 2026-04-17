# Development Guide

This guide covers Claude Code integration, available development agents, and how to contribute to the project.

## Claude Code Integration

This project includes specialized Claude Code agents and skills for MCP tool development.

### Available Agents

See [Claude Code Sub-agents documentation](https://code.claude.com/docs/en/sub-agents) for more information.

#### sap-mcp-tool-developer

Creates, updates, and fixes MCP tool implementations and their tests.

**Example:** "I need a tool to fetch CDS view source code from SAP"

#### adt-client-explorer

Explores Eclipse ADT client source code to understand SAP ADT REST API patterns.

**Requires:** `ADT_REFERENCE_PATH` environment variable pointing to your local ADT repository clone.

**Example:** "How does the ADT client handle saving ABAP programs?"

#### sadt-rest-explorer

Analyzes the SAP backend ADT REST framework (SADT_REST package).

**Example:** "How does the ADT REST framework handle content negotiation?"

### Available Skills

See [Claude Code Skills documentation](https://code.claude.com/docs/en/skills) for more information.

#### /mcp-tool-insight

Diagnostic skill for debugging MCP tools by comparing your implementation against Eclipse ADT client and SAP backend.

## Build & Test

```bash
# Build
cd jco-service && mvn clean package

# Run all tests
cd jco-service && mvn test

# Run specific test class
cd jco-service && mvn test -Dtest=GetClassHandlerTest

# Run with coverage report
cd jco-service && mvn test jacoco:report
# Coverage report at: jco-service/target/site/jacoco/index.html
```

## Project Structure

```
jco-service/
├── src/main/java/com/sapjco/mcp/
│   ├── handlers/           # MCP tool handlers (61 handlers)
│   │   ├── bopf/          # BOPF business object handlers
│   │   ├── data/          # Data preview handlers
│   │   ├── debug/         # Debugging handlers
│   │   ├── read/          # Read operation handlers
│   │   ├── session/       # Session management handlers
│   │   ├── system/        # System configuration handlers
│   │   ├── testing/       # ABAP Unit and ATC handlers
│   │   ├── transport/     # Transport management handlers
│   │   └── write/         # Write operation handlers
│   ├── mcp/               # MCP infrastructure
│   ├── service/           # Core services (AdtClient, JcoSessionManager)
│   ├── config/            # Configuration (SystemConfigLoader)
│   └── util/              # Utilities (XML parsing, etc.)
└── src/test/java/         # Unit tests
```

## Contributing

Contributions are welcome! Whether it's bug fixes, new tools, documentation improvements, or feature suggestions.

To get started:
1. Check the [roadmap](TOOLS.md#roadmap) for planned tools
2. Review `CLAUDE.md` for architecture details and coding patterns
3. Use the included Claude Code agents to assist with development

### Adding a New Handler

1. Create handler class in the appropriate category under `handlers/`
2. Implement `ToolHandler` interface
3. Register in `McpToolRegistry`
4. Add unit tests in `src/test/java/`
