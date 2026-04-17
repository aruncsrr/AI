# Available Tools

Complete list of MCP tools provided by the SAP ABAP MCP Server (84 tools).

## System Management

- `ListSystems` - List all configured SAP systems (without credentials)
- `AddSystem` - Add a new SNC-authenticated SAP system to configuration
- `UpdateSystem` - Update an existing system configuration
- `RemoveSystem` - Remove a system from configuration
- `GetLandscapeSystems` - Read SNC-enabled systems from SAP GUI landscape file

## Session Management

- `CreateSession` - Create stateful editing session (optionally for specific system)
- `DestroySession` - Clean up session resources
- `ListSessions` - View active sessions with system info

## Read Operations

- `GetClass` - Retrieve ABAP class source (active/inactive)
- `GetClassInclude` - Retrieve class include source (definitions, implementations, macros, testClasses)
- `GetProgram` - Retrieve ABAP program source
- `GetInterface` - Retrieve ABAP interface source (active/inactive)
- `GetFunctionGroup` - Retrieve function group source (active/inactive)
- `GetFunctionModule` - Retrieve function module source code within a function group
- `GetInclude` - Retrieve include program source (active/inactive)
- `GetBehaviorDefinition` - Retrieve RAP Behavior Definition (BDEF) source code
- `GetServiceDefinition` - Retrieve RAP Service Definition (SRVD) source code
- `GetServiceBinding` - Retrieve RAP Service Binding (SRVB) metadata XML (read-only)
- `GetTable` - Retrieve database table definition (fields, keys, technical settings)
- `GetTableFields` - Retrieve table field definitions via element info API (names, types, lengths, keys, descriptions)
- `GetStructure` - Retrieve structure definition with field components, data types, and labels
- `GetStructureFields` - Retrieve structure component definitions via element info API (names, types, lengths, descriptions)
- `GetDataElement` - Retrieve data element definition (domain, labels, documentation)
- `GetDomain` - Retrieve domain definition (data type, length, fixed values)
- `GetCDSView` - Retrieve CDS view / DDL source code
- `GetVersionHistory` - Retrieve version history for an object (author, date, transport, description)
- `GetVersionContent` - Retrieve source code for a specific version (use version IDs from GetVersionHistory)
- `CompareVersions` - Compare two versions and return a unified diff
- `GetObjectStatus` - Get ABAP object activation status (active, inactive, activeWithInactiveVersion)
- `Search` - Search repository objects with wildcards
- `GetDiscovery` - Get ADT endpoints and capabilities from discovery document
- `GetPackageContents` - Retrieve contents of an ABAP package (supports filters and pagination)
- `GetWhereUsed` - Find where ABAP objects are used (whole-object or position-based)
- `GetUsageSnippets` - Lazy load source code snippets for where-used results
- `GetObjectDocumentation` - Retrieve documentation for ABAP objects (sapscript, message_longtext, language_help)
- `GetBopfBusinessObject` - Retrieve BOPF Business Object metadata (nodes, actions, associations, determinations, validations)

## Write Operations

- `SaveClass` - Save ABAP class source (atomic lock/save/unlock, requires session)
- `SaveClassInclude` - Save class include (generic for all types: definitions, implementations, macros, testClasses, requires session)
- `SaveClassTestInclude` - Save test include (requires session)
- `SaveProgram` - Save ABAP program (requires session)
- `SaveInterface` - Save ABAP interface source (atomic lock/save/unlock, requires session)
- `SaveFunctionGroup` - Save function group source (atomic lock/save/unlock, requires session)
- `SaveFunctionModule` - Save function module source code (atomic lock/save/unlock, requires session)
- `SaveInclude` - Save include program source (atomic lock/save/unlock, requires session)
- `SaveBehaviorDefinition` - Save RAP Behavior Definition (BDEF) source code (atomic lock/save/unlock, requires session)
- `SaveServiceDefinition` - Save RAP Service Definition (SRVD) source code (atomic lock/save/unlock, requires session)
- `LockObject` - Manual object locking (requires session)
- `UnlockObject` - Manual object unlocking (requires session)

**Note:** All write operations that require transport requests will prompt for user confirmation before selecting a transport to ensure changes are written to the correct transport request.

### Create Operations (requires `SAP_ADT_DANGEROUS_OPERATIONS=true`)

- `CreateClass` - Create a new ABAP class
- `CreateInterface` - Create a new ABAP interface
- `CreateProgram` - Create a new ABAP program
- `CreateFunctionGroup` - Create a new ABAP function group
- `CreateInclude` - Create a new ABAP include program

### Delete Operations (requires `SAP_ADT_DANGEROUS_OPERATIONS=true`)

- `DeleteClass` - Delete an ABAP class (irreversible)
- `DeleteInterface` - Delete an ABAP interface (irreversible)
- `DeleteProgram` - Delete an ABAP program (irreversible)
- `DeleteFunctionGroup` - Delete an ABAP function group (irreversible)
- `DeleteInclude` - Delete an ABAP include program (irreversible)

## Generic Operations

- `ActivateObject` - Activate any object type (class, interface, program, function_group, function_module, include, behavior_definition, service_definition, service_binding)
- `CheckSyntax` - Validate syntax without activation (same types as ActivateObject)

## Transport Management

- `GetTransportRequests` - Get available transport requests for an object
- `ListTransportRequests` - List transport requests by user/status
- `GetTransportContents` - Retrieve all objects contained in a transport request or task

## Testing

- `RunAbapUnit` - Execute ABAP Unit tests with optional coverage and debug mode
- `GetCoverageResult` - Retrieve coverage summary (statement/branch/procedure percentages) from a coverage measurement URI
- `GetStatementCoverage` - Retrieve line-level coverage data for specific ABAP objects

## Code Analysis

- `RunATC` - Execute ATC (ABAP Test Cockpit) checks on ABAP objects
- `GetATCFindings` - Retrieve ATC findings by display ID (for async workflows)

## Data Preview

- `PreviewTableData` - Preview contents of DDIC tables and views (limited to 100,000 rows)
- `SelectSQLQuery` - Execute read-only Open SQL SELECT queries (client-side validation, limited to 100,000 rows)
- `PreviewCDSView` - Preview data from CDS views with optional parameters (limited to 100,000 rows)

## Debugging

- `DebugStartSession` - Start a complete debug session in one operation (set breakpoint, trigger test, attach). Combines the full debug workflow into a single synchronous call.
- `DebugWaitForBreakpoint` - Wait for a breakpoint hit after DebugStartSession with non-testclasses include_type. Blocks until hit, then attaches.
- `DebugSetBreakpoint` - Add an additional breakpoint during an active debug session (requires terminal_id and ide_id from DebugStartSession)
- `DebugDeleteBreakpoint` - Delete debug listener and breakpoints
- `DebugGetStack` - Get current call stack and execution position with source context
- `DebugGetVariables` - Get variables in debug session (@ROOT, @LOCALS, or variable ID to expand)
- `DebugStep` - Execute step operations (stepOver, stepInto, stepReturn)
- `DebugSetVariable` - Modify variable value during debugging
- `DebugStepToLine` - Step to specific line (stepRunToLine or stepJumpToLine)
- `DebugResume` - Resume execution until program ends or next breakpoint

**Note:** For SNC-authenticated systems, the `request_user` parameter is required for `DebugStartSession` and `DebugSetBreakpoint`. Unit tests triggered via REST API DO pause at external breakpoints - SAP GUI is NOT required.

## Runtime Error Analysis

- `ListRuntimeErrors` - List recent ABAP runtime errors (ST22 dumps) with filters by user, time range
- `GetRuntimeError` - Retrieve a specific runtime error dump (formatted text, metadata XML, or HTML summary)

## Formatting

- `FormatADTResponse` - Format raw SAP ADT XML/text responses into human-readable text

---

## Roadmap

Planned tools for future releases:

### Dictionary Objects (Write)
- `SaveCDSView` - Save CDS views

### Code Analysis (Advanced)
- `GetATCWorklist` - Get ATC worklist for comprehensive analysis

### Source Services
- `GetCodeCompletion` - Code completion proposals
- `PrettyPrint` - Format ABAP source code
- `GetSourceOutline` - Get source structure/outline

### Advanced Operations
- `GetObjectRelations` - Object dependency network
- `ValidateObjectName` - Validate naming conventions
- `GenerateObject` - Generate objects from templates
