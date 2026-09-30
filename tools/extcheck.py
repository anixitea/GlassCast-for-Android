"""
Compose modifier and helper extensions used without their import.

Kotlin extension functions must be imported by name, and a file that has never
used one (say `.clickable`) gets "Unresolved reference" at compile time. The
syntax check can't see this and identcheck only covers icons, so this maps the
extensions GlassCast actually uses to the import each needs, and flags any file
that calls one without importing it (or declaring it itself).
"""
import os, re, sys

EXT = {
    # foundation
    "clickable": "androidx.compose.foundation.clickable",
    "combinedClickable": "androidx.compose.foundation.combinedClickable",
    "background": "androidx.compose.foundation.background",
    "border": "androidx.compose.foundation.border",
    "focusable": "androidx.compose.foundation.focusable",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "horizontalScroll": "androidx.compose.foundation.horizontalScroll",
    "focusGroup": "androidx.compose.foundation.focusGroup",
    # layout
    "padding": "androidx.compose.foundation.layout.padding",
    "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
    "size": "androidx.compose.foundation.layout.size",
    "width": "androidx.compose.foundation.layout.width",
    "height": "androidx.compose.foundation.layout.height",
    "heightIn": "androidx.compose.foundation.layout.heightIn",
    "widthIn": "androidx.compose.foundation.layout.widthIn",
    "offset": "androidx.compose.foundation.layout.offset",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "statusBarsPadding": "androidx.compose.foundation.layout.statusBarsPadding",
    "navigationBarsPadding": "androidx.compose.foundation.layout.navigationBarsPadding",
    # ui
    "clip": "androidx.compose.ui.draw.clip",
    "alpha": "androidx.compose.ui.draw.alpha",
    "blur": "androidx.compose.ui.draw.blur",
    "shadow": "androidx.compose.ui.draw.shadow",
    "scale": "androidx.compose.ui.draw.scale",
    "drawBehind": "androidx.compose.ui.draw.drawBehind",
    "drawWithContent": "androidx.compose.ui.draw.drawWithContent",
    "drawWithCache": "androidx.compose.ui.draw.drawWithCache",
    "graphicsLayer": "androidx.compose.ui.graphics.graphicsLayer",
    "layout": "androidx.compose.ui.layout.layout",
    "onGloballyPositioned": "androidx.compose.ui.layout.onGloballyPositioned",
    "focusRequester": "androidx.compose.ui.focus.focusRequester",
    "onFocusChanged": "androidx.compose.ui.focus.onFocusChanged",
    "pointerInput": "androidx.compose.ui.input.pointer.pointerInput",
    "nestedScroll": "androidx.compose.ui.input.nestedscroll.nestedScroll",
    "onPreviewKeyEvent": "androidx.compose.ui.input.key.onPreviewKeyEvent",
    # animation
    "animateItem": None,  # member of LazyItemScope — no import
}

root = "app/src/main/java"
problems = 0
files = 0
for dirpath, _, names in os.walk(root):
    for name in names:
        if not name.endswith(".kt"):
            continue
        files += 1
        path = os.path.join(dirpath, name)
        src = open(path).read()
        body = re.sub(r"//[^\n]*|/\*.*?\*/", "", src, flags=re.S)
        body = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', body)
        imports = set(re.findall(r"^import\s+([\w.]+)", src, flags=re.M))
        for ext, imp in EXT.items():
            if imp is None:
                continue
            # A modifier-style call: `.name(` or `.name {` on a new line or chained.
            if not re.search(r"\.\s*" + ext + r"\s*[({]", body):
                continue
            if imp in imports:
                continue
            # declared locally (e.g. our own Modifier.tvFocusable) — not this list's concern
            if re.search(r"fun\s+(?:[\w.<>]+\.)?" + ext + r"\s*\(", body):
                continue
            # fully-qualified use counts as imported
            if re.search(re.escape(imp) + r"\s*[({]", body):
                continue
            print(f"{path.replace(root + '/', '')}: uses .{ext} without `import {imp}`")
            problems += 1
print(f"files: {files} | problems: {problems}")
sys.exit(1 if problems else 0)
