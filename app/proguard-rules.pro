# Default rules. Nothing app-specific needed while minify is off.

# The Cast SDK finds its options provider by name, from a manifest meta-data
# string. R8 can't see that reference and would strip the class, crashing the
# first time Cast starts — only in a minified release build, never in debug.
-keep class com.glasscast.app.player.CastOptionsProvider { *; }
