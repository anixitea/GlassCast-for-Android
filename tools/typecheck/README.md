# typecheck

A real Kotlin type-check of the whole app without Gradle: the app's sources
compiled by the Kotlin 2.0.21 compiler, with the Compose plugin, against the
actual library jars. It catches what the six static checks can't: type and
nullability errors, wrong overloads, Compose misuse, missing imports of
members. Ported from GlassPlay in 1.6.

    sh tools/typecheck/typecheck.sh            # errors, or "typecheck: clean"

It exits 1 with the errors, 2 when it couldn't run at all (no jars, or the
compiler produced nothing), and 0 only when the app's classes were actually
compiled. It stands in for R (gen_r.py reads res/). GlassCast has no
BuildConfig, so there's nothing to stand in for there.

## The jars

Google Maven and Maven Central can't be reached from Claude's workspace, so
the jars come from the Mac's own Gradle cache, through a linked session
(read access to `~/.gradle/caches/modules-2/files-2.1` and
`~/Library/Android/sdk/platforms/android-35`). `$TC_JARS` defaults to
`/home/claude/tc`.

Into `$TC_JARS/cp/`: android.jar (android-35), and the classes.jar of each
.aar (or the .jar) for:

- Compose 1.7.6 (BOM 2024.12.01): runtime, ui, ui-graphics, ui-text, ui-unit,
  ui-geometry, ui-util, foundation, foundation-layout, animation,
  animation-core, material-icons-core, material-icons-extended, ripple
- material3 1.3.1
- Media3 1.5.1: common, container, database, datasource, decoder, exoplayer,
  extractor, session
- core 1.15.0 (and core-ktx), versionedparcelable 1.1.1 (IconCompat's
  supertype, for the launcher shortcuts), lifecycle 2.8.7 (common, runtime,
  runtime-ktx, runtime-compose, viewmodel, livedata-core), activity 1.9.3
  (activity, activity-compose), savedstate 1.2.1, annotation-jvm 1.8.1,
  annotation-experimental 1.4.1, collection 1.4.4
- palette 1.0.0, media 1.7.0, work-runtime 2.9.1, profileinstaller 1.3.1
- haze 1.3.1 and haze-materials 1.3.1
- kotlin-stdlib 2.0.21, kotlinx-coroutines-core and -android 1.9.0, guava
  (with listenablefuture)

Into `$TC_JARS/extra/` (Cast; also on the classpath): media3-cast 1.5.1,
play-services-cast, play-services-cast-framework 21.5.0, play-services-base,
play-services-basement, play-services-flags, play-services-tasks, and
mediarouter 1.7.0; and for Sign in with Google (1.7), play-services-auth
21.4.0, play-services-auth-base 18.0.10 and play-services-auth-api-phone
18.0.2.

Into `$TC_JARS/kc/`: kotlin-compiler-embeddable, kotlin-daemon-embeddable and
kotlin-script-runtime (2.0.21), kotlin-reflect, trove4j, annotations-13.0,
kotlinx-coroutines-core, kotlin-stdlib 2.0.21, and
`kotlin-compose-compiler-plugin-embeddable-2.0.21.jar`. The plain kotlinc
download won't do: its Compose plugin is built for the non-embeddable
compiler, and the run stops with "not compatible with this version of
compiler".

media3-exoplayer-hls isn't needed: nothing references its classes, and
DefaultMediaSourceFactory loads it by reflection.

## Staging them (1.7)

With the session linked to the Mac, ask once for read access to
`~/.gradle/caches/modules-2/files-2.1` and
`~/Library/Android/sdk/platforms/android-35`. In the linked shell those are
`$HOME/mnt/files-2.1` and `$HOME/mnt/android-35`; list each
group/artifact/version above there to get the exact file (the `-android`
artifacts for Compose and lifecycle, the `-jvm` ones for annotation,
collection and coroutines). Stage them (50 files a call), then in the
workspace copy each .jar, and `unzip -p x.aar classes.jar` each .aar, into
`cp/`, `extra/` or `kc/`. kotlin-stdlib and kotlinx-coroutines-core-jvm go
in both `cp/` and `kc/`. A full run takes about a minute.

The workspace sets JAVA_TOOL_OPTIONS, and Java announces it on stderr; the
script runs the compiler without it, so that line isn't taken for an error.

