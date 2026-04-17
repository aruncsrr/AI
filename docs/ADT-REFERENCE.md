# ADT API Reference

Technical reference for SAP ADT (ABAP Development Tools) REST API patterns and endpoints.

## ADT API Design: Object-Specific Pattern

SAP ADT uses **object-specific endpoints**, not generic ones. Each ABAP object type has its own URI path:

| Object Type | Endpoint Pattern |
|------------|------------------|
| Class | `/sap/bc/adt/oo/classes/{name}` |
| Interface | `/sap/bc/adt/oo/interfaces/{name}` |
| Program | `/sap/bc/adt/programs/programs/{name}` |
| Function Group | `/sap/bc/adt/functions/groups/{name}` |
| Function Module | `/sap/bc/adt/functions/groups/{group}/fmodules/{name}` |

**Why object-specific:**
- Sub-resources differ by type (classes have `/includes/definitions`, `/includes/testClasses`, etc.)
- Hierarchical relationships (function modules under function groups)
- Content types and request/response filters vary per type

**Operations use query parameters:**
```
POST /sap/bc/adt/oo/classes/{name}?_action=LOCK
POST /sap/bc/adt/oo/classes/{name}?_action=UNLOCK
```

**When adding MCP tools:** Create object-specific tools (`GetClass`, `SaveClass`, `GetProgram`, `SaveProgram`) rather than generic tools (`GetObject(type, name)`). This allows handling object-specific behaviors cleanly.

---

## ADT API Design: Generic APIs (Cross-Cutting Operations)

Some operations are **generic** and work across all object types:

| API | Endpoint | Purpose |
|-----|----------|---------|
| Activation | `/sap/bc/adt/activation` | Activate multiple mixed types simultaneously |
| Search | `/sap/bc/adt/repository/informationsystem/search` | Query objects regardless of type |
| Navigation | `/sap/bc/adt/navigation/target` | Navigate from any object type |
| Relationships | `/sap/bc/adt/objectrelations/components` | Cross-type dependencies |

**Activation pattern** - Posts array of full object URIs:
```
POST /sap/bc/adt/activation
Body: ["/sap/bc/adt/oo/classes/ZCLASS/source/main", "/sap/bc/adt/programs/programs/ZPROG/source/main"]
```

**Design principle:** SAP separates:
- **Generic cross-cutting operations** (activation, search, navigation) - one endpoint handles all types
- **Type-specific CRUD** (create, read, update, delete) - dedicated endpoint per type

**When adding MCP tools:** Use generic tools for cross-cutting operations (`ActivateObject`, `Search`) but object-specific tools for CRUD (`SaveClass`, `GetProgram`).

---

## ADT Tools Reference Map

Complete mapping of ADT REST endpoints and their implementation files for reference during implementation.

### Source Code Access Pattern

All source endpoints follow: `{baseUri}/source/main[?version=active|inactive]#start=line,col;end=line,col`

### Object-Specific Endpoints

| Category | Endpoint | ADT Reference |
|----------|----------|---------------|
| **Classes** | `/sap/bc/adt/oo/classes/{name}` | `adt.tools/com.sap.adt.oo/` |
| Class includes | `/sap/bc/adt/oo/classes/{name}/includes/{type}` | types: definitions, implementations, testClasses, macros, localTypes |
| **Interfaces** | `/sap/bc/adt/oo/interfaces/{name}` | `adt.tools/com.sap.adt.oo/` |
| **Programs** | `/sap/bc/adt/programs/programs/{name}` | `adt.tools/com.sap.adt.programs/` |
| **Includes** | `/sap/bc/adt/programs/includes/{name}` | `adt.tools/com.sap.adt.programs/` |
| **Function Groups** | `/sap/bc/adt/functions/groups/{name}` | `adt.tools/com.sap.adt.functions/` |
| **Function Modules** | `/sap/bc/adt/functions/groups/{group}/fmodules/{name}` | |
| **CDS Views** | `/sap/bc/adt/ddic/ddl/sources/{name}` | `adt.tools.cds/com.sap.adt.cds.ddl/` |
| **Tables** | `/sap/bc/adt/ddic/tables/{name}` | `adt.tools.ddic/com.sap.adt.ddic/` |
| **Packages** | `/sap/bc/adt/packages/{name}` | `adt.tools/com.sap.adt.packages/` |

### Generic/Cross-Cutting Endpoints

| Category | Endpoint | ADT Reference |
|----------|----------|---------------|
| **Activation** | `/sap/bc/adt/activation` | `adt.tools/com.sap.adt.activation/` |
| Inactive objects | `/sap/bc/adt/activation/inactiveobjects` | |
| **Search** | `/sap/bc/adt/repository/informationsystem/search` | `adt.tools/com.sap.adt.ris.search/` |
| **Where-Used** | `/sap/bc/adt/repository/informationsystem/usageReferences` | `adt.tools/com.sap.adt.ris.whereused/` |
| **Usage Snippets** | `/sap/bc/adt/repository/informationsystem/usageReferences/usageSnippets` | Lazy loading of source locations |
| **Object Relations** | `/sap/bc/adt/objectrelations/network` | |
| **Transport** | `/sap/bc/adt/cts/transportrequests/` | `adt.tools/com.sap.adt.transport/` |

### Unit Testing / ABAP Unit

| Endpoint | Purpose |
|----------|---------|
| `/sap/bc/adt/abapunit/runs` | Execute unit tests |
| `/sap/bc/adt/abapunit/coverage` | Code coverage data |
| `/sap/bc/adt/abapunit/metadata` | ABAP Unit capabilities |

ADT Reference: `adt.tools/com.sap.adt.abapunit/`

### Code Analysis / ATC

| Endpoint | Purpose |
|----------|---------|
| `/sap/bc/adt/atc/runs` | Run ATC checks |
| `/sap/bc/adt/atc/results` | Check results |
| `/sap/bc/adt/atc/findings` | Individual findings |
| `/sap/bc/adt/atc/worklist` | ATC worklist |

ADT Reference: `adt.tools/com.sap.adt.atc/`

### Source Code Services

| Endpoint | Purpose |
|----------|---------|
| `/sap/bc/adt/abapsource/codecompletion/proposal` | Code completion |
| `/sap/bc/adt/abapsource/prettyprinter` | Pretty print source |
| `/sap/bc/adt/abapsource/outline` | Source outline/structure |

### Object Generators

| Endpoint | Purpose |
|----------|---------|
| `/sap/bc/adt/repository/generators` | Generate new objects |
| `/sap/bc/adt/oo/validation/objectname` | Validate object names |

---

## Reference Implementation

For understanding how ADT tools use the SAP API, refer to the Eclipse ADT plugin source.
Set `ADT_REFERENCE_PATH` in your `.env` file to point to your local clone of the ADT plugin source.

Clone from: `https://github.wdf.sap.corp/adt/com.sap.adt`
