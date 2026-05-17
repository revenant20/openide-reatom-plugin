#!/usr/bin/env bash
# Запуск песочницы IDE с плагином reatom-ide-plugin.
#
# Песочницу поднимает сам этот плагин (его runIde). В сборку песочницы
# через localPlugin подключён MCP Steroid (см. build.gradle.kts) — IDE
# управляема AI-агентом. По умолчанию открывает reatom-playground —
# там видно Code Lens и gutter-иконки фичи 9.
#
#   ./scripts/start-sandbox.sh [путь-к-проекту]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGIN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$PLUGIN_DIR/../.." && pwd)"
IDE_PROJECTS_DIR="$(cd "$REPO_DIR/.." && pwd)"

# Проект, который откроется в песочнице.
OPEN_PROJECT="${1:-$IDE_PROJECTS_DIR/reatom-playground}"
if [[ ! -d "$OPEN_PROJECT" ]]; then
    echo "ОШИБКА: каталог проекта не найден: $OPEN_PROJECT"
    exit 1
fi
OPEN_PROJECT="$(cd "$OPEN_PROJECT" && pwd)"

PID_FILE="$PLUGIN_DIR/build/.sandbox-ide.pid"
LOG_FILE="$PLUGIN_DIR/build/sandbox-gradle.log"

# --- уже запущена? ---
if [[ -f "$PID_FILE" ]]; then
    OLD_PID="$(cat "$PID_FILE")"
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Песочница уже запущена (PID $OLD_PID). Лог: $LOG_FILE"
        exit 0
    fi
    rm -f "$PID_FILE"
fi

# --- JAVA_HOME (нужен JDK 21) ---
if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null \
        || echo "$HOME/Library/Java/JavaVirtualMachines/liberica-21.0.10")"
fi
export JAVA_HOME
if [[ ! -d "$JAVA_HOME" ]]; then
    echo "ОШИБКА: JAVA_HOME не существует: $JAVA_HOME"
    exit 1
fi

mkdir -p "$PLUGIN_DIR/build"
echo "JAVA_HOME:           $JAVA_HOME"
echo "Проект в песочнице:  $OPEN_PROJECT"
echo "Запуск runIde в фоне (в песочнице — reatom-ide-plugin + MCP Steroid)..."

nohup "$REPO_DIR/gradlew" -p "$REPO_DIR" :ide-plugin:runIde \
    --args="$OPEN_PROJECT" --console=plain > "$LOG_FILE" 2>&1 &
GRADLE_PID=$!
echo "$GRADLE_PID" > "$PID_FILE"
echo "Gradle PID: $GRADLE_PID (сохранён в $PID_FILE)"
echo "Лог: $LOG_FILE"

# --- ранний контроль: не упал ли процесс сразу ---
sleep 8
if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    echo "ОШИБКА: процесс Gradle завершился. Хвост лога:"
    tail -25 "$LOG_FILE" 2>/dev/null || true
    rm -f "$PID_FILE"
    exit 1
fi

echo "Песочница стартует — окно IDE появится через ~30-60с."
echo "Остановить: ./scripts/stop-sandbox.sh"
