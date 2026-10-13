# GlassCast — handoff to a new chat

Written September 30, 2026, at the end of the chat that built 1.1 → 1.3;
updated through 1.4 and 1.5, on October 9 for 1.6 (the OPPO Pad mini
startup work and the GlassPlay ports), on October 12 for 1.7, and on
October 13 for 1.8.
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
- **MainActivity must not be recreated** by anything Compose can follow:
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

---

## 4. Project facts

- Package `com.glasscast.app` — Kotlin 2.0.21, AGP 8.7.3, Material 3,
  Media3 1.5.1, Cast SDK 21.5.0, mediarouter 1.7.0, WorkManager 2.9.1,
  Haze 1.3.1, palette-ktx, Figtree. Accent purple (#AF52DE dark / #9B37C3 light).
- **Version 1.8 (versionCode 20)**, packaged October 13 (DEVELOPMENT.md,
  "1.8"): the released 1.7 was built from 619148b (sign-in off, before the
  fifth and sixth rounds), so 1.8 is those rounds plus Sign in with Google
  on. Both GitHub crash issues (#1 Pixel 9 Pro on 1.7, #2 Redmi on 1.6) are
  the pull-to-refresh shadow hang the fifth round fixed. Release notes in
  `docs/release-notes/1.8.md`. License GPL-3.0. Brand verification is
  waiting on Google (homepage check needs 24 h after the Search Console
  verification); a scheduled check on October 14 looks again.
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
- One APK: phone (`MainActivity`) and TV (`TvActivity`, LEANBACK_LAUNCHER).

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
- `QueueStore`, `Opml`, `ImageStore`, iTunes directory search.

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
- `AutoLibrary` — Android Auto browse tree, search, voice play, resumption;
  also the session callback for headset buttons and the car's skip buttons
  (`CarButtons`, on only while a car is connected);
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

**tv/**
- `TvRoot` — rail; Discover/Search open a preview (Follow adds); episode
  menu; unfollow confirmation; toasts.
- `TvScreens` — Library, Discover, Latest, Show (own cover backdrop and
  palette, `TvHoldStillSpec`), Search, Settings (incl. `TvSyncSettings`).
- `TvPlayer` — rebuilt 1.3: connected transport group, wave scrubber,
  side panels (Up Next / Info / Speed & sound / Timer).
- `TvOverlays` — toast, episode menu, confirm dialog, text field.
- `TvFocus`, `TvTheme`, `TvGlass`.

**Mac** (`GlassCast-macOS.zip`) — SwiftUI, macOS 26, Liquid Glass.
Handoff/sync protocol designed (`handoff-sync-protocol.md`), not implemented.

---

## 6. Needs testing on device

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
