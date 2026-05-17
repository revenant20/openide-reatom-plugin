#!/usr/bin/env bash
# Starts the IDE sandbox with the reatom-ide-plugin plugin.
#
# The sandbox is brought up by this plugin itself (its runIde). MCP Steroid is
# attached to the sandbox build via localPlugin (see build.gradle.kts) — the
# IDE is controllable by an AI agent. By default it opens reatom-playground —
# where the Code Lens and gutter icons of feature 9 are visible.
#
#   ./scripts/start-sandbox.sh [project-path]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGIN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$PLUGIN_DIR/../.." && pwd)"
IDE_PROJECTS_DIR="$(cd "$REPO_DIR/.." && pwd)"

# The project that will be opened in the sandbox.
OPEN_PROJECT="${1:-$IDE_PROJECTS_DIR/reatom-playground}"
if [[ ! -d "$OPEN_PROJECT" ]]; then
    echo "ERROR: project directory not found: $OPEN_PROJECT"
    exit 1
fi
OPEN_PROJECT="$(cd "$OPEN_PROJECT" && pwd)"

PID_FILE="$PLUGIN_DIR/build/.sandbox-ide.pid"
LOG_FILE="$PLUGIN_DIR/build/sandbox-gradle.log"

# --- already running? ---
if [[ -f "$PID_FILE" ]]; then
    OLD_PID="$(cat "$PID_FILE")"
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Sandbox already running (PID $OLD_PID). Log: $LOG_FILE"
        exit 0
    fi
    rm -f "$PID_FILE"
fi

# --- JAVA_HOME (JDK 21 required) ---
if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null \
        || echo "$HOME/Library/Java/JavaVirtualMachines/liberica-21.0.10")"
fi
export JAVA_HOME
if [[ ! -d "$JAVA_HOME" ]]; then
    echo "ERROR: JAVA_HOME does not exist: $JAVA_HOME"
    exit 1
fi

mkdir -p "$PLUGIN_DIR/build"
echo "JAVA_HOME:           $JAVA_HOME"
echo "Sandbox project:     $OPEN_PROJECT"
echo "Starting runIde in the background (sandbox — reatom-ide-plugin + MCP Steroid)..."

nohup "$REPO_DIR/gradlew" -p "$REPO_DIR" :ide-plugin:runIde \
    --args="$OPEN_PROJECT" --console=plain > "$LOG_FILE" 2>&1 &
GRADLE_PID=$!
echo "$GRADLE_PID" > "$PID_FILE"
echo "Gradle PID: $GRADLE_PID (saved to $PID_FILE)"
echo "Log: $LOG_FILE"

# --- early check: did the process crash right away ---
sleep 8
if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    echo "ERROR: the Gradle process exited. Log tail:"
    tail -25 "$LOG_FILE" 2>/dev/null || true
    rm -f "$PID_FILE"
    exit 1
fi

echo "Sandbox is starting — the IDE window will appear in ~30-60s."
echo "To stop: ./scripts/stop-sandbox.sh"
