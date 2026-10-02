"""
Two checks for mistakes that compiled clean in my head and failed in Gradle.

1. Icon extension properties. `Icons.Filled.Replay30` is an extension property
   defined in `androidx.compose.material.icons.filled`. Spelling out the full
   path `androidx.compose.material.icons.Icons.Filled.Replay30` does NOT bring
   it into scope -- it needs its own import. Every Icons.<Style>.<Name> used must
   have the matching import.

2. Assignments to names that don't exist. A find-and-replace of `selectedFeed`
   also rewrote `selectedFeedUrl`, producing `{ shownUrl = null }`. Any bare
   `name = ...` at the start of a lambda or after a semicolon is an assignment,
   so `name` must be declared somewhere in the file (var/val/param) or project.
"""
import os, re, sys

root = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java/com/glasscast/app'
STYLE = {
    'Filled': 'filled', 'Outlined': 'outlined', 'Rounded': 'rounded',
    'Sharp': 'sharp', 'TwoTone': 'twotone',
    'AutoMirrored.Filled': 'automirrored.filled',
    'AutoMirrored.Outlined': 'automirrored.outlined',
    'AutoMirrored.Rounded': 'automirrored.rounded',
}

def strip(s):
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    s = re.sub(r'//[^\n]*', '', s)
    s = re.sub(r'"(?:\\.|[^"\\])*"', '""', s)
    return s

files = []
for dp, _, fs in os.walk(root):
    for f in fs:
        if f.endswith('.kt'):
            files.append(os.path.join(dp, f))

# project-wide declarations (for names declared in another file / as properties)
declared_anywhere = set()
for p in files:
    src = strip(open(p).read())
    declared_anywhere |= set(re.findall(r'\b(?:var|val)\s+(\w+)', src))

problems = []
for p in files:
    raw = open(p).read()
    src = strip(raw)
    imports = set(l.strip() for l in raw.split('\n') if l.startswith('import '))
    f = os.path.basename(p)

    for m in re.finditer(r'\bIcons\.((?:AutoMirrored\.)?(?:Filled|Outlined|Rounded|Sharp|TwoTone))\.(\w+)', src):
        style, name = m.group(1), m.group(2)
        need = f'import androidx.compose.material.icons.{STYLE[style]}.{name}'
        if need not in imports:
            problems.append((f, 'ICON', f'Icons.{style}.{name} needs `{need}`'))

    local = set(re.findall(r'\b(?:var|val)\s+(\w+)', src))
    local |= set(re.findall(r'[(,]\s*(\w+)\s*:', src))          # parameters
    local |= set(re.findall(r'\{\s*(\w+)\s*->', src))            # lambda params
    # Only callback lambdas -- `onBack = { x = ... }`, `(... { x = ... })` --
    # where a bare assignment must target a declared var. Receiver blocks such
    # as `graphicsLayer { scaleX = ... }` or `.apply { flags = ... }` set the
    # receiver's own properties, so a brace preceded by an identifier is skipped.
    for m in re.finditer(r'(?:[=(,]|->)\s*\{\s*([a-z]\w*)\s*=(?!=)', src):
        name = m.group(1)
        if name not in local and name not in declared_anywhere:
            problems.append((f, 'ASSIGN', f'`{name} = ...` but `{name}` is never declared'))

for f, kind, msg in sorted(set(problems)):
    print(f'{kind:6} {f}: {msg}')
print(f'files: {len(files)} | problems: {len(set(problems))}')
