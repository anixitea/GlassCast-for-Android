import re, os, collections
root='app/src/main/java/com/glasscast/app'
problems=[]
for dp,_,fs in os.walk(root):
    for f in sorted(fs):
        if not f.endswith('.kt'): continue
        path=os.path.join(dp,f)
        lines=open(path).read().split('\n')
        imports=[l for l in lines if l.startswith('import ')]
        # duplicates
        dupes=[i for i,c in collections.Counter(imports).items() if c>1]
        for d in dupes: problems.append((f,'DUPLICATE',d))
        # doubled suffixes, the signature of a prefix-collision replace
        for imp in imports:
            name=imp.rsplit('.',1)[-1]
            for suffix in ('Defaults','State','Scope','Api','Color','Size'):
                if name.endswith(suffix*2):
                    problems.append((f,'DOUBLED',imp))
        # imports out of a sane package (typo detector: unknown trailing junk)
        for imp in imports:
            if re.search(r'[A-Za-z]\.[a-z]+[A-Z]\w*\s*$', imp) and imp.count(' ')>1:
                problems.append((f,'MALFORMED',imp))
for p in problems: print(p)
print('files scanned:', sum(1 for dp,_,fs in os.walk(root) for f in fs if f.endswith('.kt')))
print('problems:', len(problems))
