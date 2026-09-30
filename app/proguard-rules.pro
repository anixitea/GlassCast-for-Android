# R8 is on for release builds: shrinking and optimisation, for speed.

# Names are left readable. Testers send crash screenshots; obfuscated traces
# would need the mapping file for every build to mean anything, and the speed
# comes from optimisation, not from renaming.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# The Cast SDK finds its options provider by name, from a manifest meta-data
# string. R8 can't see that reference and would strip the class, crashing the
# first time Cast starts — only in a minified release build, never in debug.
-keep class com.glasscast.app.player.CastOptionsProvider { *; }

# Everything else is covered: activities, services and receivers are kept via
# the manifest; WorkManager, Media3 and the Cast SDK ship their own rules; and
# GlassCast uses no reflection or name-based lookups of its own (org.json is
# read and written by hand).
