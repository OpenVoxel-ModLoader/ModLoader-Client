#!/usr/bin/env bash
# Plain-javac build (no Gradle needed). Equivalent to: ./gradlew jar
set -euo pipefail
cd "$(dirname "$0")"
OUT="dist/ModLoader-v1.1.0-pre2-vgame-0.0.8-client.jar"
GAME="${OPENVOXEL_GAME_JAR:-}"
for c in ../../../Game/versions/0.0.8-Debug/openvoxel-0.0.8-Debug.jar ../../Game/versions/0.0.8-Debug/openvoxel-0.0.8-Debug.jar game/openvoxel.jar; do
  [ -z "$GAME" ] && [ -f "$c" ] && GAME="$c"
done
if [ -z "$GAME" ]; then
  echo "Game JAR not found. Set OPENVOXEL_GAME_JAR to openvoxel-0.0.8-Debug.jar (download it once with the official launcher)." >&2
  exit 1
fi
rm -rf build/classes && mkdir -p build/classes dist
find src/main/java -name '*.java' > build/sources.txt
javac --release 11 -encoding UTF-8 -Xlint:-options -classpath "$GAME:lib/asm-9.7.1.jar:lib/asm-tree-9.7.1.jar" -d build/classes @build/sources.txt
cp -r src/main/resources/. build/classes/
(cd build/classes && jar xf ../../lib/asm-9.7.1.jar org && jar xf ../../lib/asm-tree-9.7.1.jar org)
jar --create --file "$OUT" --manifest manifest.mf -C build/classes .
echo "Built $OUT"
