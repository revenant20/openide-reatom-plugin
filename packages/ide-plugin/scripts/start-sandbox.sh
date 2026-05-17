#!/usr/bin/env bash
# Starts the IDE sandbox with the reatom-ide-plugin plugin.
#
# The sandbox is brought up by this plugin itself (its runIde). By default it
# opens the in-repo demo (examples/reatom-demo) — where the Code Lens and
# gutter icons are visible. MCP Steroid is attached only when opted in (see the
# mcpSteroidDir property in build.gradle.kts).
#
#   ./scripts/start-sandbox.sh [project-path]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGIN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$PLUGIN_DIR/../.." && pwd)"

# The project that will be opened in the sandbox.
OPEN_PROJECT="${1:-$REPO_DIR/examples/reatom-demo}"
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

# The build pins JDK 21 itself via a Gradle toolchain (see build.gradle.kts);
# the Gradle launcher still needs some JDK to start. If JAVA_HOME is unset,
# discover one (macOS) — otherwise Gradle falls back to a JDK on PATH.
if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    DISCOVERED_JDK="$(/usr/libexec/java_home 2>/dev/null || true)"
    [[ -n "$DISCOVERED_JDK" ]] && export JAVA_HOME="$DISCOVERED_JDK"
fi

mkdir -p "$PLUGIN_DIR/build"
echo "Sandbox project:     $OPEN_PROJECT"
echo "Starting runIde in the background (sandbox — reatom-ide-plugin)..."

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
