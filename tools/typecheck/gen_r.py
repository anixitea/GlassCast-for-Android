"""Generates a stand-in R.java from a res/ folder, enough for type-checking."""
import os, re, sys
from xml.etree import ElementTree as ET

res, out = sys.argv[1], sys.argv[2]
types = {}
def add(t, name):
    types.setdefault(t, set()).add(re.sub(r'[.\-]', '_', name))

for d in sorted(os.listdir(res)):
    full = os.path.join(res, d)
    if not os.path.isdir(full):
        continue
    base = d.split('-')[0]
    for f in os.listdir(full):
        name = f.split('.')[0]
        if base == 'values':
            try:
                root = ET.parse(os.path.join(full, f)).getroot()
            except Exception:
                continue
            for el in root:
                tag = el.tag
                n = el.get('name')
                if not n:
                    continue
                if tag == 'item':
                    tag = el.get('type') or 'id'
                if tag in ('string-array', 'integer-array', 'array'):
                    tag = 'array'
                if tag == 'declare-styleable':
                    tag = 'styleable'
                add(tag, n)
        else:
            add(base, name)
            # Ids declared in layouts and other XML (@+id/name).
            if f.endswith('.xml'):
                try:
                    text = open(os.path.join(full, f), encoding='utf-8').read()
                except Exception:
                    continue
                for n in re.findall(r'@\+id/([A-Za-z0-9_.]+)', text):
                    add('id', n)

lines = ['package com.glasscast.app;', 'public final class R {']
for t, names in sorted(types.items()):
    lines.append(f'  public static final class {t} {{')
    for i, n in enumerate(sorted(names)):
        lines.append(f'    public static final int {n} = 0x7f{abs(hash(t)) % 255:02x}{i:04x};')
    lines.append('  }')
lines.append('}')
os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, 'w').write('\n'.join(lines) + '\n')
