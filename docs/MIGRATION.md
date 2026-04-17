# Migration Guide: Basic Auth to SNC Authentication

## Overview

**Basic authentication (username/password) is deprecated and will be removed in v2.0.**

SNC (Secure Network Communications) authentication provides:
- Single Sign-On (SSO) using your OS credentials
- No passwords stored in configuration files
- Automatic Kerberos/certificate-based authentication

## Current Basic Auth Configuration (Deprecated)

If you are currently using basic authentication, your configuration looks like one of these:

### Multi-System Configuration

```json
{
  "systems": {
    "dev": {
      "url": "https://dev-sap.example.com:443",
      "client": "100",
      "username": "DEV_USER",
      "password": "secret",
      "description": "Development system"
    },
    "prod": {
      "url": "https://prod-sap.example.com:443",
      "client": "200",
      "username": "PROD_USER",
      "password": "secret",
      "description": "Production system"
    }
  },
  "default": "dev"
}
```

### Single-System Configuration (.env)

For simple setups using environment variables:

```
SAP_URL=https://dev-sap.example.com:443
SAP_CLIENT=100
SAP_USERNAME=DEV_USER
SAP_PASSWORD=secret
```

Both of these methods are deprecated and will be removed in v2.0.

## Migration Steps

### Step 1: Verify SNC Prerequisites

SNC authentication requires:
1. SAP system configured for SNC connections
2. Kerberos/Active Directory environment (for SSO)
3. SNC library installed (typically SAP Cryptographic Library)

Contact your SAP Basis team to confirm SNC is available for your system.

### Step 2: Update Configuration

**Before (Basic Auth):**
```json
{
  "systems": {
    "dev": {
      "url": "https://dev-sap.example.com:44300",
      "client": "100",
      "username": "MYUSER",
      "password": "secret123"
    }
  },
  "default": "dev"
}
```

**After (SNC Auth):**
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
      }
    }
  },
  "default": "dev"
}
```

### Step 3: Get SNC Partner Name

The SNC partner name identifies the SAP system. To find it:

1. **From SAP GUI**: Transaction `RZ10` -> Instance Profile -> `snc/identity/as`
2. **From Basis Team**: Request the SNC partner name for your system
3. **Common Format**: `p/secude:CN=<SYSTEM>, O=<ORG>, C=<COUNTRY>`

### Step 4: Verify Configuration

Test the connection:
```
ListSystems  # Should show your system with authType: snc
GetClass ZCL_MY_CLASS  # Should retrieve without password prompt
```

## SNC Quality of Protection (QOP)

The `qop` setting controls the security level:

| Value | Level | Description |
|-------|-------|-------------|
| 1 | Authentication only | Verifies identity |
| 2 | Integrity protection | Detects tampering |
| 3 | Privacy protection | Encrypts data |
| 8 | Default | Server determines level |
| 9 | Maximum | Highest available protection |

**Recommended**: Use `9` (maximum) for production environments.

## Platform-Specific Notes

### macOS

- SSO uses macOS Keychain for certificate storage
- First connection may prompt for keychain access
- Subsequent connections use cached credentials

### Windows

- SSO uses Windows Certificate Store
- Kerberos tickets from Active Directory login are used
- No additional configuration typically needed

### Linux

- Requires explicit certificate paths (`authType: 'x509'`)
- Contact your administrator for certificate setup

## Troubleshooting

### "SNC not configured on server"

The SAP system doesn't have SNC enabled. Contact your Basis team.

### "SNC partner name not found"

The `partnername` in your config doesn't match the server. Verify with:
```
Transaction RZ10 → snc/identity/as
```

### "Authentication failed"

1. Verify you're logged into your domain (Windows) or have valid Kerberos ticket
2. Check keychain access (macOS)
3. Try `kinit` to refresh Kerberos credentials (Linux/macOS)

### Still having issues?

1. Check `JCO_SERVICE_URL` points to running JCo service
2. Verify JCo native libraries are properly installed
3. Review JCo service logs for detailed error messages

## Fallback: Keep Basic Auth (Temporary)

If you cannot migrate immediately, basic auth continues to work until v2.0. You'll see deprecation warnings in the logs:

```
[WARN] [DEPRECATION] Basic auth (username/password) is deprecated...
```

Plan your migration before v2.0 release.

## Need Help?

- Check [ARCHITECTURE.md](ARCHITECTURE.md) for system design details
- Review [DEVELOPMENT.md](DEVELOPMENT.md) for debugging tips
- Open an issue for migration assistance
