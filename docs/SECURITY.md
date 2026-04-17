# Security Assessment: SAP MCP ADT Server

This document provides a security assessment of the SAP MCP ADT server - a Java-based MCP server that enables AI assistants to modify ABAP code in SAP systems.

## Risk Summary

| Risk Level | Count | Summary |
|------------|-------|---------|
| **HIGH** | 2 | Cert validation disabled, session ID logging |
| **MEDIUM** | 4 | Weak SQL validation, no session timeout, error disclosure, HTTP internal comms |

---

## HIGH Severity Issues

### 1. HTTPS Certificate Validation Disabled

**File:** `jco-service/src/main/java/com/sapjco/mcp/service/AdtClient.java`

The ADT client may disable certificate validation for self-signed certificates, enabling Man-in-the-Middle (MITM) attacks. Attackers on the network can intercept SAP credentials and ABAP source code.

**Mitigation:** Only use on trusted networks. Consider adding SAP certificates to the Java truststore and enabling validation for production use.

---

### 2. Session IDs Logged to Console

**Files:** Multiple handlers in `jco-service/src/main/java/com/sapjco/mcp/handlers/`

Session IDs may be logged to stderr, which could enable session hijacking if logs are accessible to attackers.

**Mitigation:** Remove session ID logging or restrict log access.

---

## MEDIUM Severity Issues

### 3. SQL Query Validation is Weak

**File:** `jco-service/src/main/java/com/sapjco/mcp/handlers/data/SelectSQLQueryHandler.java`

Client-side validation only checks if queries START with `SELECT` or `WITH`, potentially allowing stacked queries or UNION injection. The SAP backend provides additional keyword validation, but the client-side check gives a false sense of security.

---

### 4. No Session Timeout

**File:** `jco-service/src/main/java/com/sapjco/mcp/service/JcoSessionManager.java`

Sessions are stored in memory indefinitely with no automatic expiration. While timestamps are tracked, they're not enforced.

---

### 5. Error Messages May Expose SAP Details

Error responses from SAP are sometimes returned verbatim, potentially leaking internal configuration details.

---

### 6. Internal Communications Use HTTP

Communication within the JCo service uses unencrypted HTTP for internal operations on localhost.

---

## What's Done Well

1. **SNC authentication (recommended)** - SSO via Kerberos/certificates avoids password storage
2. **Session-specific CSRF tokens** - Properly isolates CSRF tokens per session, avoiding race conditions
3. **Cryptographically secure session IDs** - Uses `java.util.UUID.randomUUID()`
4. **Atomic lock/save/unlock operations** - Write operations properly handle SAP object locking
5. **Transport request confirmation** - Requires user confirmation before selecting transports
6. **Row limits on data queries** - PreviewTableData and SelectSQLQuery have result limits (100k rows max)

---

## Authentication Security

### SNC Authentication (Recommended)

SNC authentication uses Kerberos/certificate-based SSO:
- No passwords stored in configuration files
- Credentials managed by the operating system
- Automatic ticket/certificate refresh

### Basic Authentication (Deprecated)

Basic authentication stores credentials in plain text:
- `.sap-systems.json` or `.env` files contain passwords
- If committed to version control, credentials are exposed
- Deprecated and will be removed in v2.0

**If using basic auth:** Verify `.gitignore` includes credential files. Migrate to SNC authentication.

---

## Recommendations for Users

### Before Using This Server

1. **Use SNC authentication**
   - Avoids storing passwords in configuration files
   - See [MIGRATION.md](MIGRATION.md) for migration instructions

2. **Assess your network security**
   - Certificate validation may be disabled - MITM attacks are possible
   - Only use on trusted networks or localhost

3. **Review log output**
   - Session IDs may be logged; ensure logs are not accessible to unauthorized users

4. **Use in development only**
   - Disabled certificate validation makes this unsuitable for production without modification

### Security Improvement Priority

| Fix | Effort | Impact |
|-----|--------|--------|
| Enable cert validation in Java truststore | Low | Prevents MITM |
| Remove session ID logging or change to debug level | Low | Reduces exposure |
| Add session timeout to JcoSessionManager | Medium | Better session hygiene |
| Implement stricter SQL validation | Medium | Defense in depth |

---

## Conclusion

This MCP server is **usable for development purposes** on trusted networks, but has security weaknesses that users should understand:

1. **Network security is weak** - disabled certificate validation is the primary concern
2. **Use SNC authentication** - avoids credential storage issues

For controlled development environments with SNC authentication, the risk is acceptable. For production or sensitive environments, address the certificate validation issue first.
