# GlassCast — handoff to a new chat

Written September 30, 2026, at the end of the chat that built 1.1 → 1.3;
updated the same day after the first 1.4 (Google TV) round.
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
  committed). Tests on a **Xiaomi 17 Pro Max (HyperOS)** and a **Google TV
  Streamer**. Sends Gradle logs, crash screenshots and screen-recording GIFs.
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

**Six static checks, all must pass before packaging** (from `/home/claude/GlassCast`):

```
sh tools/syntaxcheck.sh        # kotlinc parse; downloads kotlinc to /tmp/kotlinc if missing
python3 tools/importcheck.py
python3 tools/argcheck.py      # named arguments vs. signatures
python3 tools/identcheck.py    # Material icons exist and are imported
python3 tools/extcheck.py      # Compose modifier extensions imported
python3 tools/owncheck.py      # app's own + library classes imported (added after the 1.3 break)
```
syntaxcheck.sh does **not** download kotlinc; if it says "kotlinc not found":
`cd /tmp && curl -sSL -o k.zip https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip && unzip -q -o k.zip`

**Package and deliver:**
```
cd /home/claude && rm -f GlassCast.zip && zip -qr GlassCast.zip GlassCast -x "*/build/*" "*/.gradle/*" "*/.idea/*" && cp GlassCast.zip /mnt/user-data/outputs/
```
then `present_files`. Append design notes for each change to `docs/DEVELOPMENT.md`.

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
- The stored value `"SHOW_COLOURS"` stays British: it's an old saved theme
  value the migration recognizes.
- **New UI text**: write `tr("…")` and add the row to `docs/translations.txt`
  and all four tables in `ui/L10n.kt` (ko/de/es/nl). Placeholders `{0}`,`{1}`.
  No code may compare against display text.

---

## 4. Project facts

- Package `com.glasscast.app` — Kotlin 2.0.21, AGP 8.7.3, Material 3,
  Media3 1.5.1, Cast SDK 21.5.0, mediarouter 1.7.0, WorkManager 2.9.1,
  Haze 1.3.1, palette-ktx, Figtree. Accent purple (#AF52DE dark / #9B37C3 light).
- **Version 1.5 (versionCode 16)**, in progress. 1.4 is released.
  Public GitHub releases: v1.0.0, v1.1. 1.2 was internal. The 1.3 compile
  error (missing `Episode` import in `TvRoot.kt`) is fixed. 1.4 so far: the
  Google TV round and the shared palette fix (see DEVELOPMENT.md, "1.4").
- In-app updater reads the GitHub latest release; version names compare
  numerically; updates need the same signing key.
- One APK: phone (`MainActivity`) and TV (`TvActivity`, LEANBACK_LAUNCHER).

---

## 5. Architecture map

**data/**
- `FeedStore` — per-show JSON files, 900ms coalesced writes; hooks
  `onSubscriptionChanged`, `onMarkedPlayed`.
- `RssParser` — ranks transcripts SRT/VTT over Podcasting 2.0 JSON.
  `EpisodeExtras` parses chapters and all three transcript formats.
- `Settings` — theme, dynamic color, skip silence, boost voices, shake,
  notifications, `discoverHidden`, `episodeOrder` (per show).
- `Downloads` — Android DownloadManager into the app folder
  (`Android/data/…/files/Podcasts`), hashed file names, polled only while
  active, verified on launch.
- `GPodderSync` — gpodder.net-style and Nextcloud servers; subscriptions
  (first sync merges) + play actions (on pause / mark played); outbox;
  gpodder.net devices joined into one sync group at sign-in; password
  sealed with a Keystore AES key. **Untested against a real server.**
- `QueueStore`, `Opml`, `ImageStore`, iTunes directory search.

**player/**
- `PlaybackService` — a `MediaLibraryService` (1.4); ExoPlayer ↔ CastPlayer hand-over (downloaded file
  URIs swapped back to the stream for Cast); `speechSilenceSkipper`
  (0.2s / 30% kept, max 1s / threshold 768); "Boost voices" is
  LoudnessEnhancer +7dB (loudness, not voice isolation).
- `PlayerConnection` — `mediaItemFor` plays the downloaded file when present,
  carries `REMOTE_URL`; `onPaused` hook feeds gPodder.
- `VoiceLevel` (wave reacts to audio), `SleepTimer`, `ShakeDetector`.
- `VoiceBoost` — DynamicsProcessing speech chain (1.4).
- `AutoLibrary` — Android Auto browse tree, search, voice play, resumption;
  `ArtworkProvider` — content:// covers for Auto; `EpisodeItems` — the one
  episode → MediaItem builder.

**background/** `RefreshWorker` (3h, also runs gPodder sync),
`NewEpisodeNotifier`. **update/** `AppUpdater`.

**ui/**
- `GlassCastRoot` — tabs Library, Latest, Downloads, Discover, Search;
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
- `Sheets` (trimmed Settings; gPodder pill), `SyncSheet`, `SortIcons`, `L10n`.

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
  redesign (done), a home-screen widget in Material 3 using the app's own
  colors and player/bubble elements (not glass), then per-show settings,
  auto-download (Wi-Fi only), TV info parity.
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
7. Deferred: Room database, per-show settings, auto-delete played downloads,
   translating Discover genre names and a few concatenated TV empty-state
   strings.

---

## 8. Reddit

Posted in r/Xiaomi; planned: r/androidapps, r/googletv, r/podcasts and
r/androiddev (weekly threads). Answered so far: downloads keep the
publisher's format; Export was promised but has since been dropped (reply owed); Boost voices is a loudness boost with
compression, not voice isolation. Asked for: gPodder (built in 1.3), Android
Auto, sponsor skipping, Spotify (no — public RSS only).
