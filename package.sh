#!/usr/bin/env bash
# Builds the ready-to-use client bundle: dist/ModLoader-Client-v1.0.0-vgame-0.0.8.zip
set -euo pipefail
cd "$(dirname "$0")"
JAR="dist/ModLoader-v1.0.0-vgame-0.0.8-client.jar"
[ -f "$JAR" ] || ./build.sh
STAGE="$(mktemp -d)/ModLoader-Client"
mkdir -p "$STAGE/dist" "$STAGE"/instance/{mods,config,lang,resourcepacks,sounds,music,logs}
cp "$JAR" "$STAGE/dist/"
cp launch.bat launch.ps1 launch.sh README.md LICENSE "$STAGE/"
cp instance/README.md "$STAGE/instance/"; cp instance/sounds/README.md "$STAGE/instance/sounds/"; cp instance/music/README.md "$STAGE/instance/music/"
chmod +x "$STAGE/launch.sh"
ZIP="$PWD/dist/ModLoader-Client-v1.0.0-vgame-0.0.8.zip"
rm -f "$ZIP"
(cd "$STAGE/.." && zip -qr "$ZIP" ModLoader-Client)
rm -rf "$(dirname "$STAGE")"
echo "Built $ZIP"
