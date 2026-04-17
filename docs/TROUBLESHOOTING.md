# Troubleshooting

## SNC Authentication Issues

### Error: "SNC not configured on server"

The SAP system doesn't have SNC enabled. Contact your SAP Basis team to enable SNC connections.

### Error: "SNC partner name not found"

The `partnername` in your configuration doesn't match the SAP server's SNC identity.

**To find the correct partner name:**
1. SAP GUI: Transaction `RZ10` -> Instance Profile -> `snc/identity/as`
2. Or ask your SAP Basis team

### Error: "Authentication failed" with SNC

1. **Windows**: Verify you're logged into your domain
2. **macOS**: Check keychain access - first connection may prompt for password
3. **Linux/macOS**: Try refreshing Kerberos credentials with `kinit`

### macOS Keychain Prompt

On first SNC connection, macOS may prompt for keychain access. This is normal - grant access to allow certificate-based authentication.

---

## Java / JAVA_HOME Setup

The JCo service requires Java 17+ and the `JAVA_HOME` environment variable to be set correctly.

### Error: JAVA_HOME is not defined

```
The JAVA_HOME environment variable is not defined correctly,
this environment variable is needed to run this program.
```

This error occurs when running `npm run build` or `npm start` because the Maven wrapper cannot find Java.

### What JAVA_HOME Should Point To

`JAVA_HOME` must point to the **root directory** of your JDK installation - the folder that contains `bin`, `lib`, etc.

| Correct | Incorrect |
|---------|-----------|
| `C:\Program Files\Java\jdk-17` | `C:\Program Files\Java\jdk-17\bin` |
| `/usr/lib/jvm/java-17-openjdk` | `/usr/lib/jvm/java-17-openjdk/bin` |

The `bin` folder contains `java.exe`/`java`, but `JAVA_HOME` should be its **parent directory**.

### Finding Your Java Installation

#### Windows

**Option 1: Check common installation paths**

Look for Java in these locations:
```
C:\Program Files\Eclipse Adoptium\jdk-*
C:\Program Files\Java\jdk-*
C:\Program Files\Microsoft\jdk-*
C:\Program Files\Zulu\zulu-*
```

**Option 2: Use the `where` command**

```cmd
where java
```

This returns something like `C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot\bin\java.exe`. The `JAVA_HOME` should be the parent directory without `\bin\java.exe`:
```
C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot
```

**Option 3: Check Windows Registry**

```powershell
Get-ItemProperty -Path "HKLM:\SOFTWARE\JavaSoft\JDK\*" -ErrorAction SilentlyContinue | Select-Object PSChildName, JavaHome
```

**Option 4: Use Eclipse ADT's bundled Java**

If you have Eclipse ADT installed, it includes a bundled JDK. To find it:

1. Open your Eclipse installation folder
2. Look in `plugins/` for a folder starting with `org.eclipse.justj.openjdk.hotspot.jre.full`
3. The full path looks like:
   ```
   C:\Users\<username>\eclipse\java-2024-03\eclipse\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_17.0.10.v20240120-1143\jre
   ```

Alternatively, check Eclipse's `eclipse.ini` file for the `-vm` entry which points to the Java executable.

#### macOS

```bash
# If installed via Homebrew
/usr/libexec/java_home -V

# Or check common paths
ls /Library/Java/JavaVirtualMachines/
ls /opt/homebrew/opt/temurin*/

# The JAVA_HOME is typically:
/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
```

#### Linux

```bash
# Find Java installation
update-alternatives --list java

# Or check common paths
ls /usr/lib/jvm/

# The JAVA_HOME is typically:
/usr/lib/jvm/java-17-openjdk-amd64
```

### Setting JAVA_HOME

#### Windows (Permanent)

**Option 1: PowerShell (User-level)**
```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot", "User")
```

**Option 2: System Properties GUI**
1. Press `Win + R`, type `sysdm.cpl`, press Enter
2. Go to **Advanced** tab -> **Environment Variables**
3. Under "User variables", click **New**
4. Variable name: `JAVA_HOME`
5. Variable value: Your JDK path (e.g., `C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot`)
6. Click OK and restart your terminal

**Option 3: Temporary (current session only)**
```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot
```

#### macOS / Linux (Permanent)

Add to your shell profile (`~/.bashrc`, `~/.zshrc`, or `~/.profile`):

```bash
export JAVA_HOME=/path/to/your/jdk
```

Then reload:
```bash
source ~/.bashrc  # or ~/.zshrc
```

### Verifying the Setup

After setting `JAVA_HOME`, open a **new terminal** and run:

```bash
# Check JAVA_HOME is set
echo $JAVA_HOME        # macOS/Linux
echo %JAVA_HOME%       # Windows cmd
$env:JAVA_HOME         # Windows PowerShell

# Verify Java works
java -version

# Test the build
npm run build
```

### Installing Java

If you don't have Java installed:

| Platform | Command |
|----------|---------|
| Windows | `winget install EclipseAdoptium.Temurin.17.JDK` |
| macOS | `brew install temurin@17` |
| Ubuntu/Debian | `sudo apt install openjdk-17-jdk` |
| Fedora/RHEL | `sudo dnf install java-17-openjdk-devel` |

Or download manually from [Eclipse Temurin](https://adoptium.net/) or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/).

---

## SAP JCo Dependency Not Found

### Error: Could not resolve dependencies for sapjco3

```
Could not resolve dependencies for project com.sapjco:jco-service:jar:1.0.0:
com.sap:sapjco3:jar:3.1.13 was not found in https://repo.maven.apache.org/maven2
```

### Why This Happens

The SAP Java Connector (JCo) is a **proprietary library** that is **not available in Maven Central**. You must obtain it from SAP and install it manually into your local Maven repository.

### Solution

#### Step 1: Obtain SAP JCo

Download the SAP JCo library from the [SAP Support Portal](https://support.sap.com/en/product/connectors/jco.html) (requires SAP S-User ID).

After downloading, extract the archive. You'll find:
- `sapjco3.jar` - The Java library
- Native library (`sapjco3.dll` on Windows, `libsapjco3.dylib` on macOS, `libsapjco3.so` on Linux)

#### Step 2: Install JCo to Local Maven Repository

Run this command to install the JAR into your local Maven repository:

```bash
mvn install:install-file \
  -Dfile=/path/to/sapjco3.jar \
  -DgroupId=com.sap \
  -DartifactId=sapjco3 \
  -Dversion=3.1.13 \
  -Dpackaging=jar
```

**Examples by platform:**

```bash
# macOS / Linux
mvn install:install-file \
  -Dfile=~/Downloads/sapjco3-macosx-arm64-3.1.13/sapjco3.jar \
  -DgroupId=com.sap \
  -DartifactId=sapjco3 \
  -Dversion=3.1.13 \
  -Dpackaging=jar

# Windows (PowerShell)
mvn install:install-file `
  -Dfile="$HOME\Downloads\sapjco3-ntamd64-3.1.13\sapjco3.jar" `
  -DgroupId=com.sap `
  -DartifactId=sapjco3 `
  -Dversion=3.1.13 `
  -Dpackaging=jar
```

#### Step 3: Clear Cached Maven Failure (if needed)

If Maven previously tried and failed to download JCo, it caches the failure. Clear it:

```bash
# macOS / Linux
rm -rf ~/.m2/repository/com/sap/sapjco3/3.1.13

# Windows (PowerShell)
Remove-Item -Recurse -Force "$HOME\.m2\repository\com\sap\sapjco3\3.1.13"
```

#### Step 4: Retry the Build

```bash
npm run build:java
```

### Native Library Setup

After installing the JAR, you also need to configure the native library path. See the main README for details on setting `DYLD_LIBRARY_PATH` (macOS), `LD_LIBRARY_PATH` (Linux), or `PATH` (Windows).

---

## Connection Issues

### Error: "CSRF token validation failed"

This can occur with concurrent requests. The MCP server uses session-specific CSRF tokens to avoid this. If you see this error:

1. Ensure you're using a session (`CreateSession`) for write operations
2. Pass the `session_id` to all subsequent operations

### Error: "Connection refused" or "ECONNREFUSED"

1. Verify the SAP system URL in `.sap-systems.json` is correct
2. Check network connectivity to the SAP system
3. Verify the SAP ICF services are active (transaction `SICF`)

### Error: "Certificate validation failed"

The server currently disables certificate validation for development use. If you need strict validation:

1. Add your SAP system's certificate to your OS trust store
2. Modify `src/lib/utils.ts` to enable `rejectUnauthorized: true`

---

## JCo Service Issues

### JCo service fails to start

1. Verify Java 17+ is installed and `JAVA_HOME` is set
2. Check that `sapjco3.jar` is installed in Maven repository
3. Verify native library path is configured correctly

### Operations fail with JCo errors

The JCo service is required for all SAP operations. Verify:
1. JCo service is running (check for Java process)
2. SAP RFC permissions are configured for your user
3. SNC configuration is correct (for SSO authentication)
