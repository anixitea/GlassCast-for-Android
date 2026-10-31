# GlassCast — handoff to a new chat

Written September 30, 2026, at the end of the chat that built 1.1 → 1.3;
updated through 1.4 and 1.5, on October 9 for 1.6 (the OPPO Pad mini
startup work and the GlassPlay ports), on October 12 for 1.7, and on
October 13 for 1.8, on October 19 for 1.9 (Sea Glass), on October 20
for 2.0's first round (app icons and Liquid Glass), and on October 30 for
2.0's second round (Liquid Glass through the app, history, faster refresh),
and again that day for the third (the look renamed Frosted Glass), the
fourth (glass on everything, a steadier swipe), the fifth (Android Auto
reworked; the look is Glass, frosted or liquid), the sixth (Glass on the
TV) and the seventh (the finish switch fixed, Liquid retuned).
Everything here is current as of the GlassCast.zip packaged alongside it.

---

## 1. Starting the new chat

1. Upload **GlassCast.zip** and **GlassCast-HANDOFF.md** (this file).
   Add **GlassCast-macOS.zip** only when working on the Mac app.
2. Claude: read this file first, then `cd /home/claude && unzip -q GlassCast.zip`
   (the zip's root folder is `GlassCast/`). This file is also in the zip at
   `docs/HANDOFF.md`, next to `docs/DEVELOPMENT.md` (per-version design notes).
3. If a detail is missing, search past chats for "GlassCast" and the topic.

---

## 2. Who, and how we work

- **Jonathan** — GitHub `anixitea` (repo `anixitea/GlassCast-for-Android`),
  Reddit `u/jung-gaon`. Builds in Android Studio on his Mac
  (`/Users/jonnydeforrest/Downloads/GlassCast`), **release variant**, signed
  with his own key (created 2026-09-30, kept in `~/Documents/App Key/`, never
  committed). Tests on a **Xiaomi 17 Pro Max (HyperOS)**, an **OPPO Pad mini**
  (8.8", 2520×1680, 60–144 Hz, ColorOS 16, likely a China-market unit) and a
  **Google TV Streamer**. Sends Gradle logs, crash screenshots and
  screen-recording GIFs.
- **GlassPlay** is Jonathan's YouTube Music app, built on GlassCast's UI (1.0).
  Fixes made there come back here as reference files to adapt, never to paste
  (package names and types differ).
- **Preferences**
  - American spelling everywhere.
  - **No explanatory text in the UI** — descriptions under settings, speed
    panel notes, subtitles were all removed in 1.3. Don't add new ones.
  - Visually polished; **Cider** (Apple Music client) is the design reference.
  - Explain a bug's cause plainly, then fix it. Be honest about limits.
  - Smoothness is judged on **release** builds only (debug is far slower).
  - **README voice**: plain, first person, like a person wrote it. No bold
    taglines, no "Features" lists with bold lead-ins, no arrows, emoji or
    em dashes, no marketing words ("seamless", "no-nonsense", "feels as good
    as it looks"). Keep it that way when adding to it.
- Build variants: `debug`, `fast` (debug-signed, not debuggable),
  `release` (R8 on, `-dontobfuscate`, keeps `CastOptionsProvider`).

---

## 3. Claude's workflow — read before touching code

**Keep the project in git.** Unzip into a fresh folder, copy it to
`/home/claude/GlassCast`, `git init`, commit. Jonathan's own zips carry
`app/build` (~200 MB), `.gradle`, `.idea` and `.kotlin`; `.gitignore` keeps
them out.

**Six static checks, all must pass before packaging** (from `/home/claude/GlassCast`):

```
sh tools/syntaxcheck.sh           # kotlinc parse
python3 -I tools/importcheck.py
python3 -I tools/argcheck.py      # named arguments vs. signatures
python3 -I tools/identcheck.py    # Material icons exist and are imported
python3 -I tools/extcheck.py      # Compose modifier extensions imported
python3 -I tools/owncheck.py      # app's own + library classes imported (added after the 1.3 break)
python3 -I tools/l10n.py --check  # every tr() text translated, L10n.kt in sync
```
syntaxcheck.sh does **not** download kotlinc; if it says "kotlinc not found":
`cd /tmp && curl -sSL -o k.zip https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip && unzip -q -o k.zip`

**Real type-check, when the session is linked to the Mac:** stage the jars
listed in `tools/typecheck/README.md` from the Mac's Gradle cache, then
`sh tools/typecheck/typecheck.sh`. Do it whenever the Mac is linked: it is
the only check that resolves library symbols, and 1.7 was the first version
packaged with it clean. Google Maven and Maven Central are blocked
from the workspace. API 35's android.jar alone can come from GitHub
(`Sable/android-platforms`, sparse checkout of `android-35/android.jar`) for
checking plain-Android code.

None of these checks read the Gradle scripts (`*.gradle.kts`). In a build
script, `java` is Gradle's Java extension, so write `import java.util.X` at
the top rather than `java.util.X()` inline (1.9's `local.properties` loader
failed the sync that way).

**Package and deliver** (only committed files go in):
```
cd /home/claude/GlassCast && git add -A && git commit -m "…" && git archive --format=zip --prefix=GlassCast/ -o /mnt/user-data/outputs/GlassCast.zip HEAD
```
then send it. Append design notes
for each change to `docs/DEVELOPMENT.md`;
release notes go in `docs/release-notes/<version>.md`.

**Hard-won lessons**
- Never check "is this import already there?" by substring —
  `import …data.Episode` matches `import …data.EpisodeSort` (broke the 1.3 build).
  Compare whole lines.
- Verify API names against source before using them. Versions: Compose BOM
  2024.12.01 (foundation 1.7.x), Media3 1.5.1. Compose source:
  raw.githubusercontent.com/androidx/androidx/androidx-main/…; Media3:
  `git clone --depth 1 --branch 1.5.1 --filter=blob:none --sparse https://github.com/androidx/media.git`.
- Anything a shared-element flight tracks must be genuinely **placed**, not
  moved with a layer translation (the cover landed at the corner in 1.2).
  In flight, shared elements draw on the overlay — clip them with
  `clipInOverlayDuringTransition` (`FlightClip`).
- Springs that drive sizes must not overshoot, and readers clamp (1.1 crash:
  width −1).
- The checks parse and match names; they can't resolve library symbols. An
  import of something that isn't a top-level declaration passes them and
  fails in Gradle: `KeyEvent.nativeKeyEvent` is a *member* of Compose's
  `KeyEvent` value class (use `e.nativeKeyEvent`, no import) — importing it
  broke the first 1.4 build. Check each new import against source.
- Anything that must show above a flying shared element (the cover) must be
  in the overlay too: `Modifier.aboveFlyingArtwork(LocalPlayerScope.current)`.
- App icon: glyph = 54% of the *visible* icon (final 1.5 art) (middle 72 of 108dp). No
  night variants of the icon or its layers, ever.
- Use any Material text style freely — `Typography.allFigtree()` puts Figtree
  on all 15 (before, 8 of them silently fell back to the system font).
- SponsorBlock is parked (docs/parked/), not in the build.
- A download failing? Logcat tag `GlassCastDownloads` has DownloadManager's
  reason (HTTP status, or 1005 = too many redirects).
- `border(width = 0.dp)` is **not** "no border": 0dp is `Dp.Hairline`, a
  1-pixel line. Make the border modifier conditional.
- Compose 1.7 has **no D-pad long-press** in `combinedClickable`, and a
  clickable clicks on any key-up. Use `tvFocusable(onLongClick = …)` and put
  `ignoreHeldSelect()` on anything that opens while select is held.
- TV rows of buttons must fit their column in every language — weight the
  flexible one, keep labels single-line. (Four pills beside the show cover
  squeezed Refresh to 8dp; fixed-width transport buttons hid Next.)
- The tab bar animates in layout/draw only — no state written from
  `onGloballyPositioned`, no per-frame recomposition.
- On TV, Compose's default `BringIntoViewSpec` pins focus at 30% of the list —
  use a custom spec (`TvHoldStillSpec`) where that fights layout.
- `SleepTimer` deadlines are on `elapsedRealtime`; use `SleepTimer.remainingMs()`.
- **Nothing slow on the main thread before the first frame.** Cast goes
  through `CastSupport.whenReady` (the async `getSharedInstance(context,
  executor)`); never call the one-argument `getSharedInstance`. Disk work at
  launch goes on the app's IO scope. A debug build logs every main-thread
  disk or network call under `GlassCastStrict`.
- **GlassCastActivity** (MainActivity until 2.0) **must not be recreated** by anything Compose can follow:
  `configChanges` lists orientation, screenSize, smallestScreenSize,
  screenLayout, density, keyboard, keyboardHidden, navigation and uiMode.
  Leave `locale` out (`tr()` relies on the recreation).
- The launch mark plays once per process (`GlassCastApp.splashPlayed`) over
  an app that's already composed. Wait for `opened`, not for the splash, to
  show anything at launch.
- Window and launch-screen colors (`ground_light`, `ground_dark`) must equal
  the in-app grounds (`LightGround0`, `DarkGround0`).
- On TV every list scrolls with `TvBringIntoView` (provided at the root);
  a screen's own spec must use `TvScrollSpring` too.
- Never write the player's queue to `QueueStore` from an empty, freshly
  connected player: that overwrote the saved queue for every version up to
  1.5. Restore first.
- Skip amounts come from `Settings.skipSeconds`, never a literal 30_000:
  app buttons seek by it, and the session's `SkipPlayer` answers everything
  else.
- The stored value `"SHOW_COLOURS"` stays British: it's an old saved theme
  value the migration recognizes.
- **New UI text**: write `tr("…")`, add its row to `docs/translations.txt`
  (ko/de/es/nl), then `python3 -I tools/l10n.py` writes the tables in
  `ui/L10n.kt`. Don't edit the tables by hand. Placeholders `{0}`,`{1}`.
  No code may compare against display text.
- **Two looks (1.9).** Anything new that draws a filled shape, a hard-coded
  white or black, or its own text color must also work in Sea Glass: check
  `LocalSea.current` (null in the Original look) and use the shared pieces
  (`Pill`, `GroupButton`, `AppSwitch`, `SheetPillButton`, `IconBadge`,
  `chromePill`, `rowCard`, `rowTitleStyle()`, `GlassButton`, `seaGlass`)
  rather than a new fill. Glass whose size changes every frame is drawn once
  at full size and scaled with a layer (the player's morph does this), never
  resized, or its ground needs new masks each frame. On the TV: colors from
  `rememberTvColors`, panels in `tvPanelColor`, controls as `TvGlass` (no
  focus ring on glass; the glass is the focus).
- **Three looks (2.0).** The third is called **Glass** in the app, with a
  finish, Frosted or Liquid (`Settings.glassLiquid` → `GlassFinish.liquid`,
  which the `LiquidMaterial` factories read for real glass, and `PaneFinish`
  for the drawn panes; give a new pane the finish through `frostedGlass` or
  `liquidPane`, never a fixed fill). Liquid must look different over a flat
  page too: that's its sheen and rims, not its blur. In code it's Liquid
  (`Look.LIQUID_GLASS` is stored, so never rename it). **The TV has Glass
  too**, drawn rather than lensed (`tv/TvClear.kt`; no readback of the page
  on the Streamer): controls through `TvGlass` with null tokens, focus
  through `FocusIndicationNode`'s glass branch, panels through `tvPanel`.
- **Never read a setting only inside an effect.** `SideEffect { x = state }`
  doesn't re-run when `state` changes (the effect's reads aren't watched);
  that kept the Glass finish from switching for two rounds. App-wide state
  like `GlassFinish` is set from the setting's flow in `GlassCastApp`.
- **A drawn pane that floats over content stays solid**
  (`frostedGlass(finish = false)`): it doesn't blur, so see-through means
  unreadable.
- **Glyphs made of outlines with holes need evenOdd** (`LiquidIcons`, the
  second value of each path). With nonZero a ring fills in solid.
  It asks `LocalLiquid.current` (null in
  the other two). The shared pieces above have Liquid branches too. Real
  lens glass (`Modifier.liquidGlass`) only ever goes on chrome *outside* the
  recorded page (`liquidBackdrop`), with `LocalLiquidBackdrop`; inside a
  page use `liquidPane` (or `Panes.pane` for a button). Glass that records
  itself draws forever. The player records its own picture the same way
  (`playerGlass`). `Modifier.liquidLens` is the exception: it lenses an
  element's own content, so it's safe anywhere.
- **Swipes on rows need a real pull** (`rememberDeliberatePull`, 40% of the
  row at release). Don't go back to a quarter, or let a flick count. The
  tile behind follows the row's offset (`reach`), never the box's
  `progress`, which jumps to 1 once let go.
- **Android Auto shows four tabs** (the root hint
  `EXTRAS_KEY_ROOT_CHILDREN_LIMIT`; past it they're dropped): Home,
  Library, Latest, Downloads. Add a list inside one of them, never a fifth
  tab. Sections in a list are group titles; episode actions are command
  buttons for media items (`AutoLibrary.itemButtons`), each also allowed in
  `onConnect`.
- **The queue is saved by whoever is there**: the app's player connection
  while the app is connected, the service (`keepQueue`) otherwise. Never
  both: each drops what's behind the playhead.
- **Nothing that floats over a sheet's slide draws its top.** A
  `ModalBottomSheet`'s `modifier` sits outside the sheet's offset; draw
  on the sheet's own content (the grabber row), as `GlassSheet` does.
- **Every look's tab bar lays its tabs out with `TabStrip`** (TabBar.kt):
  icons, the selected one widened to its whole name. Don't give a bar
  labels under every icon again; they clipped. The tab glyphs are
  `LiquidIcons` in every look and on the TV.
- **Inter is static files, one per weight** (`res/font/inter_text_*`,
  `inter_display_*`). Don't go back to the variable font with variation
  settings: on HyperOS every weight drew Regular.
- **The Appearance tiles are one design** (`AppearanceTile`) in every look,
  drawn from the live accent and Adaptive tab bar. A look's own branch in a
  tile is what made them jump between looks.
- **The launcher entry is an alias (2.0).** The activity is
  `GlassCastActivity`; the launcher icons are `<activity-alias>`es, the
  original named `.MainActivity` so home screen icons from 1.x keep working.
  Never rename that alias or disable the activity. Intents inside the app
  target the activity class; shortcuts are pinned to the alias that's on
  (`Shortcuts.publish`, `AppIcons.current`).

---

## 4. Project facts

- Package `com.glasscast.app` — Kotlin 2.0.21, AGP 8.7.3, Material 3,
  Media3 1.5.1, Cast SDK 21.5.0, mediarouter 1.7.0, WorkManager 2.9.1,
  Haze 1.3.1, palette-ktx, Figtree. Accent purple (#AF52DE dark / #9B37C3 light).
- **Version 2.0 (versionCode 22), seventh round** packaged October 30
  (DEVELOPMENT.md, "2.0, seventh round"): the finish switch now switches
  live (it was stuck at whatever the app opened with); Liquid retuned close
  to the first round's clearer glass; drawn panes change gently; the
  Downloads menu is solid.
- **Version 2.0 (versionCode 22), sixth round** packaged October 30
  (DEVELOPMENT.md, "2.0, sixth round"): the Liquid finish made visible
  (refraction, dispersion, sheen and brighter rims on real glass; clearer,
  edge-lit drawn panes); Downloads' ⋯ menu and the edit glyphs fixed; Glass
  on the TV (drawn glass controls, glass focus, glass panels and rail, the
  glass rod); TV client values stripped of quotes and spaces.
- **Version 2.0 (versionCode 22), fifth round** packaged October 30
  (DEVELOPMENT.md, "2.0, fifth round"): Android Auto reworked (four tabs
  with icons: Home with Continue Listening and Up Next, Library with
  History, Latest by day, Downloads; progress bars, download badges, episode
  actions, lists that follow the app; the queue kept without the app); the
  look is **Glass**, Frosted or Liquid; the glass player's scrubber is a rod
  of glass; glass on the player's panel buttons; new Downloads edit glyphs;
  Prism without stripes; fixes for the swipe tile, the sheet's top line and
  the bubble's cover; TV sign-in failures show Google's error.
- **Version 2.0 (versionCode 22), fourth round** packaged October 30
  (DEVELOPMENT.md, "2.0, fourth round"): the bubble's two-step path, the
  panel's overhang fixed on the right, 18dp Library corners, Continue
  Listening in threes with the time left, swipes that need a 40% pull, and
  frosted glass on episodes, length capsules, sheets (blur behind),
  switches, search fields, segmented controls, Settings cards and the toast.
- **Version 2.0 (versionCode 22), third round** packaged October 30
  (DEVELOPMENT.md, "2.0, third round"): the third look is called **Frosted
  Glass** in the app (the code keeps `Liquid` names and the stored
  `LIQUID_GLASS`); only the mini player becomes the bubble, beside a bar
  that narrows to icons for it; lens glass on the show page's back and info
  and Library's bar buttons (page backdrops); frosted glass on the show
  page's buttons and the pills; the panel's glass past the screen's sides
  when pulled up; Continue Listening as an Apple Music style shelf of rows.
- **Version 2.0 (versionCode 22), second round** packaged October 30
  (DEVELOPMENT.md, "2.0, second round"): Inter's weights fixed (static
  files), one Appearance tile design, new tab glyphs in every look, Liquid's
  bar with Search back in it and minimizing on scroll into a circle and a
  glass bubble (cover, time as a ring), the player's panel, play and buttons
  in glass, plain lists and toolbar glass on the pages, Apple's sheets,
  Continue listening and History in Library, refresh six shows at a time,
  on opening, and hourly in the background. Typecheck and the checks clean.
  Not built on a device yet.
- **Version 2.0 (versionCode 22), first round** packaged October 20
  (DEVELOPMENT.md, "2.0, first round"): app icons (fifteen, in Settings >
  Appearance > App icon, switched by launcher aliases) and the third look,
  Liquid Glass (Inter, Apple's colors and layout, lens glass on the tab
  capsule, the search button and the mini player accessory). Typecheck and
  the six checks clean; the lens shader compiled and rendered in Skia. Not
  built on a device yet. Draft notes in `docs/release-notes/2.0.md`. The
  design canvas is the "GlassCast App Icons" artifact in Jonathan's
  account; the icon art and concept renderers are in the chat's scratchpad
  only (`sgtool/keyline.py`, `applepages.py`).
- **Version 1.9 (versionCode 21)**, packaged October 19 (DEVELOPMENT.md,
  "1.9: Sea Glass"): a second look for the whole app, Settings > Appearance
  > Original / Sea Glass. Release notes in `docs/release-notes/1.9.md`.
  Typecheck and the six checks clean; first built on the Xiaomi on October
  19, with a second round of fixes from those screenshots. Third round
  (DEVELOPMENT.md, "1.9, third round"): the bubble as a pebble, more color,
  Newsreader for Fraunces, play still on a skip; the car's speed button and
  Continue listening; and the TV: Sign in with Google by QR code (needs the
  TV OAuth client in `local.properties`, steps there), Sea Glass, a
  Continue listening row, bookmarks, and reordering Up Next. The TV client
  ("GlassCast TV", TVs and Limited Input devices) was created October 20 and
  is in `local.properties` in both of the Mac's copies.
  **Released October 20** as v1.9 ("GlassCast for Android 1.9",
  `GlassCast1.9.apk`, sha256 74026ef4…, same signer as 1.8; the repo uses
  immutable releases, so tag and assets can't change afterwards). The tag is
  d633a5a, committed and pushed from the GitHub Desktop clone; the notes
  carry two promo images made from Jonathan's Sea Glass screenshots
  (`docs/images/sea-glass-banner.jpg` and `sea-glass-screens.jpg`, the
  second also in the README). Releases are made in Chrome
  (`releases/new?tag=…&target=main&title=…`), since `gh` has no token here.
  Seen in those screenshots, for a later round: the player panel's title
  in Sea Glass still truncates to "Playing… / Nothing …" beside the capsule.
  Next planned: AI transcripts and ad skipping as a paid
  subscription in 1.10 (per-listener processing because of dynamic ad
  insertion, opt-in, Polar for payments, no bring-your-own-key unlock).
- Before: **1.8 (versionCode 20)**, packaged October 13 (DEVELOPMENT.md,
  "1.8"): the released 1.7 was built from 619148b (sign-in off, before the
  fifth and sixth rounds), so 1.8 is those rounds plus Sign in with Google
  on. Both GitHub crash issues (#1 Pixel 9 Pro on 1.7, #2 Redmi on 1.6) are
  the pull-to-refresh shadow hang the fifth round fixed. Release notes in
  `docs/release-notes/1.8.md`. License GPL-3.0. Brand verification is
  waiting on Google (homepage check needs 24 h after the Search Console
  verification); a scheduled check on October 14 looks again. The GitHub
  repo was a mix of old versions and Finder's " 2"/" 3" copies; on October 13
  it was replaced with exactly this source (minus `site/`) in one commit,
  13a0e75, pushed from Jonathan's GitHub Desktop clone at
  `~/Documents/GitHub/GlassCast-for-Android`. His Android Studio copy at
  `~/Downloads/GlassCast` was updated to the same source.
- Before: **1.7 (versionCode 19)**, packaged October 12 (DEVELOPMENT.md,
  "1.7"): GlassBook's menus (sheets, confirmation sheets, Settings as the
  sixth tab), bookmarks with memos, the title opening its show, headset
  skip, car skip buttons, per-show auto-download on Wi-Fi, a home-screen
  widget, launcher shortcuts; then in a second round the chrome following
  the page (crossfading), Buy Me a Coffee, images in update notes, and
  a backup file; then in a third round the tab bar dark again (adaptive as
  an option), the tab icon jump fixed, a custom accent color, the phone's
  landscape player, and **Sign in with Google** (the library syncs through
  the account's hidden app folder; no server). **Sign-in is on and
  published** (DEVELOPMENT.md "1.7, seventh round"): Cloud project
  `glasscast-511413` in production (no user cap: `drive.appdata` is
  non-sensitive), home page and privacy policy at anixitea.github.io (repo
  `anixitea/anixitea.github.io`, source in `site/`), ownership verified in
  Search Console. Brand verification still to pass (Google wants 24 h after
  the ownership check); until then the consent screen lacks the app name.
  Code 8 was the missing setup. Only the release key has an Android client.
  Then an ad campaign (English and Korean) from Jonathan's own screenshots,
  used as sent at his choice: a Design canvas "GlassCast Campaign" (social
  posts, store screenshots, link previews, X headers; 24 artboards), the
  rendered images, and the GlassCast web page redesigned in the same look
  with a Korean version (`site/glasscast/ko/`). Then a
  fifth round from Jonathan's pad reports and screenshots (DEVELOPMENT.md,
  "1.7, fifth round"): the not-responding hang on pull-to-refresh (a shadow
  cast from the cookie's concave outline, retriangulated every frame; now
  a circle's), the sideways player's color strip (the backdrop's mirror
  stopped short of the right edge), and the open panel on a sideways phone
  (rises about 60dp higher; the timer tab scrolls). Sixth round: the tab
  bar's shake (spring, label cuts, fixed widths) and Settings' blank off
  switches (DEVELOPMENT.md, "1.7, sixth round"). Before: **1.6.1 (versionCode 18)**. The
  first field report (a Redmi Note 11, Android 13) was a hang behind the
  RenderThread; 1.6.1 makes reports carry that thread and the screen that
  was up (DEVELOPMENT.md, "1.6.1"). Pixel users report crashes: get their
  reports. Before: **1.6 (versionCode 17)**.
  1.6 so far: the pad's startup suspects and the GlassPlay ports (see
  DEVELOPMENT.md, "1.6"). The pad stuck on the launcher icon (before the
  first frame) with Play services working; the synchronous Cast start is the
  likely cause, not yet confirmed. Jonathan won't use adb, so evidence comes
  from Settings > About > Crash report.
- In-app updater reads the GitHub latest release; version names compare
  numerically; updates need the same signing key.
- One APK: phone (`GlassCastActivity`, launched through the icon aliases)
  and TV (`TvActivity`, LEANBACK_LAUNCHER).

---

## 5. Architecture map

**GlassCastApp** — owns the stores; installs `CrashLog` (and StrictMode in
debug) in `attachBaseContext`; launch disk work on its IO scope;
`splashPlayed`.

**data/**
- `CrashLog` — last crash (`files/crash/last.txt`) plus unusual process exits
  from Android's exit record, with not-responding stacks (`exits.txt`);
  `report` flow feeds Settings > About > Crash report; cleared once sent.
- `FeedStore` — per-show JSON files, 900ms coalesced writes; hooks
  `onSubscriptionChanged`, `onMarkedPlayed`, `onRefreshed`.
- `RssParser` — ranks transcripts SRT/VTT over Podcasting 2.0 JSON.
  `EpisodeExtras` parses chapters and all three transcript formats.
- `Settings` — theme, dynamic color, skip seconds, headphone skip, skip
  silence, boost voices, shake, notifications, `discoverHidden`,
  `episodeOrder` and `autoDownload` (per show).
- `Bookmarks` — `BookmarkStore`, by episode guid, `files/bookmarks.json`.
- `AutoDownload` — after each refresh, on unmetered networks only.
- `Backup` (`BackupManager`) — the backup document; save/read files;
  restore (merges). `GoogleAccount` — (off for now, `GOOGLE_SIGN_IN`) Sign in with Google through
  `AuthorizationClient` (drive.appdata); one library document per account,
  merged both ways (follow/unfollow times, furthest progress, bookmarks with
  deletions); `SyncWorker` on open/close, daily from RefreshWorker.
- `Downloads` — Android DownloadManager into the app folder
  (`Android/data/…/files/Podcasts`), hashed file names, polled only while
  active, verified on launch (off the main thread).
- `GPodderSync` — gpodder.net-style and Nextcloud servers; subscriptions
  (first sync merges) + play actions (on pause / mark played); outbox;
  gpodder.net devices joined into one sync group at sign-in; password
  sealed with a Keystore AES key. **Untested against a real server.**
- `TvSignIn` (1.9) — the TV's Google sign-in by Google's device flow (QR
  code, a code typed at google.com/device); keeps the refresh token in the
  account's prefs; `GoogleAccount.withToken` uses it on a TV. Client ID and
  secret come from `local.properties` through manifest meta-data.
- `QueueStore`, `Opml`, `ImageStore`, iTunes directory search.
  `FeedStore.inProgress` is Continue listening for the car and the TV.

**player/**
- `CastSupport` — starts Cast off the main thread, once per process; calls
  back where Cast can run, never elsewhere; output switcher params.
- `PlaybackService` — a `MediaLibraryService` (1.4); ExoPlayer ↔ CastPlayer hand-over (downloaded file
  URIs swapped back to the stream for Cast); `speechSilenceSkipper`
  (0.2s / 30% kept, max 1s / threshold 768); "Boost voices" is
  LoudnessEnhancer +7dB (loudness, not voice isolation).
- `PlayerConnection` — `mediaItemFor` plays the downloaded file when present,
  carries `REMOTE_URL`; `onPaused` hook feeds gPodder. Reads the saved queue
  once at start-up and shows it at once; restores it into an empty player
  before ever syncing (syncing first wiped it, until 1.6); replaces a
  controller whose service died.
- `SkipPlayer` — the session's view of the active player; back and forward
  follow `Settings.skipSeconds` (15 or 30).
- `VoiceLevel` (wave reacts to audio), `SleepTimer`, `ShakeDetector`.
- `VoiceBoost` — LoudnessEnhancer +7dB (the 1.4 DynamicsProcessing chain was
  reverted).
- `AutoLibrary` — Android Auto browse tree (four tabs: Home = Continue
  Listening + Up Next, Library = History + shows, Latest by day,
  Downloads), episode actions (Up Next, played), lists that follow the app
  while a car is connected, search, voice play, resumption; also the
  session callback for headset buttons and the car's skip and speed
  buttons (`CarButtons`, on only while a car is connected);
  `ArtworkProvider` — content:// covers for Auto; `EpisodeItems` — the one
  episode → MediaItem builder.

**background/** `RefreshWorker` (3h; notifications, auto-download, gPodder
sync), `NewEpisodeNotifier`. **update/** `AppUpdater`. **widget/**
`NowPlayingWidget`. `Shortcuts` (launcher shortcuts, dynamic).

**ui/**
- `GlassCastRoot` — tabs Library, Latest, Downloads, Discover, Search,
  Settings (`SettingsScreen`, grouped badge cards); handles shortcuts;
  card/bubble state persists across tabs; `LocalToast` provided here.
- `MiniPlayer` — one element morphing card ⇄ bubble; ring uses the card's
  two colors (button = played, surface = unplayed). In dark mode the *card*
  swaps them (pale card, dark button); the ring never does.
- `SharedElements` — `MiniArtKey` flight, `FlightClip`, `MiniArtShape`,
  `bubbleFlightPath` (superellipse → scalloped; lands exactly on the bubble).
- `PlayerScreen` (title scrolls once; download button beside Cast),
  `PlayerPanel`.
- `TabBar` — custom Layout + two Animatables.
- `Artwork` — palette; accent by hue family (≥4% of the cover); `card`
  color set ~0.16 lightness from the cover's true average.
- `FeedScreen` (per-show sort pill, Downloaded filter),
  `LatestScreen` / `DownloadsScreen` (per-show color wash, pull-to-refresh),
  `DiscoverScreen` (refresh rounds, `DiscoverCache`, Not interested dialog,
  pull-to-refresh), `SubscriptionsScreen` (Cider-style pill top bar).
- `GlassSheet` (every sheet; `ConfirmSheet` for destructive actions),
  `Sheets`, `SyncSheet`, `SortIcons`, `L10n`.
- `seaglass/` (1.9) — the Sea Glass look: `SeaGlass.kt` (tokens and
  `LocalSea`, recipes, pigments, `seaPigment`), `GlassBody` (AGSL shader and
  the drawn fallback), `GlassContour` (the tumbled outline), `GlassGround`
  (cached shadow and pool masks), `GlassPiece` (`Modifier.seaGlass`,
  `GlassButton`, `GlassSurface`, `GlassPainter`, `coverPool`), `SeaControls`
  (`SeaSwitch`, `rowTitleStyle`, `seaCoverPool`, `chromePill`, `rowCard`).
  `theme/Theme.kt` has `seaScheme`; `theme/Type.kt` has `SeaType` (Newsreader).

**tv/**
- `TvRoot` — rail; Discover/Search open a preview (Follow adds); episode
  menu; unfollow confirmation; toasts; the sign-in overlay.
- `TvScreens` — Library (Continue listening row on top), Discover, Latest,
  Show (own cover backdrop and palette, `TvHoldStillSpec`), Search, Settings
  (Appearance, `TvAccountSettings`, `TvSyncSettings`).
- `TvPlayer` — rebuilt 1.3: connected transport group, wave scrubber,
  side panels (Up Next with reordering / Info / Bookmarks / Speed & sound /
  Timer).
- `TvAccount` (1.9) — the Google account group and `TvSignInOverlay` (QR
  code, the code to type, polling). `QrCode` — a small QR encoder.
- `TvSea` (1.9) — Sea Glass on the TV: `rememberTvColors`, `tvPanelColor`,
  `TvGlass` (a control as glass; focus lights or brightens it, no ring).
- `TvOverlays` — toast, episode menu, confirm dialog, text field.
- `TvFocus`, `TvTheme`, `TvGlass`.

**Mac** (`GlassCast-macOS.zip`) — SwiftUI, macOS 26, Liquid Glass.
Handoff/sync protocol designed (`handoff-sync-protocol.md`), not implemented.

---

## 6. Needs testing on device

**2.0, seventh round (not built yet):**
- Library in Glass, scrolled so covers sit under the bar: switch Frosted
  and Liquid in Settings and come back. Liquid's bar and mini player show
  the covers' colors more clearly, with brighter edges; Frosted is the
  heavier frost. It changes at once, without reopening the app, on the
  phone and the TV.
- Downloads ⋯ menu: solid and readable over the list, in both finishes.

**2.0, sixth round (not built yet):**
- Settings > Glass, Frosted then Liquid: over Library's covers the bar,
  bubble and buttons turn clearer, with color at their edges and light on
  them; Settings sections and cards turn darker and clearer with brighter
  rims. The difference should show on the Settings page too.
- Downloads: the ⋯ button opens Select and Select all; selecting shows the
  Select all, Delete and Cancel glyphs (not dots).
- **TV**: Settings > Glass (and Frosted / Liquid beside it). Buttons are
  clear glass, lit in the cover's color when focused or chosen; covers and
  cards rise and catch the light; the rail, menus and side panel are glass;
  the player's buttons are separate pieces, play slabbed; the scrubber is a
  glass rod that swells while focused. Moving fast through grids stays
  smooth on the Streamer.
- **TV sign-in** again: if it still fails, the line under the message.

**2.0, fifth round (not built yet):**
- **Android Auto** (Desktop Head Unit, then a car): four tabs with icons,
  Home first; Home shows Continue Listening and Up Next under headings,
  without the episode that's loaded; Library starts with a History row,
  then the grid; Latest and History have day headings. Rows part way
  through have a progress bar; downloaded ones a badge. A row's menu (long
  press, or ⋮ on newer Auto) offers Add to / Remove from Up Next and Mark
  as played / unplayed, shows a message, and the row's menu flips after.
  With Home open, let an episode finish: Up Next moves on by itself. Start
  something from the car without opening the app, then open the app after:
  it's on what the car played.
- **Glass**: Settings says Glass, with Frosted / Liquid under it (only while
  Glass is picked); Liquid makes the bars, bubble, buttons, the player's
  glass and sheets clearer. The icon picker says Glass.
- The player in Glass: no wave; the scrubber is a glass rod that swells
  while dragged. The panel's buttons sit in a glass pill when pulled up;
  the open tab has a drop of glass; pressing one swells it.
- Swiping a row and letting go: the tile shrinks back with the row and is
  gone with it, in every look.
- Opening History (any sheet) in Glass: no white line at the top of the
  screen.
- Closing the full player into the bubble: the cover lands without
  changing size.
- Downloads' Select, Select all, Delete and Cancel glyphs; Prism without
  stripes.
- **TV sign-in**: if it fails, the small line under the message is Google's
  error; send it. If the TV has no Sign in with Google at all, the build is
  missing the TV client (the build log warns). Values in local.properties
  go without quotes: `glasscast.tvClientId=123-abc.apps.googleusercontent.com`.

**2.0, fourth round (not built yet):**
- The mini player into the bubble: right first, then down, as in the
  Original look; back out the same way.
- The player's panel pulled up: no gap at either side, no white lines.
- Swiping: scrolling a list with some drift no longer queues or marks
  played; a deliberate pull still does, on the show page, Latest and
  Downloads.
- Sheets in Frosted Glass: the app blurs behind them (if the phone allows
  cross-window blur; battery saver may turn it off) and the sheet is
  translucent; otherwise solid.
- Switches: the knob swells while held.

**2.0, third round (not built yet):**
- Settings and the icon picker say **Frosted Glass**.
- Scrolling with something playing: the mini player becomes the bubble at
  the bar's right end and the bar narrows to icons; nothing changes with
  nothing playing. The cover in the mini player sits clear of the end.
- The show page's back and info buttons bend the cover behind them; Add,
  Play and Refresh are separate frosted capsules; Library's top buttons are
  glass over the covers.
- Pulling up the player's panel: no white lines down the sides.
- Continue Listening pages sideways in columns of four rows; the menu button
  opens the episode's actions.

**2.0, second round (not built yet):**
- **Bold titles** in Liquid Glass (Inter's weights): "Settings", "Podcasts"
  and the player's title should be heavy now.
- **Appearance tiles**: the same size and style whichever look is on; they
  follow Dynamic and Custom color, light and dark, and in the Original look
  Adaptive tab bar (pale bar on the dark screens).
- **Liquid tab bar**: six tabs, the selected one with its name; scroll a
  page down and it draws in to a circle with the mini player as a bubble at
  the other end; scroll up or tap the circle to bring it back. The bubble's
  ring grows as the episode plays; no dot on it (nor on Sea Glass's).
- **Legibility** of the bar over bright covers, light and dark.
- **Player in Liquid Glass**: the panel is a glass capsule at rest and a
  glass sheet pulled up; play is a glass disc that changes shape; the skips
  show glass while held; download and cast are glass discs.
- **Continue listening** in Library, and **History** (the clock button);
  history fills as you listen.
- **Refresh**: pull to refresh is quicker with many shows; opening the app
  after a quarter hour away brings new episodes without a pull.

**2.0, first round (not built yet):**
- **Updating from 1.9** keeps the icon on the home screen working (it points
  at `.MainActivity`, now the original's alias).
- **App icon**: Settings > Appearance > App icon, pick a few in each family;
  the launcher swaps the icon without the app closing; long-press shortcuts
  still there afterwards. On HyperOS and ColorOS, note whether the home
  screen icon stays or moves to the drawer.
- **Liquid Glass**: the tab capsule, search button and accessory bend the
  page behind them while scrolling (covers in the Library grid show it best),
  light lines on the upper left and lower right edges, the droplet slides
  between tabs. Light and dark. If the bars come out as plain frosted panes,
  the lens didn't build on that GPU.
- Inter everywhere in Liquid Glass, Korean falling back to the system face.
- The cover flies between the accessory and the player.
- Settings in Liquid Glass: gray page, white cards, Apple's switches,
  segmented controls and the round checks under the looks and brightness.

**1.9, third round (not built yet):**
- **Skip** forward and back while playing: play doesn't turn over or flicker.
- **Bubble** in Sea Glass: the cover fills a pebble, the line runs around it
  and grows as the episode plays; the pebble slowly changes shape while
  playing and holds still paused; the cover flies in and out of it cleanly.
- **Show pages and the player** carry the cover's colors; the panel's tabs
  press as pebbles with no rectangle.
- **Newsreader** titles, in English and with Korean falling back cleanly.
- **Car**: the speed button steps through the speeds and the phone's player
  follows; Continue listening is first in browse when something is started.
- **TV sign-in** (after adding the TV client to `local.properties`):
  Settings > Sign in with Google shows a QR code and a code; scan, enter the
  code, approve; the TV shows the account and syncs. Sign out works. A code
  left 30 minutes expires and offers a new one.
- **TV Sea Glass**: Settings > Appearance > Sea Glass; the tide wash drifts;
  buttons light in the cover's color on focus; in option rows the chosen
  one stays lit and the focused one brightens; moving focus fast through
  the player stays smooth on the Streamer.
- **TV Continue listening** row; **Bookmarks** tab (add, jump, remove);
  **Up Next** reordering (handle or hold select, up/down, select or Back).

**1.9 (Sea Glass):**
- **Confirmed on the Xiaomi (October 19):** the AGSL shader compiles; the
  glass is frosted in light and dark (second-round fixes in DEVELOPMENT.md).
  On an Android 12 or older phone the smooth drawn fallback is expected.
- **Player**: the cover flies in and lands on rounded corners; play changes
  shape on each press; dragging the panel up shrinks the glass buttons into
  the header without stutter; the bead follows the wave.
- **Scrolling** the Library and Latest with something playing: smooth on the
  release build (each cover's pool and each piece of glass is cached).
- **Mini player** collapsing to the bubble and back: the card's shadow fades
  first, the glass ring fades in around the cover.
- **Dark**: the tide ground, glass lit from its edges, status bar icons light.
- **Switching looks** back and forth: nothing left over in either.

**1.8:**
- **Pull to refresh** on the Library, many times, on the Pixel or the pad:
  no freeze, no "isn't responding".
- **Sign in with Google** from Settings on the release build: the card shows
  the account and "Synced". A second phone signed in gets the same library.
- **Sideways phone**: the player's backdrop reaches the right edge; the panel
  opens higher; the sleep timer tab scrolls.
- **Tab bar**: switching tabs doesn't wobble; Downloads fits its label.
- **Settings switches**: off ones show a gray thumb and rim.

**1.7:**
- **Chrome**: light mode, dark mini player and bar; dark mode, both pale;
  switch the theme (or open a show) and they fade, not snap. Labels and
  icons stay readable on both.
- **Buy Me a Coffee** opens the page.
- **Update notes with an image**: publish a test release whose notes have a
  dragged-in screenshot; the update sheet shows the picture, not code.
- **Backup file**: save one, restore it on another phone (or after clearing
  the app's data): shows, played marks, places, bookmarks, queue, settings.
- **Sign in with Google** (once `GOOGLE_SIGN_IN` is on): sign in; the card shows
  photo, name, email, "Synced …". On a second phone sign in: the shows,
  progress and bookmarks arrive. Unfollow a show and delete a bookmark on
  one phone, sync both: gone on the other too. Sign out keeps the library.
- **Tab bar**: dark in both themes; Adaptive tab bar on, it goes pale with
  the mini player in dark mode.
- **Tab icon**: collapse and expand the mini player repeatedly (scroll):
  the selected icon stays inside its pill throughout.
- **Custom color**: pick with the sliders; the accent changes app-wide;
  Dynamic color turns it off and the other way round.
- **Phone sideways, full player**: cover on the left, title clear of the
  status bar, everything visible; pull up the panel; drag down to close.
- **Sheets** on the phone with the camera cutout: tall ones (Episode info,
  OPML, gPodder) stop below it; nothing opens half way.
- **Confirmation sheets**: Not interested (Discover), Unfollow, deleting one
  and several downloads.
- **Settings tab**: every row works; the theme tiles switch the app as you
  tap; Crash report only when there is one; the tab bar's six labels fit in
  Korean, German, Spanish and Dutch.
- **Bookmarks**: add while playing, write a memo, tap the time to jump,
  swipe to delete; dots on the scrubber; still there after closing the app.
- **The title in the full player** opens the show.
- **Headphone buttons** on Skip: double and triple tap on earbuds skip
  forward and back by the skip setting; on Next they change episode; the
  notification's own buttons (Android 12 and older) still do what they show.
- **Android Auto** (Desktop Head Unit and a car): back and forward buttons
  appear, skip by the setting, and follow a change to it; next and previous
  keep their places; after disconnecting, the phone's notification is back
  to normal (on HyperOS especially).
- **Auto-download**: turn it on for a show, then on Wi-Fi refresh after it
  publishes (or wait for the background check): the episode downloads; on
  mobile data nothing starts until Wi-Fi; a deleted one doesn't come back.
- **Widget**: add it, play/pause/skip from it with the app closed and open,
  the cover color and icons follow the episode and the skip setting; tap
  opens the player. With nothing ever played it shows just the name.
- **Shortcuts**: long-press the icon; Resume plays and opens the player from
  a cold start; Latest, Search, Downloads open their tabs. Labels in each
  language.

**1.6.1:** a forced hang in debug (a temporary `Thread.sleep(10_000)` on a
button, then tap the screen until Android offers to close the app). Reopen:
Settings > About > Crash report should show "not responding", a "showing:"
line naming the screen, and the main, RenderThread and hwui threads.

**1.6:** judge smoothness on `fast` or release, never debug.
- **The pad, cold start ten times in a row**: force stop (or swipe away)
  between launches. Note which screen each one shows if it sticks: a plain
  field (Android waiting for the first frame) or the purple mark (the app).
  After any crash or hang, Settings > About should offer Crash report; send
  it. On a debug build, `adb logcat -s GlassCastStrict` lists main-thread
  disk and network calls during start-up.
- **Split screen, a floating window, and a display-size change** on the pad:
  no replay of the mark, nothing reloads, layouts follow.
- **Casting to the Streamer** from the phone and the pad: devices appear in
  the Play on sheet, casting starts, "This phone" brings it back; the
  system output switcher still lists the Streamer.
- **A forced crash in debug** (e.g. a temporary `error("test")` on a button):
  reopen, and Settings > About shows Crash report; Send opens the share
  sheet with the trace, and the row is gone afterwards.
- **D-pad held on the TV**: focus moves without the old bounce, lists keep
  their speed and margins (no sliced first cards), the backdrop holds still
  while keys are down and drifts again a moment after.
- Launch on Android 12+: a plain ground, then the mark, no icon jump; in dark
  mode no darker flash before the mark.
- **Reopening remembers what was playing**: play something, pause, swipe
  GlassCast away, reopen. The mini player is back with the same episode and
  progress, Up Next is intact, and play resumes from the same spot without
  anything playing by itself. Also after a reboot, and with Bluetooth
  headphones' play button while the app is closed.
- **Skip buttons** set to 15 and to 30 seconds: the phone and TV players'
  icons and jumps follow; so do the notification's and Android Auto's,
  where they show skip buttons.
- Notification-permission prompt (fresh install, Android 13+) and the update
  check still come up once the mark fades.

**1.4 (second round):** Android Auto on the Desktop Head Unit and in a car
(browse, artwork, search, "play X on GlassCast", resume, Up Next kept);
Boost voices on speech with a loud host / quiet guest; the palette on the six
test covers plus a few more; dark-mode dynamic color (gold, not lemon).

**1.4 (TV round):** show pages' backdrop under the rail; show header (no
sliver, no gap; check = Following; sort toast); player transport (all five fit,
only play/pause morphs, fill-only focus, no white outlines); panel bar icons;
Discover hold menu (hold opens it, releasing doesn't pick Follow, focus lands
on the next card); the moving backdrop (smoothness on the Streamer, stops
under the player); gPodder fields (no keyboard while scrolling, select opens
it, Done returns). **Palette** on phone and TV: Broski (gold, not chartreuse),
Deutschland3000 (purple on neutral), and a few covers that looked right in
1.3 — neutrals now win the ground more often.

**1.3:**

- The cover flying into the bubble (should land already scalloped).
- Show-page episode cards against their blur (e.g., The Headlines).
- The accent on the Brittany Broski cover (teal, not chartreuse).
- Latest / Downloads color washes; pull-to-refresh on Latest and Discover.
- Downloads: download, airplane mode, play; the player's download button.
- Skip silence strength (midpoint tuning).
- **Korean** UI (Jonathan reads Korean — ask for corrections); de/es/nl are
  Claude's translations.
- gPodder against a real server (phone and TV).
- TV: player panels, sleep timer, hold-select menu, show page scrolling,
  preview → Follow, Unfollow.

---

## 7. Roadmap and promises

- **1.10: AI transcripts and ad skipping** as a paid subscription (the plan
  Jonathan shared on October 18): process per listener (dynamic ad insertion
  means everyone hears different ads), opt-in with a plain privacy note,
  payments through Polar, and no bring-your-own-API-key unlock. The dormant
  ad-skip engine (AdFinder, AdSkipper, `skipAds` pinned false) is the place
  it plugs in.

- **1.6:** find the pad's cause from its first report, and fix that. Then,
  only with evidence: the image cache on large screens (if OutOfMemoryError),
  and 144 Hz choppiness (measure with `dumpsys gfxinfo … framestats` first).
- **TV level bars** on the playing episode's row (GlassPlay's `TvLevelBars`,
  driven by `VoiceLevel`): asked, not built.

- **Export downloads — dropped** (Jonathan: it feels unfair to podcasters).
  It had been promised on Reddit to u/SilverBladeCG; a reply saying so is
  still owed.
- **Mac app — on hold.**
- **Done in 1.4:** Android Auto (tested on the Desktop Head Unit, Oct 1 —
  browse, artwork, resume, controls all good); show-page Play button in
  the cover's background color; downloads that follow redirects themselves.
- **Skip ads: parked** (toggle removed, setting pinned off; engine dormant in
  the code; SponsorBlock in docs/parked/).
- **Boost voices**: the DynamicsProcessing chain was tried and reverted (it
  sounded worse); it's the 1.3 LoudnessEnhancer again. Don't retry without
  listening tests.
- **SponsorBlock** was built and parked (docs/parked/): needs research on
  matching RSS audio to YouTube videos before it returns.
- **Ad skipping phase 2** (later): on-device transcription for shows that
  publish no transcript.
- **1.5 plan:** tablet layouts (landscape player and two-pane show page done), TV focus fixes (done), Downloads
  redesign (done), a home-screen widget (done in 1.7), per-show settings
  (auto-download, done in 1.7), TV info parity.
- **Phone edits saved for 1.4** (build later, together):
  - Discover long-press: offer **Follow** and **Not interested** (as the TV
    menu does) instead of only the "Not interested?" confirmation.
  - The palette fix is in shared code, so the phone already has it — verify.

1. ~~Export downloads~~ (dropped, above).
2. **Android Auto** — recommended next. MediaLibraryService browse tree
   (Up Next, Latest, Downloads, Shows → episodes), voice search, a
   content:// artwork provider, `automotive_app_desc`; test with the Desktop
   Head Unit. README note: sideloaded apps need Android Auto developer
   settings → Unknown sources.
3. **Boost voices, done properly** (mentioned on Reddit): DynamicsProcessing
   — low cut, presence lift, compression.
4. **Sponsor skipping, phase 1**: from transcripts and chapters, a
   "Skip sponsor" button, opt-in auto-skip. Phase 2 (on-device
   transcription) later.
5. Wi-Fi-only downloads option (offered).
6. Mac (on hold): implement the handoff/sync protocol; the Mac in Play On; gPodder.
7. Deferred: Room database, auto-delete played downloads,
   translating Discover genre names and a few concatenated TV empty-state
   strings.

---

## 8. Reddit

Posted in r/Xiaomi; planned: r/androidapps, r/googletv, r/podcasts and
r/androiddev (weekly threads). Answered so far: downloads keep the
publisher's format; Export was promised but has since been dropped (reply owed); Boost voices is a loudness boost with
compression, not voice isolation. Asked for: gPodder (built in 1.3), Android
Auto, sponsor skipping, Spotify (no — public RSS only).

## 9. Listings and launch posts

The "GlassCast Launch List" artifact (claude.ai/artifact/PjQvi34x8ackcdEBLiuGVH)
has every venue with its text; "GlassCast on Reddit" has the Reddit posts.

- Obtainium badge: on both site pages (`site/glasscast/obtainium.png`, the
  `.store-badge` link under the install buttons) and in the README
  (`docs/images/obtainium.png`). The link is Obtainium's redirect page with
  `{"id":"com.glasscast.app","url":"https://github.com/anixitea/GlassCast-for-Android","author":"anixitea","name":"GlassCast"}`.
- Obtainium's own app list (ImranR98/apps.obtainium.imranr.dev) needs 35
  stars and a repo at least 4 months old. The repo was created 2026-09-29, so
  late January 2027 at the earliest.
- Podcast Index apps list: fork `anixitea/web-ui` exists. The entry (end of
  `server/data/apps.json`, Chapters and Transcript linked to RssParser.kt at
  v1.8) and a 288px icon (`server/assets/glasscast.png`) are in the Mac's
  Downloads/GlassCastCampaign/listings/podcastindex. The upload to the fork
  was blocked here, so the PR isn't open yet.
- Downloader code (go.aftvnews.com) needs a captcha, so Jonathan makes it.
  The link is the v1.8 APK. Once he has the code, add "Downloader code
  NNNNNNN" to the site's install section (both languages) and the
  r/AndroidTV post.
- Skipped: IzzyOnDroid (rejects AI-written apps), F-Droid (Play services),
  Podnews articles (need an own domain).
