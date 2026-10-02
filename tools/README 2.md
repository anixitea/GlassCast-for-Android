# tools

Two static checks, run before packaging. Neither is a compiler; each catches a
class of mistake that has actually shipped from this project.

## importcheck.py

Duplicate imports, and names ending in a doubled suffix (`SwitchDefaultsDefaults`).

Both are the signature of a careless find-and-replace: `import ...Slider` is a
*prefix* of `import ...SliderDefaults`, so replacing the first line hits the
second too — duplicating one import and mangling another. That shipped once and
cost a build.

## argcheck.py

Every named argument at a call site, checked against the real parameter list of
the function being called.

Catches a function whose signature was edited away underneath its callers — a
`size = 44.dp` passed to a `SeekButton` that no longer takes `size`. That also
shipped once. Signatures are unioned by name, so same-named helpers in different
files are treated together; that direction only produces false negatives.

Two parsing details worth keeping: `>` is not treated as a closing bracket
(`() -> Unit` parameters otherwise wreck the depth count), and named arguments
are only read at the call's own depth (a nested `copy(played = true)` is not an
argument to the outer call).

## identcheck.py

Two more classes, both of which shipped.

**Icon extension properties.** `Icons.Filled.Replay30` is an extension
property living in `androidx.compose.material.icons.filled`. Spelling out the
full path — `androidx.compose.material.icons.Icons.Filled.Replay30` — looks like
it should work and doesn't: an extension still needs its own import. Every
`Icons.<Style>.<Name>` must have the matching import.

**Assignments to names that were never declared.** A replace of `selectedFeed`
with `shown` also rewrote `selectedFeedUrl` into `shownUrl` — the same prefix
collision `importcheck` guards against, but in code. The check looks at callback
lambdas (`onBack = { x = null }`) and requires `x` to be declared. Receiver
blocks like `graphicsLayer { scaleX = … }` are skipped, since those set the
receiver's own properties; a brace preceded by an identifier marks one.

Verified both ways: silent on the working tree, and it names both bugs when
they're put back into a scratch copy.

**The actual fix for the prefix class is upstream of any check**: replacements
of identifiers use word boundaries (`\bselectedFeed\b`), never a bare substring.

What none of these can catch: type and nullability errors, like a `when`
subject that isn't narrowed by an earlier `null` branch. That needs a compiler.
