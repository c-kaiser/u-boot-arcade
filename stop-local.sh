#!/usr/bin/env bash
# Beendet die von ./run-local.sh gestarteten Services anhand der PID-Dateien unter .run/.

set -euo pipefail
cd "$(dirname "$0")"

STOP_TIMEOUT="${STOP_TIMEOUT:-20}"

running=""
for pid_file in .run/*.pid; do
  [ -f "$pid_file" ] || continue
  name=$(basename "$pid_file" .pid)
  pid=$(cat "$pid_file")
  # Nur echte Java-Prozesse beenden – die PID könnte inzwischen neu vergeben sein
  if kill -0 "$pid" 2>/dev/null && ps -p "$pid" -o comm= | grep -q java; then
    echo "Beende $name (PID $pid)"
    kill "$pid"
    running="$running $pid"
  fi
  rm -f "$pid_file"
done

if [ -z "$running" ]; then
  echo "Keine laufenden Services gefunden."
  exit 0
fi

waited=0
for pid in $running; do
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$waited" -ge "$STOP_TIMEOUT" ]; then
      echo "PID $pid reagiert nicht, erzwinge Ende"
      kill -9 "$pid" 2>/dev/null || true
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
done
echo "Alle Services beendet."
