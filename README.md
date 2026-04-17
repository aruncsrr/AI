# SAP ABAP MCP Server

MCP server for SAP ABAP development via ADT (ABAP Development Tools) REST APIs and JCo (Java Connector).

## Platform Support

Works on Windows and macOS:

| Platform | Architecture | Status |
|----------|-------------|--------|
| Windows | x64 | ✓ Supported |
| macOS | Intel (x64) | ✓ Supported |
| macOS | Apple Silicon (ARM64) | ✓ Supported |

## Quick Start

For detailed setup instructions for prerequisites, you can refer to the following guides:

- **macOS**: [Claude Code on Mac How-To](https://github.wdf.sap.corp/sap-managed-tm-ewm/ai-modernization/blob/main/ai-foundation/ClaudeCodeOnMacHowTo.md)
- **Windows**: [Claude Code on Windows How-To](https://github.wdf.sap.corp/sap-managed-tm-ewm/ai-modernization/blob/main/ai-foundation/ClaudeCodeOnWindowsHowTo.md)

## Architecture

MCP server with Spring Boot JCo service to access ADT APIs in ABAP backends.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for component-level details and data flow diagrams.

## Build

### macOS / Linux

```bash
cd jco-service && mvn clean package
```

### Windows
**Note: JCo library needs to be registered with Maven for the first time:
```bash
./mvnw.cmd install:install-file -Dfile="<your_path>\ai-sap-abap-adt\sapjco3-ntamd64-3.1.13\sapjco3.jar" -DgroupId="com.sap" -DartifactId="sapjco3" -Dversion="3.1.13" -Dpackaging="jar"
```

```cmd
cd jco-service
mvnw clean package
```

## Configure SAP Connection

Create `.sap-systems.json` in the project root with SNC authentication:

```json
{
  "systems": {
    "dev": {
      "url": "https://dev-sap.example.com:44300",
      "client": "100",
      "authType": "snc",
      "snc": {
        "partnername": "p/secude:CN=DEV, O=EXAMPLE, C=US",
        "qop": 9
      },
      "description": "Development system"
    }
  },
  "default": "dev"
}
```

SNC provides Single Sign-On using your OS credentials.

**Configuration Path:** By default, the MCP server looks for `.sap-systems.json` in the current working directory. To use a fixed location, set the `SAP_SYSTEMS_CONFIG` environment variable to the full path of your config file.

**Alternative:** You can also manage systems via MCP tools:
- `GetLandscapeSystems` - Import SNC-enabled systems from your SAP GUI landscape file
- `AddSystem` / `UpdateSystem` / `RemoveSystem` - Manage systems programmatically
- `ListSystems` - View configured systems

## Configure Claude Code

### macOS

```bash
# Replace paths with the actual paths
claude mcp add sap-adt --scope user --transport stdio -e LOADER_PATH="/path/to/sapjco3-darwinarm64-3.1.13/sapjco3.jar" -e SNC_LIB="/Applications/Secure Login Client.app/Contents/MacOS/lib/libsapcrypto.dylib" -e SAP_SYSTEMS_CONFIG="/path/to/.sap-systems.json" -e DYLD_LIBRARY_PATH="/path/to/sapjco3-darwinarm64-3.1.13" -- java -jar /path/to/jco-service/target/jco-service-1.0.0.jar --mcp
```

### Windows

```cmd
# Replace paths with the actual paths
claude mcp add sap-adt --scope user --transport stdio --env SNC_LIB="C:\Program Files\SAP\FrontEnd\SecureLogin\lib\sapcrypto.dll" --env SAP_SYSTEMS_CONFIG="C:\path\to\.sap-systems.json" -- java "-Dloader.path=C:\path\to\sapjco3-ntamd64-3.1.13" "-Djava.library.path=C:\path\to\sapjco3-ntamd64-3.1.13" -jar C:\path\to\jco-service\target\jco-service-1.0.0.jar --mcp
```


## Available Tools

See [docs/TOOLS.md](docs/TOOLS.md) for complete list of 60+ tools across these categories:

- **System & Session Management** - Multi-system configuration, stateful session lifecycle
- **Read Operations** - Classes, programs, interfaces, dictionary objects, CDS views, versions
- **Write Operations** - Save with atomic lock/unlock, activation, syntax checking
- **Transport Management** - Transport requests, contents, availability checks
- **Testing & Analysis** - ABAP Unit tests, ATC checks, where-used analysis
- **Data Preview** - Table data, SQL queries, CDS view data
- **Debugging** - Breakpoints, stepping, variable inspection

## Usage Examples

```
"Show me the source code for class ZCL_MY_CLASS"
"Run ABAP Unit tests for class ZCL_MY_CLASS"
"Compare the last two versions of ZCL_MY_CLASS"
```

See [docs/USAGE-EXAMPLES.md](docs/USAGE-EXAMPLES.md) for more examples.

## Uninstall

```bash
claude mcp remove sap-adt
```

## Known Issues

- Write operations require JCo native libraries and proper SAP RFC permissions
- Session management is required for all write operations
- On Windows, ensure Java is in your PATH environment variable

## Security

See [docs/SECURITY.md](docs/SECURITY.md) for a security assessment including known vulnerabilities and recommendations for safe usage.

## Development & Contributing

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for Claude Code integration, available agents/skills, and contribution guidelines.
