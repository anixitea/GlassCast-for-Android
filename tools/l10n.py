#!/usr/bin/env python3
"""
Regenerates the four language tables in ui/L10n.kt from docs/translations.txt.

    python3 tools/l10n.py           write the tables
    python3 tools/l10n.py --check   list tr("...") text missing from translations.txt

translations.txt is the one place translations live: one line per string,

    English || 한국어 || Deutsch || Español || Nederlands

in the order the tables should list them. Blank lines and lines starting with
# are skipped. The separator is exactly " || ", and nothing else is trimmed,
so a string that starts with a space (" · UPDATED {0}") keeps it.

Only the entries between `hashMapOf(` and its closing `)` in each table are
written; the file's header, tr() and the table structure are left as they are.
Run it from the project root, as the other tools are. Safe to run any number
of times: the same translations.txt gives the same L10n.kt.

--check reads every literal tr("...") under app/src/main/java (and every
string passed to one of the TR_HELPERS, which call tr() themselves) and lists
the ones translations.txt doesn't have, with where they're used. It also flags a
tr() whose text is a string template ("$name"), since the table can't hold
text that's different every time, and says when L10n.kt doesn't match
translations.txt (edited by hand, or not regenerated).
"""
import os
import re
import sys

ROOT = os.getcwd()
TRANSLATIONS = os.path.join(ROOT, "docs", "translations.txt")
L10N = os.path.join(ROOT, "app", "src", "main", "java", "com", "glasscast", "app", "ui", "L10n.kt")
SOURCES = os.path.join(ROOT, "app", "src", "main", "java")
LANGS = ["ko", "de", "es", "nl"]
SEP = " || "

TABLE = re.compile(
    r"(    val (\w+): Map<String, String> by lazy \{\n        hashMapOf\(\n)(.*?)(\n        \)\n    \})",
    re.S,
)
PLACEHOLDER = re.compile(r"\{\d+\}")


def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)


def read_translations():
    """[(english, {lang: text}), ...] in file order; exits on a malformed line."""
    rows, seen, problems = [], {}, []
    with open(TRANSLATIONS, encoding="utf-8") as f:
        for number, raw in enumerate(f, 1):
            line = raw.rstrip("\r\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            fields = line.split(SEP)
            if len(fields) != 1 + len(LANGS):
                problems.append(f"line {number}: {len(fields)} fields, expected {1 + len(LANGS)}")
                continue
            english, texts = fields[0], fields[1:]
            if english in seen:
                problems.append(f"line {number}: {english!r} is already on line {seen[english]}")
                continue
            if any(not t.strip() for t in texts):
                problems.append(f"line {number}: an empty translation")
                continue
            # A translation that loses or invents a placeholder shows the
            # wrong number, or none, with no error anywhere.
            wanted = sorted(PLACEHOLDER.findall(english))
            for lang, text in zip(LANGS, texts):
                if sorted(PLACEHOLDER.findall(text)) != wanted:
                    problems.append(f"line {number}: {lang} placeholders differ from the English")
            seen[english] = number
            rows.append((english, dict(zip(LANGS, texts))))
    if problems:
        fail("translations.txt:\n  " + "\n  ".join(problems))
    return rows


def kotlin_string(text):
    """A Kotlin string literal: backslashes, quotes and $ escaped."""
    out = text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    out = out.replace("\t", "\\t")
    return '"' + out + '"'


def kotlin_unescape(body):
    """The text of a Kotlin string literal's body (the common escapes only)."""
    return re.sub(r'\\(.)', lambda m: {"n": "\n", "t": "\t", "r": "\r"}.get(m.group(1), m.group(1)), body)


def render(rows):
    """(current L10n.kt, L10n.kt with its tables generated from [rows])."""
    with open(L10N, encoding="utf-8") as f:
        source = f.read()
    found = [m.group(2) for m in TABLE.finditer(source)]
    if found != LANGS:
        fail(f"L10n.kt: expected tables {LANGS}, found {found}")

    def table(match):
        lang = match.group(2)
        lines = [f"            {kotlin_string(en)} to {kotlin_string(texts[lang])}" for en, texts in rows]
        return match.group(1) + ",\n".join(lines) + match.group(4)

    return source, TABLE.sub(table, source)


def write_tables(rows):
    source, updated = render(rows)
    if updated != source:
        with open(L10N, "w", encoding="utf-8") as f:
            f.write(updated)
        print(f"L10n.kt: {len(rows)} strings in each of {', '.join(LANGS)}")
    else:
        print(f"L10n.kt already up to date ({len(rows)} strings)")


# tr( not preceded by part of another name (attr(, str(), then its first
# argument when that's a plain string literal.
TR_CALL = re.compile(r'(?<![\w$])tr\(\s*"((?:[^"\\\n]|\\.)*)"')
TEMPLATE = re.compile(r'(?<!\\)\$[\w{]')
STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"')

# Helpers that hand their string arguments to tr() themselves, so the text
# is written at the call rather than inside a tr(). Every string literal
# passed to one counts as tr() text. Add a helper here when you write one.
TR_HELPERS = []


def helper_arguments(source, name):
    """(offset, literal body) for each string literal passed directly to [name](...)."""
    for m in re.finditer(r'(?<![\w$])' + re.escape(name) + r'\(', source):
        depth, i = 1, m.end()
        while i < len(source) and depth:
            c = source[i]
            if c == '"':
                literal = STRING.match(source, i)
                if literal is None:
                    break
                if depth == 1:
                    yield i, literal.group(1)
                i = literal.end()
                continue
            depth += {"(": 1, ")": -1}.get(c, 0)
            i += 1


def tr_literals():
    """{text: [file:line, ...]} for every literal tr("...") call, plus the template ones."""
    used, templates = {}, []
    for folder, _, files in os.walk(SOURCES):
        for name in sorted(files):
            if not name.endswith(".kt") or name == "L10n.kt":
                continue
            path = os.path.join(folder, name)
            with open(path, encoding="utf-8") as f:
                source = f.read()
            calls = [(m.start(), m.group(1)) for m in TR_CALL.finditer(source)]
            for helper in TR_HELPERS:
                calls += helper_arguments(source, helper)
            for offset, body in sorted(calls):
                where = f"{os.path.relpath(path, SOURCES)}:{source.count(chr(10), 0, offset) + 1}"
                if TEMPLATE.search(body):
                    templates.append((where, body))
                    continue
                used.setdefault(kotlin_unescape(body), []).append(where)
    return used, templates


def check(rows):
    known = {en for en, _ in rows}
    used, templates = tr_literals()
    missing = sorted((text for text in used if text not in known), key=lambda t: used[t][0])
    for text in missing:
        print(f"missing: {text!r}  ({', '.join(used[text])})")
    for where, body in templates:
        print(f"template: tr(\"{body}\") at {where}: use {{0}} and an argument instead")
    source, updated = render(rows)
    stale = updated != source
    if stale:
        print("L10n.kt doesn't match translations.txt: run python3 tools/l10n.py")
    if missing or templates or stale:
        sys.exit(1)


def main():
    args = sys.argv[1:]
    if args not in ([], ["--check"]):
        fail(__doc__)
    rows = read_translations()
    if args == ["--check"]:
        check(rows)
    else:
        write_tables(rows)


if __name__ == "__main__":
    main()
