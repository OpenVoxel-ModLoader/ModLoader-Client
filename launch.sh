#!/usr/bin/env bash
# Starts the official OpenVoxel launcher (Linux build) with the ModLoader agent injected.
# Game location: OPENVOXEL_HOME, or the folder that contains this repo (../.. or ..).
set -euo pipefail
cd "$(dirname "$0")"
JAR="$PWD/dist/ModLoader-v1.0.0-vgame-0.8.0-client.jar"
[ -f "$JAR" ] || ./build.sh
HOME_DIR="${OPENVOXEL_HOME:-}"
for c in ../.. .. .; do
  [ -z "$HOME_DIR" ] && { [ -f "$c/OpenVoxelLauncher-linux64" ] || [ -f "$c/OpenVoxelLauncher" ]; } && HOME_DIR="$(cd "$c" && pwd)"
done
if [ -z "$HOME_DIR" ]; then
  echo "EN: OpenVoxel launcher not found. Set OPENVOXEL_HOME to the launcher folder." >&2
  echo "ES: No se encontró el launcher de OpenVoxel. Define OPENVOXEL_HOME con la carpeta del launcher." >&2
  exit 1
fi
LAUNCHER="$HOME_DIR/OpenVoxelLauncher-linux64"; [ -f "$LAUNCHER" ] || LAUNCHER="$HOME_DIR/OpenVoxelLauncher"
chmod +x "$LAUNCHER" 2>/dev/null || true
mkdir -p instance/{mods,config,logs,lang,resourcepacks,sounds,music}
export OPENVOXEL_MODS_DIR="$PWD/instance"
export JAVA_TOOL_OPTIONS="-javaagent:$JAR"
cat <<EOF
============================================================
 OpenVoxel ModLoader 1.0.0 (game 0.8.0)
 EN: In the launcher select version "0.0.8-Debug" and press PLAY.
 ES: En el launcher selecciona la versión "0.0.8-Debug" y pulsa PLAY.
 Mods: instance/mods   Settings: instance/config
============================================================
EOF
cd "$HOME_DIR" && exec "$LAUNCHER" "$@"
