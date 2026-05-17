#!/usr/bin/env bash
# Stops the IDE sandbox started by start-sandbox.sh.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGIN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$PLUGIN_DIR/build/.sandbox-ide.pid"
GRACEFUL_TIMEOUT=15

if [[ ! -f "$PID_FILE" ]]; then
    echo "Sandbox is not running (no $PID_FILE)."
    exit 0
fi

PID="$(cat "$PID_FILE")"
if ! kill -0 "$PID" 2>/dev/null; then
    echo "Process $PID not found — removing the stale pidfile."
    rm -f "$PID_FILE"
    exit 0
fi

echo "SIGTERM → PID $PID..."
kill "$PID" 2>/dev/null || true
waited=0
while (( waited < GRACEFUL_TIMEOUT )); do
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "Stopped gracefully in ${waited}s."
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 1
    waited=$((waited + 1))
done

echo "Did not stop within ${GRACEFUL_TIMEOUT}s — SIGKILL."
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "Stopped."
