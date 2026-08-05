#!/usr/bin/env bash
#
# Build Massiveo's Freaky Addons and deploy it to LabyMod.
#
# It used to drop a copy on the Desktop as well, which just piled up a jar per
# version. Releases are on GitHub and the live jar is in LabyMod, so a build
# artefact sitting on the Desktop was only clutter.
#
# The version is read from the jar that gets built, so this never needs editing
# when the version changes.
#
# Usage:
#   ./deploy.sh            build, then deploy
#   ./deploy.sh --no-build  deploy whatever is already in build/libs

set -euo pipefail

PROJECT="/Users/otto/Developer/CelleScanner"
JAVA8="/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
LABY_MODS="/Users/otto/Library/Application Support/LabyMod/instances/f9b097c3-757b-4829-a2a9-ccaf85449987/loader/forge/1.8.9/mods"

cd "$PROJECT"

if [ "${1:-}" != "--no-build" ]; then
    echo "==> Building with Java 8"
    JAVA_HOME="$JAVA8" ./gradlew clean build
fi

JAR="$(ls -t build/libs/cellescanner-*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)"
if [ -z "$JAR" ]; then
    echo "!! No jar in build/libs. Build failed or produced nothing." >&2
    exit 1
fi

VERSION="$(basename "$JAR" .jar)"
VERSION="${VERSION#cellescanner-}"
echo "==> Built cellescanner-${VERSION}.jar ($(du -h "$JAR" | cut -f1))"

# A stale conflict copy would be packaged and can crash the mod, so refuse to ship one.
if unzip -l "$JAR" | grep -qE ' [0-9]\.(class|png|json|ogg)$'; then
    echo "!! Jar contains sync conflict copies (\" 2.class\" and friends). Not deploying." >&2
    echo "   Clean them and rebuild:  find src build -name '* [0-9].*' -delete" >&2
    exit 1
fi

if [ ! -d "$LABY_MODS" ]; then
    echo "!! LabyMod mods folder not found: $LABY_MODS" >&2
    exit 1
fi

echo "==> Clearing old builds from LabyMod"
rm -rf "$LABY_MODS/remapped"
# Scoped to our own jar name so other mods in the folder are left alone.
find "$LABY_MODS" -maxdepth 1 -name 'cellescanner-*.jar' -delete

echo "==> Copying to LabyMod"
cp "$JAR" "$LABY_MODS/cellescanner-${VERSION}.jar"

echo
echo "Deployed ${VERSION}:"
ls -la "$LABY_MODS/cellescanner-${VERSION}.jar" | awk '{print "  LabyMod  " $5 " bytes"}'
echo "  Restart Minecraft to load it."
