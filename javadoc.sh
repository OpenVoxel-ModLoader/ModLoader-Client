#!/usr/bin/env bash
# Generates the public API Javadoc into dist/javadoc (needs the game jar: OPENVOXEL_GAME_JAR or the launcher install).
set -euo pipefail
cd "$(dirname "$0")"
GAME="${OPENVOXEL_GAME_JAR:-}"
for c in ../../../Game/versions/0.0.8-Debug/openvoxel-0.0.8-Debug.jar game/openvoxel.jar; do [ -z "$GAME" ] && [ -f "$c" ] && GAME="$c"; done
[ -n "$GAME" ] || { echo "Game JAR not found; set OPENVOXEL_GAME_JAR." >&2; exit 1; }
P=src/main/java/com/happyuky7/openvoxelmoding/loader
rm -rf dist/javadoc
javadoc --release 11 -quiet -Xdoclint:none -encoding UTF-8 -charset UTF-8 -windowtitle "OpenVoxel ModLoader API" \
  -doctitle "OpenVoxel ModLoader client API" -sourcepath src/main/java -classpath "$GAME:lib/asm-9.7.1.jar:lib/asm-tree-9.7.1.jar" \
  -link https://docs.oracle.com/en/java/javase/11/docs/api/ -d dist/javadoc \
  $P/ClientMod.java $P/ModContext.java $P/ModConfig.java $P/NativeBlocks.java $P/Lang.java $P/BlockRegistry.java $P/package-info.java
echo "Javadoc in dist/javadoc"
