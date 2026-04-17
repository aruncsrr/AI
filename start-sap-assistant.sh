#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# start-sap-assistant.sh  —  Launch the SAP AI Assistant web UI
#
# Usage:
#   ./start-sap-assistant.sh            → starts web server on http://localhost:8080
#   ./start-sap-assistant.sh --mcp      → starts MCP STDIO mode (for Claude Code)
#   ./start-sap-assistant.sh --port 9090 → override port
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/jco-service/target/jco-service-1.0.0.jar"

# ── CDD/NCDD offline cache ────────────────────────────────────────────────────
export CDD_CACHE_DIR="${CDD_CACHE_DIR:-$HOME/sap-ai-assistant/cdd-cache}"

# ── Jira ──────────────────────────────────────────────────────────────────────
export JIRA_URL="${JIRA_URL:-https://jira.tools.sap/}"
export JIRA_TOKEN="${JIRA_TOKEN:-}"

# ── Wiki ──────────────────────────────────────────────────────────────────────
export SAP_WIKI_TOKEN="${SAP_WIKI_TOKEN:-}"

# ── JCo native library (macOS ARM64 default) ──────────────────────────────────
if [[ -z "${DYLD_LIBRARY_PATH:-}" ]]; then
    export DYLD_LIBRARY_PATH="$SCRIPT_DIR/jco-service/src/main/resources/native/macos-arm64"
fi

# ── Verify JAR exists ─────────────────────────────────────────────────────────
if [[ ! -f "$JAR" ]]; then
    echo ""
    echo "  ERROR: JAR not found at:"
    echo "    $JAR"
    echo ""
    echo "  Build it first:"
    echo "    cd jco-service && mvn clean package"
    echo ""
    exit 1
fi

# ── Parse args ────────────────────────────────────────────────────────────────
MCP_MODE=false
PORT=8080
EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --mcp)   MCP_MODE=true; shift ;;
        --port)  PORT="$2"; shift 2 ;;
        *)       EXTRA_ARGS+=("$1"); shift ;;
    esac
done

# ── Launch ────────────────────────────────────────────────────────────────────
if $MCP_MODE; then
    # MCP STDIO mode — for Claude Code / AI assistant integration
    exec java -jar "$JAR" --mcp "${EXTRA_ARGS[@]:-}"
else
    # Web server mode — opens browser-accessible UI on localhost
    echo ""
    echo "  ╔══════════════════════════════════════════════════════╗"
    echo "  ║         SAP AI Assistant — Starting                  ║"
    echo "  ╚══════════════════════════════════════════════════════╝"
    echo ""
    echo "  Web UI  →  http://localhost:$PORT"
    echo "  CDD cache: $CDD_CACHE_DIR"
    echo ""
    echo "  Tip: Bookmark http://localhost:$PORT in your browser."
    echo "  Press Ctrl+C to stop."
    echo ""

    # Open browser after a short delay (background)
    if command -v open &>/dev/null; then
        (sleep 3 && open "http://localhost:$PORT") &
    elif command -v xdg-open &>/dev/null; then
        (sleep 3 && xdg-open "http://localhost:$PORT") &
    fi

    exec java \
        -DSERVER_PORT="$PORT" \
        -jar "$JAR" \
        --server \
        "${EXTRA_ARGS[@]:-}"
fi
