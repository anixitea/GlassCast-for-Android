#!/bin/sh
# Parse every Kotlin file and report syntax errors only.
#
# Fails loudly if the compiler is missing. It used to be run as a bare
# pipeline — `kotlinc … | grep …` — and when /tmp was cleared the compiler
# vanished, the pipeline printed nothing, and "no output" read as "no errors".
# Several rounds shipped unchecked that way.
KOTLINC="${KOTLINC:-/tmp/kotlinc/bin/kotlinc}"
if [ ! -x "$KOTLINC" ]; then
  echo "syntaxcheck: FAILED — kotlinc not found at $KOTLINC"
  exit 2
fi
OUT=$(mktemp)
"$KOTLINC" $(find app/src -name "*.kt") -d /tmp/syntaxcheck-out > "$OUT" 2>&1
# Without the Android classpath every file has unresolved references; only
# parse errors mean the source itself is malformed.
grep -E "Syntax error|Expecting |Unexpected tokens|Unclosed|Unresolved label" "$OUT" | sed 's#.*/app/src/main/java/##' > "$OUT.syntax"
COUNT=$(wc -l < "$OUT.syntax")
if [ "$COUNT" -gt 0 ]; then
  cat "$OUT.syntax" | head -40
  echo "syntaxcheck: $COUNT syntax problems"
  exit 1
fi
# Sanity: the compiler must actually have run over the sources.
if ! grep -q "error:\|warning:" "$OUT"; then
  echo "syntaxcheck: FAILED — compiler produced no diagnostics at all; it probably didn't run"
  exit 2
fi
echo "syntaxcheck: problems: 0"
