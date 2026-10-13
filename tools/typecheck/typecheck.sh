#!/bin/sh
# Type-checks GlassCast's Kotlin against the real libraries (staged from the
# Mac's Gradle cache), with the Compose compiler plugin.
# Usage: sh tools/typecheck/typecheck.sh [project dir]   (default: this project)
# Needs the library jars in $TC_JARS (default /home/claude/tc): cp/ holds the
# app's classpath (classes.jar out of each .aar, plus android.jar), extra/ the
# Cast set, and kc/ the embeddable Kotlin 2.0.21 compiler with the Compose
# plugin. See tools/typecheck/README.md for how they're staged.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
P=${1:-$(cd "$HERE/../.." && pwd)}
TC=${TC_JARS:-/home/claude/tc}
if [ ! -d "$TC/cp" ] || [ ! -d "$TC/kc" ]; then
  echo "typecheck: FAILED — no jars in $TC (see tools/typecheck/README.md)"
  exit 2
fi
WORK=$(mktemp -d /tmp/tc.XXXXXX)
GEN=$WORK/gen
OUT=$WORK/out
trap "rm -rf $WORK" EXIT
mkdir -p $GEN/com/glasscast/app
# R is generated from res/. GlassCast has no BuildConfig (the build feature
# is off), so there's nothing to stand in for there.
python3 -I $HERE/gen_r.py $P/app/src/main/res $GEN/com/glasscast/app/R.java
CP=$(ls $TC/cp/*.jar $TC/extra/*.jar 2>/dev/null | grep -v compiler-plugin | tr '\n' ':')
COMPOSE=$TC/kc/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar
SRC=$(find $P/app/src/main/java -name '*.kt' -o -name '*.java')
# Stand-ins for libraries missing from the staged cache, if ever needed: one dir each.
for d in $HERE/stubs/*/ ${EXTRA_STUBS:-}; do [ -d "$d" ] && SRC="$SRC $(find $d -name '*.java' -o -name '*.kt')"; done
KC=$(ls $TC/kc/*.jar | grep -v compiler-plugin | tr '\n' ':')
exec 9>/tmp/tc.lock; flock 9
RESULT=$(env -u JAVA_TOOL_OPTIONS java -Xmx3g -Xss8m -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$CP" \
  -Xplugin=$COMPOSE \
  -jvm-target 17 -opt-in=androidx.media3.common.util.UnstableApi \
  -nowarn -d $OUT $SRC $GEN/com/glasscast/app/R.java 2>&1 \
  | grep -v -e "^warning:" -e "^Picked up JAVA_TOOL_OPTIONS" || true)
if [ -n "$RESULT" ]; then
  echo "$RESULT"
  exit 1
fi
# Silence only counts if the compiler actually produced the app's classes.
if [ ! -f "$OUT/com/glasscast/app/GlassCastApp.class" ]; then
  echo "typecheck: FAILED — the compiler printed nothing but produced no classes"
  exit 2
fi
echo "typecheck: clean"
