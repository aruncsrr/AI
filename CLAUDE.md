# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SAP MCP ADT is a **Java MCP server** for SAP ABAP development. It provides:
- **MCP Server** (Java) - Full MCP protocol implementation via STDIO
- **88 Tools** - Complete SAP ADT toolset for reading, writing, and managing ABAP code
- **JCo Integration** - Stateful SAP session management via JCo RFC

The project enables AI assistants to safely modify ABAP code with proper locking, atomic save operations, and session management.

## Build and Development Commands

```bash
# Build
cd jco-service && mvn clean package

# Run (MCP STDIO mode - for Claude Code)
java -jar jco-service/target/jco-service-1.0.0.jar --mcp

# Test
cd jco-service && mvn test

# Test single class
cd jco-service && mvn test -Dtest=ClassName
```

- Java 17, Spring Boot 3.2.1, MCP SDK 0.10.0
- Tests are formatter-focused (114 test files in `src/test/`), no SAP connection required
- Handler code: `jco-service/src/main/java/com/sapjco/mcp/handlers/{category}/`
- Formatter code: `jco-service/src/main/java/com/sapjco/mcp/formatters/{category}/`

## Architecture

### Single-Process Model
- Java handles MCP protocol via STDIO transport (MCP Java SDK)
- JCo provides stateful SAP RFC connections
- Spring Boot manages dependency injection and lifecycle

### Key Directories
- `jco-service/src/main/java/com/sapjco/mcp/handlers/` - MCP tool handlers (88 handlers)
- `jco-service/src/main/java/com/sapjco/mcp/service/` - Core services (AdtClient, JcoSessionManager)
- `jco-service/src/main/java/com/sapjco/mcp/mcp/` - MCP infrastructure (McpToolRegistry, McpResponseFormatter)
- `jco-service/src/main/java/com/sapjco/mcp/config/` - Configuration (SystemConfigLoader)

### Handler Categories
| Category | Handlers | Description |
|----------|----------|-------------|
| system | 5 | System management (ListSystems, Add/Remove/Update) |
| session | 3 | Session lifecycle (Create/Destroy/List) |
| write | 24 | Create, save, and delete operations for all ABAP objects |
| read | 29 | Get source code, search, where-used, field info |
| transport | 3 | Transport request management |
| testing | 9 | ABAP Unit, ATC, coverage, ATC Result Browser |
| data | 3 | Table/CDS data preview |
| debug | 10 | Debug session management |
| bopf | 1 | BOPF business object metadata |
| format | 1 | FormatADTResponse for human-readable output |

### Session Flow
```
CreateSession → SaveClass (session_id) → ActivateObject (session_id) → DestroySession
```

### CSRF Token Best Practice

**CRITICAL**: All ADT API calls MUST use session-specific CSRF tokens to avoid race conditions.

**Problem:** Global CSRF tokens can become stale when multiple requests run concurrently, causing intermittent "CSRF token validation failed" errors.

**Solution Pattern:**
1. Always create a temporary session if one isn't provided
2. Pass session ID to ADT request methods
3. Clean up temporary sessions in a finally block

**Why it works:**
- Session-specific tokens avoid race conditions between concurrent requests
- `sap-adt-connection-id` header ensures SAP connection affinity
- Isolated token storage prevents cross-request contamination

### Handler Return Format
Handlers return MCP-compliant responses:
```java
{ "isError": boolean, "content": [{"type": "text", "text": "..."}] }
```

### Transport Request Best Practice

**CRITICAL**: When transport requests are required to save non-local objects, ALWAYS ask the user for confirmation before selecting a transport request.

**Solution Pattern:**
1. Use `GetTransportRequests` to fetch available transports
2. Present the list to the user
3. NEVER automatically select a transport without explicit user confirmation
4. Only proceed with save after receiving user confirmation

### Large Payload Handling

**Problem:** Tools like `GetPackageContents` and `GetWhereUsed` can return large datasets that cause memory issues or UI hangs.

**Solution Pattern (aligned with Eclipse ADT):**

1. **Server-Side Result Limiting**
   - `GetWhereUsed`: Uses `maximumNumberOfResults` attribute in request XML
   - Server limits results before returning, reducing bandwidth and timeout risk

2. **Client-Side Result Limiting with `max_results`**
   - `GetWhereUsed`: Default 100, max 1000
   - `GetPackageContents`: Default 500, max 5000

3. **Hard Cap with Clear Error**
   - If package exceeds 5000 objects without filters, return error with guidance

4. **Client-Side Filtering**
   - `GetPackageContents` supports `object_type` filter (e.g., "CLAS", "PROG")
   - `GetPackageContents` supports `name_pattern` wildcard filter (e.g., "Z*TEST*")

### Unified File-Based Architecture

**Problem:** Large responses (ABAP source, XML results, data previews) passed inline fill up the LLM context window.

**Solution:** All tools that return substantial data use file-based output. Only metadata summaries + file paths are returned in MCP responses.

**Path Structure:**
```
/tmp/sap-mcp/{system_id}_{client}/{category}/{filename}.{extension}
```

**Categories:**
| Category | Tools | Extension |
|----------|-------|-----------|
| class, interface, program, function_group, include | Get*/Save* source handlers | .abap |
| class_include | GetClassInclude, SaveClassInclude | .abap |
| behavior_definition | GetBehaviorDefinition, SaveBehaviorDefinition | .abap |
| service_definition | GetServiceDefinition, SaveServiceDefinition | .abap |
| service_binding | GetServiceBinding | .xml |
| cds_view | GetCDSView | .ddl |
| version | GetVersionContent | .abap |
| diff | CompareVersions | .diff |
| version_history | GetVersionHistory | .xml |
| where_used | GetWhereUsed | .xml |
| usage_snippets | GetUsageSnippets | .xml |
| search | Search | .xml |
| package | GetPackageContents | .xml |
| bopf | GetBopfBusinessObject | .xml |
| transport | GetTransportContents | .xml |
| abap_unit | RunAbapUnit | .xml |
| atc | RunATC, GetATCFindings, ListATCResults, GetATCResult, ListATCCheckVariants, GetATCFindingDocumentation | .xml/.html |
| coverage | GetCoverageResult, GetStatementCoverage | .xml |
| data | PreviewTableData, PreviewCDSView, SelectSQLQuery | .xml |
| dictionary/* | GetTable, GetStructure, GetDataElement, GetDomain, etc. | .xml |
| documentation | GetObjectDocumentation | .html |
| debug | DebugGetStack, DebugGetVariables | .xml |
| runtime_errors | ListRuntimeErrors, GetRuntimeError | .xml/.txt/.html |

**Workflow:**
```
1. GetClass(class_name=ZTEST_FHP_ADT)
   → Writes source to /tmp/sap-mcp/erx_001/class/ZTEST_FHP_ADT.abap
   → Returns summary: "File: /tmp/.../ZTEST_FHP_ADT.abap | Lines: 82 | Size: 2,341 bytes"

2. Edit the file using Edit tool
   → Edit(/tmp/sap-mcp/erx_001/class/ZTEST_FHP_ADT.abap, old_string, new_string)

3. SaveClass(source_file=/tmp/sap-mcp/erx_001/class/ZTEST_FHP_ADT.abap, ...)
   → Reads file from disk → Sends to SAP ADT API → Returns: "Saved ZTEST_FHP_ADT | HTTP 200"

4. ActivateObject(object_name=ZTEST_FHP_ADT, ...)
   → Returns: "activationExecuted=true"
```

Save tools accept either `source_code` (inline) or `source_file` (file path). Exactly one is required.

### Where-Used Lazy Loading Pattern (ADT-Aligned)

**Problem:** Getting all source locations for widely-used objects is expensive and causes timeouts.

**Solution:** Two-phase lazy loading:
1. **GetWhereUsed** → lightweight object references with `objectIdentifier` values
2. **GetUsageSnippets** → on-demand source locations for specific `objectIdentifiers`

## Multi-System Support

Supports multiple SAP systems via `.sap-systems.json`. Use `ListSystems` to see configured systems.

- **SNC auth (recommended)**: SSO via Kerberos (JCo/RFC) + X.509 certificates (HTTP/ADT). See `docs/MIGRATION.md`
- Basic auth and .env fallback are deprecated (removed in v2.0.0)
- Sessions are bound to a system at creation time
- Read operations accept `system_id` parameter directly
- Responses include `[systemId | host | client]` header

## JCo Configuration

**Critical**: `sapjco3.jar` MUST come before app JAR in classpath. Native library path set via `DYLD_LIBRARY_PATH` (macOS ARM64).

## Environment Variables

**Single-system mode (fallback):**
Required: `SAP_URL`, `SAP_USERNAME`, `SAP_PASSWORD`, `SAP_CLIENT`

**Multi-system mode:**
Create `.sap-systems.json` instead (see Multi-System Support above)

**Dangerous operations (Create/Delete tools):**

| Variable | Default | Description |
|----------|---------|-------------|
| `SAP_ADT_DANGEROUS_OPERATIONS` | `false` | Enable Create* and Delete* tools |

The Create and Delete tools (CreateClass, DeleteClass, CreateInterface, DeleteInterface, etc.) are disabled by default as a safety measure. To enable them:

```bash
export SAP_ADT_DANGEROUS_OPERATIONS=true
```

**Affected tools (10):**
- CreateClass, CreateInterface, CreateProgram, CreateFunctionGroup, CreateInclude
- DeleteClass, DeleteInterface, DeleteProgram, DeleteFunctionGroup, DeleteInclude

When disabled, the MCP server exposes 78 tools instead of 88.

## Supported ABAP Object Types

class, interface, program, function_group, include, behavior_definition, service_definition, service_binding

## Reference Implementation

For understanding how ADT tools use the SAP API, refer to the Eclipse ADT plugin source.
Set `ADT_REFERENCE_PATH` in your `.env` file to point to your local clone of the ADT plugin source.

## ADT API Design: Object-Specific Pattern

SAP ADT uses **object-specific endpoints**, not generic ones. Each ABAP object type has its own URI path:

| Object Type | Endpoint Pattern |
|------------|------------------|
| Class | `/sap/bc/adt/oo/classes/{name}` |
| Interface | `/sap/bc/adt/oo/interfaces/{name}` |
| Program | `/sap/bc/adt/programs/programs/{name}` |
| Function Group | `/sap/bc/adt/functions/groups/{name}` |
| Function Module | `/sap/bc/adt/functions/groups/{group}/fmodules/{name}` |
| BOPF Business Object | `/sap/bc/adt/bopf/businessobjects/{name}` |
| Behavior Definition | `/sap/bc/adt/bo/behaviordefinitions/{name}` |
| Service Definition | `/sap/bc/adt/ddic/srvd/sources/{name}` |
| Service Binding | `/sap/bc/adt/businessservices/bindings/{name}` |

**When adding MCP tools:** Create object-specific tools (`GetClass`, `SaveClass`) rather than generic tools (`GetObject(type, name)`). This allows handling object-specific behaviors cleanly.

**Operations use query parameters:** `POST /sap/bc/adt/oo/classes/{name}?_action=LOCK`

## ADT API Design: Generic APIs (Cross-Cutting Operations)

Some operations are **generic** and work across all object types:

| API | Endpoint | Purpose |
|-----|----------|---------|
| Activation | `/sap/bc/adt/activation` | Activate multiple mixed types simultaneously |
| Search | `/sap/bc/adt/repository/informationsystem/search` | Query objects regardless of type |
| Navigation | `/sap/bc/adt/navigation/target` | Navigate from any object type |
| Relationships | `/sap/bc/adt/objectrelations/components` | Cross-type dependencies |

**Design principle:** SAP separates generic cross-cutting operations (activation, search) from type-specific CRUD (dedicated endpoint per type).

## ADT Tools Reference Map

See `docs/TOOLS.md` for complete endpoint mapping. Key pattern: object-specific URIs at `/sap/bc/adt/{type-path}/{name}/source/main`.

All source endpoints follow: `{baseUri}/source/main[?version=active|inactive]#start=line,col;end=line,col`

## Implemented Tools

See MCP tool descriptions for per-tool details. 88 concrete handlers organized in: system (5), session (3), write (24), read (29), transport (3), testing (9), data (3), debug (10), bopf (1), format (1).

### Key Debugging Notes

- Unit tests triggered via REST API (`RunAbapUnit` with `debug_mode: true`) DO pause at external breakpoints. SAP GUI is NOT required
- For SNC-authenticated systems, you MUST ask the user for their SAP username before calling debug tools that require `request_user`
- Line numbers match exactly what SAP stores - no offset or transformation. Every blank line counts

**Simplified Debug Workflow (Testclasses):**
```
1. CreateSession                              → Get session_id
2. DebugStartSession                          → All-in-one: breakpoint + trigger + attach
3. DebugGetStack (use JCo session_id)        → See current position
4. DebugGetVariables @LOCALS                  → See local variables
5. DebugStep/DebugResume                      → Control execution
6. DestroySession (JCo session_id)            → Clean up
```

**Split Workflow (Non-testclasses):**
```
1. CreateSession → 2. DebugStartSession(include_type: "implementations") → returns immediately
3. RunAbapUnit(debug_mode: true) → triggers execution
4. DebugWaitForBreakpoint(session_id) → blocks until hit
5. DebugGetStack / DebugGetVariables / DebugStep → debug as normal
6. DestroySession → clean up
```

**Adding breakpoints mid-session:** Use `DebugSetBreakpoint` with `terminal_id` and `ide_id` from `DebugStartSession`

## Tool Roadmap - Remaining

### Phase 2 - Testing (Advanced)
- **GetTestCoverage** - Detailed code coverage analysis
- **GetAbapUnitMetadata** - ABAP Unit capabilities

### Phase 3 - Dictionary Objects (Write)
- **SaveCDSView** - Save CDS view

### Phase 4 - Code Analysis / ATC (Advanced)
- **GetATCWorklist** - Get ATC worklist for comprehensive analysis

### Phase 5 - Source Services
- **GetCodeCompletion** - Code completion proposals
- **PrettyPrint** - Format ABAP source code
- **GetSourceOutline** - Get source structure/outline

### Phase 6 - Advanced Operations
- **GetObjectRelations** - Object dependency network
- **ValidateObjectName** - Validate naming conventions
- **GenerateObject** - Generate objects from templates
