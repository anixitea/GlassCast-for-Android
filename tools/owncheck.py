"""
The app's own declarations used from another package without an import.

Kotlin needs `import com.glasscast.app.data.Episode` in a file outside that
package — and a script checking "is that import already there?" by substring
is fooled by `import com.glasscast.app.data.EpisodeSort`. This lists every
top-level class, object, interface, typealias, function and property the app
declares, then flags any file in another package that uses one without an
exact import (or a wildcard, or a fully qualified reference).
"""
import os, re, sys
root = "app/src/main/java"
decl = re.compile(r'^(?:(?:public|internal|private|data|enum|sealed|abstract|open|inline|value|annotation|fun|suspend|operator|infix|tailrec|const)\s+)*(class|object|interface|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)')
pkgs = {}; decls = {}; sources = {}
for dp, _, names in os.walk(root):
    for n in names:
        if not n.endswith(".kt"): continue
        p = os.path.join(dp, n); s = open(p, encoding="utf-8").read(); sources[p] = s
        pkg = re.search(r'^package\s+([\w.]+)', s, re.M).group(1); pkgs[p] = pkg
        for line in s.split("\n"):
            if line.startswith((" ", "\t")) or line.startswith("private "): continue  # top level, non-private
            m = decl.match(line)
            if m and m.group(1) != "fun" or (m and not re.match(r'.*fun\s+[\w.<>?, ]+\.\w+\s*\(', line)):
                if m: decls.setdefault(m.group(2), set()).add(pkg)
def strip(s):
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    s = re.sub(r'//[^\n]*', '', s)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', s)
problems = 0
for p, s in sources.items():
    pkg = pkgs[p]
    imports = set(re.findall(r'^import\s+([\w.]+)', s, re.M))
    wild = {i[:-2] for i in re.findall(r'^import\s+([\w.]+\.\*)', s, re.M)}
    body = strip(re.sub(r'^(import|package)\s.*$', '', s, flags=re.M))
    for name, owners in decls.items():
        if pkg in owners: continue
        if len(owners) > 1: continue          # ambiguous simple name; skip
        owner = next(iter(owners))
        if f"{owner}.{name}" in imports or owner in wild: continue
        # Another class of the same name imported (android.provider.Settings): that's what the file means.
        if any(i.endswith("." + name) for i in imports): continue
        for m in re.finditer(r'(?<![\w.])' + re.escape(name) + r'\b', body):
            # declared locally in this file (parameter, local val, class) — not a reference
            if re.search(r'(class|object|interface|fun|val|var)\s+' + re.escape(name) + r'\b', body): break
            print(f"{p.replace(root + '/', '')}: uses {name} without `import {owner}.{name}`")
            problems += 1
            break
print(f"declarations: {len(decls)} | problems: {problems}")

# ---- second pass: library classes this project imports somewhere ------------
# Any capitalized name imported by some file (Path, Brush, LocalContext…) that
# another file uses without importing it — the same substring trap, for
# library classes rather than the app's own.
known = {}
for p, s in sources.items():
    for fq in re.findall(r'^import\s+([\w.]+)', s, re.M):
        simple = fq.rsplit(".", 1)[-1]
        if simple[:1].isupper():
            known.setdefault(simple, set()).add(fq)
lib_problems = 0
for p, s in sources.items():
    imports = set(re.findall(r'^import\s+([\w.]+)', s, re.M))
    simple_imported = {i.rsplit(".", 1)[-1] for i in imports}
    wild = {i[:-2] for i in re.findall(r'^import\s+([\w.]+\.\*)', s, re.M)}
    body = strip(re.sub(r'^(import|package)\s.*$', '', s, flags=re.M))
    for name, fqs in known.items():
        if name in simple_imported or name in decls and pkgs[p] in decls[name]: continue
        if any(fq.rsplit(".", 1)[0] in wild for fq in fqs): continue
        if not re.search(r'(?<![\w.])' + re.escape(name) + r'\b', body): continue
        if re.search(r'(class|object|interface|typealias|fun|val|var)\s+' + re.escape(name) + r'\b', body): continue
        # An enum entry declared in this file (Haptic.Pause), not the library class.
        if re.search(r'^\s+' + re.escape(name) + r'\s*[,(]?\s*$', body, re.M): continue
        if re.search(r'\b' + re.escape(name) + r'\s*[:,)]\s', body) and re.search(r'\(\s*' + re.escape(name) + r'\s*:', body): continue
        print(f"{p.replace(root + '/', '')}: uses {name} without importing it ({sorted(fqs)[0]})")
        lib_problems += 1
print(f"library names: {len(known)} | problems: {lib_problems}")
sys.exit(1 if (problems or lib_problems) else 0)
