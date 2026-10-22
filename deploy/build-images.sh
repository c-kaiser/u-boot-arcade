#!/usr/bin/env bash
# Baut alle Images und pusht sie in eine Registry, damit der Cluster sie ziehen kann.
#   deploy/build-images.sh ghcr.io/c-kaiser 1.0.0          bauen + pushen
#   PUSH=false deploy/build-images.sh local 1.0.0       nur bauen (z. B. für kind load docker-image)
set -euo pipefail
REGISTRY="${1:?Registry angeben, z. B. ghcr.io/c-kaiser}"
TAG="${2:-1.0.0}"
PUSH="${PUSH:-true}"
PLATFORM="${PLATFORM:-linux/amd64}"
cd "$(dirname "$0")/.."

# Gradle-Modul -> Image-Name (muss zu deploy/helm/.../templates passen)
declare -A IMAGES=(
  [game-server]=game-server
  [physics-service]=physics
  [leaderboard]=leaderboard
  [enemy-ai]=enemy-ai
  [player-swarm]=player-swarm
)

for module in "${!IMAGES[@]}"; do
  image="${REGISTRY}/u-boot-arcade-${IMAGES[$module]}:${TAG}"
  echo "==> $image"
  docker build --platform "$PLATFORM" --build-arg SERVICE="$module" -t "$image" .
  if [[ "$PUSH" == "true" ]]; then docker push "$image"; fi
done

echo
echo "Fertig. Helm-Install z. B.:"
echo "  helm upgrade --install u-boot deploy/helm/u-boot-arcade -n u-boot --create-namespace \\"
echo "    --set image.registry=${REGISTRY} --set image.tag=${TAG}"
