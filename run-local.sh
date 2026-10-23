#!/usr/bin/env bash
# U-Boot Arcade ohne Docker: baut die Services und startet sie als JVM-Prozesse im Hintergrund.
#   ./run-local.sh           Spiel mit physics, leaderboard, enemy-ai (Bots und Gegner regelbasiert per .env)
#   ./run-local.sh --swarm   zusätzlich Schwarm-Spieler (WebSocket-Last)
#
# Konfiguration aus .env (Vorlage: .env.example, andere Datei per ENV_FILE=...), Logs unter logs/,
# PID-Dateien unter .run/. Beenden mit ./stop-local.sh.
# Wohin die Telemetrie geht, steht nur in .env – das Skript kennt keine Telemetrie-Adressen.

set -euo pipefail
cd "$(dirname "$0")"

# Muss zu opentelemetry-api in build.gradle.kts und zum Dockerfile passen – nur gemeinsam ändern
OTEL_AGENT_VERSION=2.32.0
# Absolute Pfade, damit stop-local.sh die Prozesse auch ohne PID-Datei an der Kommandozeile erkennt
OTEL_AGENT="$PWD/.otel/opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar"

with_swarm=false
for arg in "$@"; do
  case "$arg" in
    --swarm) with_swarm=true ;;
    -h|--help) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unbekannte Option: $arg (erlaubt: --swarm)" >&2; exit 2 ;;
  esac
done

# ---------- Konfiguration ----------
ENV_FILE="${ENV_FILE:-.env}"
case "$ENV_FILE" in /*) ;; *) ENV_FILE="./$ENV_FILE" ;; esac
if [ ! -f "$ENV_FILE" ]; then
  echo "$ENV_FILE fehlt. Vorlage kopieren und anpassen:  cp .env.example .env" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

GAME_PORT="${GAME_PORT:-8088}"
PHYSICS_PORT="${PHYSICS_PORT:-8081}"
LEADERBOARD_PORT="${LEADERBOARD_PORT:-8082}"
ENEMY_AI_PORT="${ENEMY_AI_PORT:-8083}"
JAVA_OPTS="${JAVA_OPTS:--Xmx256m}"
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-120}"
export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-service.namespace=u-boot-arcade,deployment.environment.name=${DEPLOY_ENV:-local}}"

# ---------- Läuft schon etwas? ----------
mkdir -p logs .run .otel
for pid_file in .run/*.pid; do
  [ -f "$pid_file" ] || continue
  if kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "Es laufen noch Services ($(basename "$pid_file" .pid) u. a.). Erst ./stop-local.sh ausführen." >&2
    exit 1
  fi
  rm -f "$pid_file"
done
# Verwaiste Services (PID-Datei fehlt) halten Ports und die H2-Datei des Leaderboards gesperrt
orphans=$(pgrep -f -- "-jar $PWD/" || true)
if [ -n "$orphans" ]; then
  echo "Es laufen noch Services aus diesem Verzeichnis ohne PID-Datei (PID $(echo $orphans)). Erst ./stop-local.sh ausführen." >&2
  exit 1
fi
for port in "$PHYSICS_PORT" "$LEADERBOARD_PORT" "$ENEMY_AI_PORT" "$GAME_PORT"; do
  if (exec 3<>"/dev/tcp/localhost/$port") 2>/dev/null; then
    echo "Port $port ist schon belegt – läuft noch eine ältere Instanz? Prozess finden z. B. mit: lsof -i :$port" >&2
    exit 1
  fi
done

# ---------- OTel-Java-Agent (einmalig) ----------
if [ ! -f "$OTEL_AGENT" ]; then
  echo "Lade OTel-Java-Agent ${OTEL_AGENT_VERSION} ..."
  curl -fsSL -o "$OTEL_AGENT.tmp" \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"
  mv "$OTEL_AGENT.tmp" "$OTEL_AGENT"
fi

# ---------- Bauen ----------
echo "Baue die Services (./gradlew bootJar) ..."
./gradlew --quiet bootJar

boot_jar() {
  local module=$1 jar
  for jar in "$module"/build/libs/"$module"-*.jar; do
    case "$jar" in *-plain.jar) ;; *) echo "$PWD/$jar"; return ;; esac
  done
  echo "Kein bootJar für $module gefunden" >&2
  exit 1
}

# start <service-name> <modul> [VAR=wert ...]
start() {
  local name=$1 module=$2 jar
  shift 2
  jar=$(boot_jar "$module")
  # shellcheck disable=SC2086 # JAVA_OPTS darf mehrere Optionen enthalten
  env OTEL_SERVICE_NAME="$name" "$@" \
    nohup java $JAVA_OPTS -javaagent:"$OTEL_AGENT" -jar "$jar" >"logs/$name.log" 2>&1 &
  echo $! >".run/$name.pid"
  echo "  $name gestartet (PID $!, Log logs/$name.log)"
}

fail() {
  local name=$1
  echo "$name ist nicht hochgekommen. Letzte Log-Zeilen:" >&2
  tail -n 20 "logs/$name.log" >&2 || true
  echo "Bereits gestartete Services beenden mit ./stop-local.sh" >&2
  exit 1
}

# wait_healthy <service-name> <port>
wait_healthy() {
  local name=$1 port=$2 waited=0
  until curl -fs --noproxy '*' -o /dev/null "http://localhost:$port/actuator/health"; do
    kill -0 "$(cat ".run/$name.pid")" 2>/dev/null || fail "$name"
    [ "$waited" -lt "$STARTUP_TIMEOUT" ] || fail "$name"
    sleep 1
    waited=$((waited + 1))
  done
  echo "  $name bereit auf Port $port"
}

# ---------- Starten ----------
echo "Starte Services ..."
start physics physics-service PORT="$PHYSICS_PORT"
start leaderboard leaderboard PORT="$LEADERBOARD_PORT"
start enemy-ai enemy-ai PORT="$ENEMY_AI_PORT"
wait_healthy physics "$PHYSICS_PORT"
wait_healthy leaderboard "$LEADERBOARD_PORT"
wait_healthy enemy-ai "$ENEMY_AI_PORT"

start game-server game-server PORT="$GAME_PORT" \
  PHYSICS_URL="http://localhost:$PHYSICS_PORT" \
  LEADERBOARD_URL="http://localhost:$LEADERBOARD_PORT" \
  ENEMY_AI_URL="http://localhost:$ENEMY_AI_PORT"
wait_healthy game-server "$GAME_PORT"

if [ "$with_swarm" = true ]; then
  start player-swarm player-swarm GAME_WS_URL="ws://localhost:$GAME_PORT/ws"
fi

echo
echo "Spiel: http://localhost:$GAME_PORT   Admin: http://localhost:$GAME_PORT/admin.html"
echo "Beenden mit ./stop-local.sh"
