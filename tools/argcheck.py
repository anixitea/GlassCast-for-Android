
import re, os
root='app/src/main/java/com/glasscast/app'

def strip_comments(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = re.sub(r'//[^\n]*', '', t)
    return t

def split_top(text):
    parts, depth, cur = [], 0, ''
    for ch in text:
        if ch in '([{': depth += 1
        elif ch in ')]}': depth -= 1
        if ch == ',' and depth == 0:
            parts.append(cur); cur = ''
        else:
            cur += ch
    parts.append(cur)
    return [p.strip() for p in parts if p.strip()]

def params_of(src, open_idx):
    i = open_idx; depth = 1
    while i < len(src) and depth:
        if src[i]=='(': depth+=1
        elif src[i]==')': depth-=1
        i+=1
    return src[open_idx:i-1], i

# key signatures by (file, name) AND by name, so same-name funcs union safely
sigs = {}
for dp,_,fs in os.walk(root):
    for f in fs:
        if not f.endswith('.kt'): continue
        src = strip_comments(open(os.path.join(dp,f)).read())
        for m in re.finditer(r'\bfun\s+(?:<[^>]+>\s*)?(?:[A-Za-z0-9_.]+\.)?([A-Za-z][A-Za-z0-9]*)\s*\(', src):
            name = m.group(1)
            params, _ = params_of(src, m.end())
            names = set()
            for p in split_top(params):
                mm = re.match(r'(?:@\w+\s+)*(?:vararg\s+)?(\w+)\s*:', p)
                if mm: names.add(mm.group(1))
            sigs.setdefault(name, set()).update(names)

problems = []
for dp,_,fs in os.walk(root):
    for f in fs:
        if not f.endswith('.kt'): continue
        src = strip_comments(open(os.path.join(dp,f)).read())
        for name, params in sigs.items():
            if not params: continue
            for m in re.finditer(r'(?<![A-Za-z0-9_.])'+re.escape(name)+r'\s*\(', src):
                if 'fun ' in src[max(0,m.start()-6):m.start()]: continue
                call, _ = params_of(src, m.end())
                # Only arguments at the call's own depth: a nested
                # copy(foo = 1) or PaddingValues(vertical = 2) is not ours.
                named = set()
                for part in split_top(call):
                    mm = re.match(r'(\w+)\s*=(?!=)', part)
                    if mm: named.add(mm.group(1))
                unknown = named - params
                if unknown:
                    problems.append((f, name, sorted(unknown)))
for p in sorted(set(map(str, problems))): print(p)
print('signatures:', len(sigs), '| problems:', len(set(map(str,problems))))

