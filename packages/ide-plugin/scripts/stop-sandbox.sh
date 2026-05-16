#!/usr/bin/env bash
# Останавливает песочницу IDE, запущенную start-sandbox.sh.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGIN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$PLUGIN_DIR/build/.sandbox-ide.pid"
GRACEFUL_TIMEOUT=15

if [[ ! -f "$PID_FILE" ]]; then
    echo "Песочница не запущена (нет $PID_FILE)."
    exit 0
fi

PID="$(cat "$PID_FILE")"
if ! kill -0 "$PID" 2>/dev/null; then
    echo "Процесс $PID не найден — удаляю устаревший pidfile."
    rm -f "$PID_FILE"
    exit 0
fi

echo "SIGTERM → PID $PID..."
kill "$PID" 2>/dev/null || true
waited=0
while (( waited < GRACEFUL_TIMEOUT )); do
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "Остановлено штатно за ${waited}с."
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 1
    waited=$((waited + 1))
done

echo "Не остановилось за ${GRACEFUL_TIMEOUT}с — SIGKILL."
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "Остановлено."
