#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
#  SAP AI Assistant — Background Auto-Start
#  Called by LaunchAgent (~/Library/LaunchAgents/com.sap.assistant.autostart.plist)
#  Starts the server silently on login and opens the browser when ready.
# ─────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="/Users/I766689/Desktop"
JAR="$SCRIPT_DIR/sap-ai-assistant/jco-service/target/jco-service-1.0.0.jar"
JCO_LIB="$SCRIPT_DIR/Visual/sapjco3-darwinarm64-3.1.13"
LOG="/tmp/sap-assistant-auto.log"

# ── Environment (mirrors start-sap-assistant.sh) ─────────────────────────────
export ANTHROPIC_AUTH_TOKEN="${ANTHROPIC_AUTH_TOKEN:-}"
export ANTHROPIC_BASE_URL="${ANTHROPIC_BASE_URL:-http://localhost:6655/anthropic/}"
export ANTHROPIC_MODEL="${ANTHROPIC_MODEL:-claude-sonnet-latest}"
export SAP_WIKI_URL="${SAP_WIKI_URL:-https://wiki.one.int.sap/wiki}"
export SAP_WIKI_TOKEN="${SAP_WIKI_TOKEN:-}"
export SAP_SYSTEMS_CONFIG="$SCRIPT_DIR/sap-ai-assistant/.sap-systems.json"
export JIRA_URL="${JIRA_URL:-https://jira.tools.sap}"
export JIRA_TOKEN="${JIRA_TOKEN:-}"
export DYLD_LIBRARY_PATH="$JCO_LIB"

echo "$(date): Auto-start triggered" >> "$LOG"

# ── If already running, just open the browser ────────────────────────────────
if pgrep -f "jco-service-1.0.0.jar" > /dev/null 2>&1; then
    echo "$(date): Server already running — opening browser" >> "$LOG"
    sleep 3
    open "http://localhost:8080"
    exit 0
fi

# ── Pre-flight checks ────────────────────────────────────────────────────────
if [ ! -f "$JAR" ]; then
    echo "$(date): ERROR — JAR not found at $JAR" >> "$LOG"
    exit 1
fi
if [ ! -d "$JCO_LIB" ]; then
    echo "$(date): ERROR — JCo lib not found at $JCO_LIB" >> "$LOG"
    exit 1
fi

# ── Start server silently in background ──────────────────────────────────────
echo "$(date): Starting SAP AI Assistant server..." >> "$LOG"
nohup java \
    -Djava.library.path="$JCO_LIB" \
    -Dloader.path="$JCO_LIB" \
    -Dspring.profiles.active=web \
    -jar "$JAR" \
    >> "$LOG" 2>&1 &

SERVER_PID=$!
echo "$(date): Server started with PID=$SERVER_PID" >> "$LOG"

# ── Wait for server ready (max 120 seconds), then open browser ───────────────
for i in $(seq 1 120); do
    sleep 1
    if curl -sf --max-time 1 http://localhost:8080 >/dev/null 2>&1; then
        echo "$(date): Server ready after ${i}s — opening browser" >> "$LOG"
        open "http://localhost:8080"
        exit 0
    fi
done

echo "$(date): ERROR — Server did not respond within 120 seconds" >> "$LOG"
exit 1
