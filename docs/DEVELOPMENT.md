# GlassCast — developer notes

## 1.5 (in progress) — tablets, TV focus, Downloads

Version 1.5 (versionCode 16).

**TV: stuck on the rail.** Opening a show from Discover or Search goes
through `previewUrl`, which the content-focus effect didn't key on; the
clicked card vanished, focus fell to the only focusable thing left (the
rail), and the rail opened. The effect now keys on tab, selected show,
preview and its load, and the player closing; the loading spinner holds
`contentFocus` so focus can't drop; and the rail handles Right itself
(`contentFocus.requestFocus()`), not trusting geometric search with the page
laid out under it. A mouse worked throughout because clicks don't search.

**TV: hold opens the episode menu while held**, like Discover cards:
`tvFocusableRow(onLongClick)` uses `holdSelect`; `TvEpisodeMenu` has
`ignoreHeldSelect()`. The trailing queue-icon button on episode rows — the
old way into the menu, which the remote couldn't reliably reach — is gone.

**TV: show description** in the header is focusable; select expands it from
three lines to all of it, and back.

**Tablets.** Library grid is `GridCells.Adaptive(160.dp)` (2 columns on a
phone, more on a tablet). The bottom chrome (mini player, tab bar, bubble) is
capped at 640dp and centered; `barBounds` now also refreshes when the bar's
left edge moves (rotation, split screen), still not on width, which changes
every frame of the collapse. `Modifier.readableWidth()` (720dp, centered) on
Latest and Downloads. Not done: the show page (its blurred backdrop scrolls
inside the list, so capping the list would cut it), a two-pane landscape
player. Choppiness at 144 Hz noted, not investigated.

**Landscape tablets (≥480dp tall, wider than tall).**
- *Full player*: the portrait player turned on its side. The cover fills the
  height on the left (`min(height, 55% of width)`) and dissolves rightward
  (`PlayerArtwork(sideways)`); the controls keep portrait's order in a column
  beside it (`colLeft`/`colW` replace `side`/`w − 2·side`; portrait computes
  the same numbers as before), vertically centered above the strip (`lift`);
  the panel rises inside the column and only the column dims. Cause of the
  "information loads late" on the pad: portrait's cover is `maxWidth × 1.2`
  tall — taller than a landscape screen — so it covered every control, and
  during the shared-element flight (drawn above everything) they stayed
  hidden until it landed. Frames 24–30 and 83–89 of Jonathan's recording.
- *Show page*: two panes. `ShowHeader(compact)` and `ShowActions()` (local
  composables) on the left over a fixed full-screen `ArtworkBackdrop`;
  `showEpisodes()` (a local `LazyListScope` extension) in its own list on the
  right. Portrait uses the same pieces, unchanged in order and look.
- Rotation already doesn't recreate the activity (manifest configChanges).
- *Second pass, after device screenshots:* portrait cover height is capped
  so the title starts 86% down it (`titleTopPortrait / 0.86`, floored at 60%
  of the short side) — tall tablet portraits put the title on the sharp
  cover. Landscape backdrop mirrors sideways (`softenedMirrorSideways`: the
  cover with its reflection to the right, blurred as one) instead of
  stretching the vertical mirror; its darkening runs left to right. No panel
  dimming in landscape (it read as a dark box). Landscape drag-dismiss slides
  the player off as one sheet (`closing` drops the shared element) instead of
  flying the cover diagonally to the bubble after the drag moved it down.

**App icon refresh.** New gradient and rounded glyph from Jonathan's art.
Adaptive layers are PNGs in drawable-nodpi (432px); the glyph spans 54% of
the *visible* icon (launchers show the middle 72dp of 108dp, so 36% of the
layer) — 42.5% read small, 60% was tried, the final art (purple vertical
gradient) ships at 54%; earlier icons sized against the whole layer
and read as zoomed in. No dark-mode variant: the night layers are deleted, and
any night-qualified icon resource would outrank the adaptive icon at night
(the 1.3 bug). Monochrome layer kept for opt-in themed icons. Legacy mipmaps
regenerated from the visible crop (square and round). TV banner rebuilt (new
gradient and glyph, Figtree wordmark inside its margins). `ic_mark` (splash,
placeholder, notification icon) redrawn as the rounded glyph.

**Downloads.** Finished rows show a downloaded mark instead of a trash can;
swipe left deletes (`SwipeToDelete`, the Up Next swipe's gesture and reveal
in the error color). Select (top right) turns marks into circles, with
Select all, Delete (confirmed) and Cancel; Back leaves select mode. Rows
still downloading keep their cancel button. No auto-delete (Jonathan's call).

## 1.4 (in progress) — Google TV fixes, the living backdrop, the palette, Android Auto, Boost voices

Version 1.4 (versionCode 15). More 1.4 goals to come before release (ad skipping).

### Fourth round

**Skip ads parked too.** Toggle removed from Speed & sound (phone and TV) and
`Settings.skipAds` pinned to false (a value saved while testing must not keep
it running unseen). The engine stays in the code, dormant. README updated.

**Downloads that streamed but wouldn't download** (seen on The Broski
Report, Audioboom): DownloadManager stops after 5 redirects, the player
follows 20, and podcast audio often chains tracking redirects. `Downloads.start`
now resolves the address itself first (up to 15 hops, `Range: bytes=0-0`, each
hop requested once) and enqueues the final one, with a GlassCast User-Agent.
Failures log DownloadManager's reason under tag `GlassCastDownloads` (HTTP
status, or 1000-range codes; 1005 = too many redirects). Untested against the
Broski feed itself — the log will say if it's something else.

**SponsorBlock parked.** Removed from the build (code kept in
`docs/parked/SponsorBlock-YouTube.kt.txt`, with restore notes): it rarely
found a matching video, and shows with inserted ads never pass the length
check. Skip ads now uses chapters and transcripts only. README updated.

**Player title icons**: with a sleep timer the icon row is 140dp instead of
96dp; its offset was fixed for 96, so it grew off the right edge and cut Cast
off. Now `iconsWidth` sets both the offset and the title's width.

**Show page chips**: "N unplayed" removed (Hide played covers it). Plain row
again — Hide played, Downloaded, sort — with the filters `weight(1f, fill =
false)` and `Pill` single-line with ellipsis, so sort can't be squeezed.
`Pill` gained a `modifier` parameter.

**Show info**: title Bold, publisher SemiBold.

**Discover's Not interested dialog**: Cancel in onSurfaceVariant, as Library's
Unsubscribe dialog.

**Mini player in dark mode**: the card swaps its pair — the cover's pale
tone as the card, the dark tone on the play button and progress line, dark
text — because the dark card sank into a dark page. Light mode unchanged. The
bubble's ring keeps the original pair in both themes (Jonathan's call).

**Full player's top vignette**: the flying cover is drawn in the shared
transition's overlay, above everything, so the top scrim and the handle were
under it for the whole flight and appeared only when it landed.
`Modifier.aboveFlyingArtwork(scope)` (SharedElements.kt) renders them in the
overlay at zIndex 1, with alpha following the player's enter/exit transition
(the overlay doesn't inherit the player's fade).

**Sleep timer status**: the moon beside the title is gone (it took the title's
room and repeated the panel bar's). The panel bar's timer button stays lit —
its selected background — while a timer runs, with the accent moon. Its
"Running" label is now translated.

**Hidden shows in Settings**: the summary row expands to a list (cover, name,
Show again each; Show all again). `Settings.hideFromDiscover(url, title, art)`
now stores name and cover (`discoverHiddenInfo`, JSON); shows hidden before
this show their feed's host name. `unhideFromDiscover(url)`.

### Third round

**Boost voices reverted** to the 1.3 LoudnessEnhancer (+7 dB). The
DynamicsProcessing chain sounded worse at the same volume: compression from
−30 dB with modest makeup flattened dynamics without adding loudness, and
10 ms frequency-domain frames smeared consonants. `VoiceBoost` keeps its API.

**Skip ads** (Speed & sound, phone and TV; off by default). `data/AdFinder`
finds breaks from Podcasting 2.0 chapters (ad-like titles) and transcripts
(sponsor opener → "back to the show" closer, or the last promo-code / "dot
com" line; 10 s–3 min). `player/AdSkipper` (object, like SleepTimer) holds the
current episode's breaks and a skipped-event flow; the service loads breaks
only while the setting is on, and a 500 ms ticker skips a break playback runs
*into* (landing in one by seeking or resuming plays it). "Ad skipped" toast on
phone and TV (STARTED only). Breaks are drawn on the progress line in
`ArtworkColors.adMark` — the cover color farthest in hue from the accent
(≥ 40°), else the accent darker. `WavySlider(marks, markColor)` redraws the
wave and track inside each break. Limit: dynamically inserted ads may not
match published times; most feeds publish neither, and then nothing is found.

**SponsorBlock** (`data/YouTube.kt`). No input: `YouTube.detect` scans the
show's description (weight 2) and its 12 latest episodes' notes (weight 1)
for channel / @handle / playlist links, takes one with weight ≥ 2 (a guest's
channel is usually mentioned once), else the channel that posted the episodes'
own video links (majority of up to 3). Run by the service the first time Skip
ads plays a show; stored in `Settings.youtubeLinks` (`none:<time>` = none
found, rechecked after 7 days). A manual paste row existed for one build and
was removed — Jonathan preferred scanning only. Links are resolved via the
page's canonical link / externalId / channelId and validated against the
public upload feed (`feeds/videos.xml`, latest 15, no key). Episodes matched by title
words (Jaccard) + episode number + date; Shorts excluded. Segments via the
k-anonymous endpoint (`/api/skipSegments/<sha256[0..4]>`, sponsor + selfpromo,
"skip" only). Every SponsorBlock break carries `onlyIfLengthMs` (the video's
length at submission) and is used only when the playing file is within 3 s —
`List<AdBreak>.usable(lengthMs)` filters and merges, in the service ticker and
in the progress-line marks. RSS audio with inserted ads won't match by design.
README credits SponsorBlock.

**Figtree on every style.** `GlassType` tuned only 7 styles; the other 8
(titleLarge, bodyLarge, labelLarge, headlineSmall…) fell back to the system
sans — the show info sheet was set in it, and so were TV menus using
headlineSmall / labelLarge. `Typography.allFigtree()` now sets Figtree on all
15. (`TvType` in TvTheme.kt is unused; the TV runs on `GlassType`.)

**Show page chip row**: the filters scroll (fading out at the edge) and sort
is pinned at the end. With Downloaded added, the plain row squeezed sort to a
sliver — the same bug as the TV show header.

**SponsorBlock diagnostics**: Logcat tag `GlassCastAds`, one line per step
(detection, match, SponsorBlock response, length check, HTTP failures).
Detection now distinguishes "the show names no YouTube" (`none:`, rechecked
after a day) from "YouTube unreachable" (`retry:`, after an hour); the first
build stored `none` for a week even on a network error. Links key bumped to
`youtube_links_2` to drop those.

**Show info**: an (i) at the top right of the phone show page (the back
button's twin), when the feed has a description or categories. Opens
`ShowInfoSheet`: cover, title, publisher (accent), episode count, category
pills, full description (`stripHtml`), in the show's colors. Asked for on
Reddit.

**Show page Play button**: the cover's background hue (`showPlayColors` /
`coverEdgeColor`), else the accent, else black/white — checked against the
page, not the cover's corner (the old check turned blue-on-blue covers black
or white, and darker 1.4 accents tripped it more).

### Second round

**Palette, again.** The first 1.4 build let neutrals win the ground whenever
they were the biggest family: red + black-and-white covers (Evolution of a
Snake) and multicolor ones (Good Hang) went griege. Now the ground is the
largest *color* family whenever color is ≥ 1/5 of the cover (neutral ≤ 4×
all colors); only overwhelmingly neutral covers (Broski, ~90%) get a neutral
page. Tested on six covers cropped from screenshots (Broski, Deutschland3000,
Trixie & Katya, Good Hang, The Toast, Evolution of a Snake).
Light-mode cards: were 16% under a light blur (The Toast's slate #8A8C94,
Trixie's brick red); now `(averageL − 0.06)` clamped 0.78–0.84, saturation
0.14–0.40 — pastel. The Toast was gray before 1.4 too: its blue-gray studio
backdrop is the largest area. Yellow → gold now at every lightness (dark
theme had lemon primaries and olive containers; `themeAccent` → `withHsl`).

**Boost voices** (`player/VoiceBoost.kt`): DynamicsProcessing speech chain —
5-band pre-EQ (−12 dB < 80 Hz, −2.5 dB to 250 Hz, flat to 1.8 kHz, +4 dB to
5 kHz, +1 dB above), 3-band MBC (250 Hz / 4 kHz, ratios 3–3.5, thresholds
−30/−32 dB, makeup 0/+7/+4 dB), limiter at −2 dB. Android 8 falls back to the
old LoudnessEnhancer (+7 dB). `AudioEffect.setEnabled` returns int — call it
explicitly rather than using Kotlin's `enabled =`.

**Android Auto** (`player/AutoLibrary.kt`, `player/ArtworkProvider.kt`).
`PlaybackService` is a `MediaLibraryService`. Tree: Up Next (live player
items), Latest (40), Downloads, Library (grid) → show → 100 episodes. Played /
in-progress marks via `android.media.extra.PLAYBACK_STATUS`; browse and search
hints as plain keys. Search + "play X on GlassCast" (`requestMetadata
.searchQuery` → show's newest unplayed, else episode title). `onSetMediaItems`
passes the app's complete items through untouched; bare ids/queries play like
the app's `play()` (saved position, Up Next kept). `onPlaybackResumption` from
`QueueStore`. Everything awaits `FeedStore.awaitLoaded()`. Artwork: exported
read-only provider, `content://<pkg>.artwork/<base64url>`, serves only URLs in
the library, from `ImageStore.cachedFile`. `EpisodeItems.playable` is now the
single episode → MediaItem builder (app and service).

**README** rewritten in a plain first-person voice (see HANDOFF, "README").

### First round

**Show pages under the rail.** The show page is laid out full width and its
content inset by `leadingInset` (the collapsed rail). Its backdrop used to
start after a 92dp spacer, so the strip beside the rail stayed the shell's
now-playing color.

**Show header.** Four labeled pills needed ~470 of the ~478dp beside the
cover; Refresh got 8dp and its label wrapped a letter per line — the "scrolling
line", whose height was the gap above the episodes. Now: Play/Resume labeled
(`weight(1f, fill = false)`, so a long translation ellipsizes), Follow labeled
until followed, then a check; sort and refresh are `TvIconButton`s. Flipping
sort shows a toast. Pill labels are single-line.

**Player transport.** Fixed widths (512dp) in a 362dp column squeezed Forward 30
and hid Next. Now the phone's weights (0.78/1/1.7/1/0.78, 4dp gaps) across the
column. Shapes are fixed (phone corners 30/10); only play/pause morphs — 38dp
oval paused, 22dp playing, phone spring, clamped. Focus is a fill only (no
grow, no ring); only the focused button is lit. Select squashes to 0.93.
The panel bar is four equal icon slots (count / speed / timer value shown).

**The white outline** was `border(if (…) 3.dp else 0.dp)`: 0dp is
`Dp.Hairline`, a 1px line. Same bug fixed in `TvTextField`. Never pass a
computed 0dp to `border` — make the modifier conditional.

**Discover hold menu** (`TvShowMenu`, `TvShowMenuRequest`). Compose 1.7's
`combinedClickable` has **no key long-press**, and its clickable clicks on any
key-up, even one whose key-down it never saw. So: `tvFocusable(onLongClick)`
→ `holdSelect` owns select keys in preview (timer on the first down, repeats
swallowed, release after a hold swallowed); the menu root uses
`ignoreHeldSelect()` so the held key's repeats and release don't choose
Follow. Follow/Not interested hide the show at once (`gone`), and focus goes
to the card now at the same row/index (`FocusSlot`, claimed via a plain field
so the claim doesn't recompose and cancel itself), or the first card if the
row went. A shelf rebuild that drops the focused card also refocuses.

**Living backdrop** (`TvCoverBackdrop`, all TV pages and the player). Two
copies of the 32px softened cover, drawn as squares 1.2× the screen diagonal,
rotating in opposite directions (110s / 150s) around drifting centers; the
upper at 55%. Canvas draw only; clock ticks every 40ms, capped steps, stops
below STARTED and under the open player (`LocalTvBackdropMoving`). New covers
crossfade. Browse pages use it with `even = true` (uniform scrim); nothing
playing keeps the still `artworkGround`.

**TV text fields** open the keyboard only on select: the focus stop is the
box; the `BasicTextField` has `canFocus = editing`. Done or moving off hands
focus back (box first — disabling a focused field would clear focus).

**Palette (shared, phone and TV).** Reproduced from screenshots with a port of
Palette's quantizer: on The Broski Report the cream background (#F8F8E0,
chroma 0.09, HSL S ~0.5) won the accent and became #B7B729. Now: colorfulness
is RGB chroma; swatches under 0.14 are neutrals, pooled, never the accent;
the dominant is the biggest pooled *family* (a flat lime title no longer beats
a textured gray wall); accent families need 4% (fallback 1.5%, else neutral).
`withHsl` caps the result's chroma at 1.5× the source's and turns yellows
(45–75°) toward gold below 0.5 lightness; neutral sources keep ≤0.08
saturation (`tinted`). Expected: Broski gold accent on warm neutral;
Deutschland3000 purple on neutral.

## 1.3

- **Bubble ring**: the mini card's own pair — `button` played, `surface` unplayed.
- **Flight into the bubble**: `bubbleFlightPath` — a superellipse (n 24→2) whose
  scallops grow in over the last 40% at the bubble's spin; identical to
  cookiePath at landing (verified: 0.0px).
- **Palette**: accent chosen per 30° hue family (pooled population, grays set
  aside, ≥4% of the cover to qualify) — a few vivid letters no longer win.
  New `card` color for show-page episode cards, set ~0.16 lightness away from
  the cover's true average (whites and blacks included, which Palette drops).
- **Latest / Downloads rows** wash in their show's color (`elevated`, animated).
- **Episode order** per show (`Settings.episodeOrder`), toggled on the show page
  (drawn icons, `SortIcons.kt`); removed from Settings on phone and TV.
- **Skip silence**: 0.2s / 30% / threshold 768 — midway between 1.1 and 1.2.
- **Downloads**: button beside Cast in the player.
- **Pull-to-refresh** on Latest (refresh all) and Discover (new round).
- **Toasts**: "Added to library" wherever a show is followed; `LocalToast` is
  now provided at the root.
- **Not interested** uses the Unsubscribe dialog.
- **gPodder sync** (`GPodderSync.kt`, `SyncSheet.kt`): gpodder.net-style and
  Nextcloud servers; subscriptions (first sync merges) and play actions
  (recorded on pause / mark played, batched 30); devices joined into one
  gpodder.net sync group at sign-in; password sealed with a Keystore AES key.
- **Text**: explanatory lines removed from Settings, the speed panel, Discover
  and the OPML sheet; American spelling throughout (`SHOW_COLOURS` is a stored
  value and stays).

- **Languages**: Korean, German, Spanish, Dutch, following the system language
  (no setting). `tr(en, args…)` in `ui/L10n.kt` looks the English up in the
  language's table; {0}/{1} placeholders let each language order its words.
  A function rather than string resources because much text is set outside
  composition (toasts, sync errors, notifications); anything missing falls back
  to English. New text: write `tr("…")` and add a row to all four tables.
  Nothing may compare against display text (checked: none does).

### TV
- **Player** rebuilt in the phone's language: connected transport group
  (focused buttons morph to pills), wave scrubber (left/right seek), panel bar
  opening a side panel with Up Next / Info / Speed & sound / Timer.
- **Sleep timer** fixed: it subtracted the wall clock from an elapsed-realtime
  deadline, so it always read 0:00.
- **Show page** on its own blurred cover and palette; Follow/Following
  (Unfollow confirms), per-show order, and a scroll spec that replaces the TV's
  30% pivot (the header's buttons scrolled the page, fighting its own
  scroll-to-top).
- **Discover/Search** open a preview instead of subscribing on click.
- **Hold select** on an episode: menu (play now / next / Up Next / played) with
  toasts. **gPodder** sign-in in TV Settings.

## 1.2

### The cover landing outside the card
Two tester recordings, one cause. The morph moved the cover with a layer
*translation*, so its layout position stayed at the box's top-left — and the
player-closing flight lands on an element's layout position. The cover flew to
the screen's left edge (card) or the box's corner (bubble), sat there, then
snapped. The cover is now genuinely measured and placed each frame. The
bubble's center is a fixed 106dp below the box's top (76dp box + half the 60dp
bar), used whenever the measured bounds aren't in yet.

### The cover's shape in flight
In flight, a shared element is drawn on the transition overlay, above
everything, and the clip its home applies (MiniPlayer's drawWithContent) doesn't
reach there — so the cover flew as a hard square and took its shape only on
landing. Both ends now pass `FlightClip` as `clipInOverlayDuringTransition`: a
Shape that derives its corner from the current size (square at player size,
rounding with an eased curve as it shrinks) and lands on `MiniArtShape.roundness`,
which MiniPlayer writes as it draws (0.25 card, 0.5 bubble). 48dp lands at 12dp
corners; the 44dp bubble lands round.

### Page-to-page cover flights removed
The library/Discover/Search → show page flight hung whenever you scrolled
during it — back in 1.1, forward in a new recording. The player flight stays.

### Also
- Card/bubble state persists across tabs (it reset on every page change).
- Bubble ring: played arc in the cover's vibrant accent, track in a second,
  deeper cover color, both mid-tone (`ringPlayed`, `ringTrack`) — the old
  18%-white track vanished in light mode.
- Skip silence tuned for speech (`speechSilenceSkipper`): 0.3s minimum pause,
  40% kept (max 1s), threshold 512 — ExoPlayer's music-oriented defaults
  (0.1s, 20%, 1024) clipped quiet words.
- Library bar in Cider-style pills; the scroll value is read in layers, not
  composition.
- Discover: refresh (deeper, seeded shuffles plus a rotating extra genre),
  long-press Not interested (Settings can restore), a process cache so
  revisits don't refetch.
- Transcripts: Podcasting 2.0 JSON accepted (SRT/VTT still preferred); word-
  level segments joined into lines at sentence ends, speaker changes or ~12s.
  Apple's own transcripts aren't reachable: they're generated by Apple and
  served only to Apple Podcasts through a private API.
- **Downloads** (`data/Downloads.kt`): Android's DownloadManager into the app's
  own folder (no permission; removed with the app), polled only while active,
  verified on launch. Playback uses the file when present; items carry their
  online address so a Cast hand-off swaps back to the stream. A Downloads tab,
  a Downloaded filter in Latest and on show pages, a badge on every row, and
  Download / Remove in the episode sheet.

## In-app updates (1.1)

`update/AppUpdater.kt` checks `anixitea/GlassCast-for-Android`'s latest
release (GitHub excludes drafts and pre-releases), compares the tag to the
installed `versionName` numerically (1.10 > 1.9; "v" prefix ignored), and
announces a newer one with a banner — dismissing it retires that version,
though Settings › Check for updates always shows what's there. Automatic
checks run at most every 12 hours; GitHub allows 60 unauthenticated calls an
hour per network.

The APK downloads to `cache/updates/` with progress, must match the asset's
exact size, and — when GitHub publishes one — its SHA-256 digest, or it's
deleted. It's handed to Android's installer through a FileProvider scoped to
that one folder. Android asks once to allow GlassCast to install apps (the
sheet resumes the install when you come back) and confirms every install.

Release rules that make it work:
- **One APK per release**, tagged like `v1.2` with `versionName` 1.2 and a
  higher `versionCode`.
- **Always the same signing key.** Android installs an update only over an
  app signed with the same key; a debug-signed 1.0 can't be updated to a
  release-signed build — testers have to reinstall once.
- `REQUEST_INSTALL_PACKAGES` is fine for GitHub builds; Google Play forbids
  self-updating apps, so a Play build would have to drop it.

## Tooling note

`tools/syntaxcheck.sh` parses every file with kotlinc and reports syntax
errors only. It exits non-zero if the compiler is missing. The old check was
a bare pipeline, and when the compiler vanished from /tmp it printed nothing,
which read as "no errors" — WavySlider shipped with a stray brace that way.

## 1.1 — sound, swipes, and speed

From the first round of tester feedback.

- **Skip silence** and **Boost voices** in the player panel's Speed & sound
  tab (and TV Settings). Skip silence is ExoPlayer's own; Boost voices is a
  `LoudnessEnhancer` (+7dB, limited) on a fixed audio session generated up
  front, so it attaches before the first sound. Both follow the settings live
  from the service.
- **Swipe right** on an episode marks it played or unplayed, with a toast;
  left still queues. The reveal tile's growth is read in the layer, so a swipe
  redraws rather than recomposes.
- **Discover card text**: the cards were clipped to a rounded rect for the
  ripple, and the bottom-left curve cut the first letter off the last line.
  No clip now; the card squashes on press instead.

### The collapse crash, and the tab bar's feedback loop

**Crash** (`width(-1) and height(174) must be >= 0`, GlassCastRoot): the
collapse spring was underdamped, so on the way back to the card it dipped just
below 0; the bubble slot multiplied that into a width of −1px and
`Constraints.fixed` threw. The spring is critically damped now
(`DampingRatioNoBouncy`) and every reader clamps to 0..1 as a second guard.

**Lag**: the tab bar animated through composition. Each tab wrote its position
into a state map from `onGloballyPositioned`; the pill read the map during
composition and chased it with springs; each tab's width was a spring read the
same way. Any change of size ran layout → positions written → recompose →
springs retargeted → layout, frame after frame. It was brief on a tab switch,
but the morph narrows the bar on every frame of a collapse, so it ran on every
scroll. The bar is now a custom `Layout` driven by two `Animatable`s — `pill`,
the selected tab as a traveling index, and `labeled` — read only in measure
(tab widths) and draw (the pill). Nothing in the bar recomposes to animate.

Cookie paths take a `steps` count; the bubble uses 72 rather than 216.

### The title scrolls once

A long episode title in the full player glides across once, 1.5s after the
player opens, and comes to rest at the start — Apple Music's behavior, not a
perpetual ticker. `basicMarquee(iterations = 1)`; it replays only when the
player is opened again (it's composed fresh each time) or the episode changes
(`key(episode.guid)`). The title fills its width and fades over the last 10%,
so a long one never ends mid-letter and a short one never reaches the fade.

### Tooling: owncheck

`tools/owncheck.py` flags a file that uses a class or function without
importing it: the app's own declarations (Episode, tr…) and any library class
the project imports somewhere else (Path, Brush…). It exists because of a
build failure in 1.3: a script checked "is `import …data.Episode` already
there?" by substring, and `import …data.EpisodeSort` said yes. Run it with the
others before every package.

### Tooling: extcheck

`tools/extcheck.py` flags Compose extensions (`.clickable`, `.layout`,
`.drawWithContent`…) used in a file that doesn't import them — the class of
error that surfaced as `Unresolved reference 'clickable'`. The syntax check
can't see it; this runs with the others before every package.

### Card ⇄ bubble as one morph (Cider's approach)

From a frame-by-frame look at a screen recording of Cider: its mini player
doesn't swap two elements. The card shrinks toward its right end until only
the cover is left, then that cover drops beside the bar, turning from rounded
square to circle to scalloped bubble. The layout above the bar never moves —
the page shows through once it's collapsed — and only the bar narrows.

`MiniPlayer` now does exactly that, as one element driven by one value
(`collapse`, 0 = card, 1 = bubble, a spring in the root). It's read only in
`drawWithContent` (container, ring) and a placement block (the cover's
position and scale), so a frame of the morph repaints and re-places, and never
recomposes or re-measures the card. `MorphMetrics.frame` holds the geometry:
phase one (0–0.55) shrinks the card to a square at its right end; phase two
drops it to the bubble's center, taken from the tab bar's measured bounds. The
cover's clip opens from rounded square to circle, then the cookie's depth grows
from 0 (a circle) to full; the ring fades in over the last 30%. The card's
text and controls are composed only while `collapse < 0.35`, so invisible
buttons never catch taps meant for the page. The bar gives up a slot measured
from `collapse` in a `layout` block — it narrows without recomposing — and the
slot is what you tap. MiniBubble and its separate key are gone.

The bar is opaque, as Cider's is; the page's blur shows around and beneath it.

### The card ↔ bubble swap, and the hanging cover

Tester report: scrolling was smooth while the card or the bubble was showing,
and choppy at the moment they swapped — worst on a short list scrolled back and
forth. Three causes, all in the swap:

- **They shared a flight key.** Card and bubble both used `NowPlayingArtKey`
  (so the player could grow from either), so every swap also ran a
  shared-element transition from one to the other — the heaviest animation in
  the app, chasing a bubble that was still growing. They now have their own
  keys (`MiniArtKey`, `BubbleArtKey`) and the player takes whichever is
  showing.
- **The card animated its height**, re-laying-out the chrome column every
  frame. It fades and scales in place now; it sits above the bar, so nothing
  moves when it leaves.
- **It flapped.** 8px of scroll flipped it. Now ~48dp of travel in one
  direction, reset on reversal.

The cover that "hung" on the library after going back was the return flight:
scroll during it and the cover finished at the tile's old position before
jumping. Flights are open-only now — the tapped tile is remembered with
`remember`, not `rememberSaveable`, so coming back just fades.

### The wave follows the voice

`player/VoiceLevel.kt` is a `TeeAudioProcessor.AudioBufferSink` in the audio
chain (ahead of skip-silence and speed, which `DefaultAudioProcessorChain`
still appends). The tee passes audio through untouched — the worst failure is
a still wave, never silence — and needs no microphone permission, unlike
Android's Visualizer. RMS every 4th sample, −40..−12 dBFS mapped to 0..1, fast
attack and slightly slower release; each reading is stamped +250ms (the output
buffer) and read back when its moment arrives. Stale for 1.5s (paused, casting)
→ a neutral level. The wave's height is 30% + 70% × level: a ripple in pauses,
full on the words.

### Why it felt heavier than Cider — and keeping the glass

Haze's own benchmarks (docs/performance.md in the Haze repo, Pixel 6): blurring
an app bar and a bottom bar adds about 2ms a frame (+29%). Input scale saves
5-20% of that; masks and progressive blur cost about the same on SDK 34+. At
60Hz, 2ms disappears into a 16.6ms budget. At 120-144Hz a frame has about
7ms, so it's a quarter or more of the budget. The blur itself is fine; how
*often* the screen redraws is the multiplier.

- **Decorative motion runs at 30fps** (`FrameClock.kt`). The mini player's
  wave, the bubble's turn and the equaliser kept the whole screen redrawing at
  full panel rate while anything played — 144 blurred frames a second while
  you read a list. They advance 30 times a second now and stop dead when
  paused. The equaliser was the worst: its infinite transition ran even while
  paused and fed values through composition, so a list showing the current
  episode recomposed every frame, indefinitely. The full player's wave stays at
  full rate.
- **One blur behind the bottom chrome, not two.** The tab bar ran its own
  blur on top of the bottom fade's. It's a tinted pane over the fade's blur now
  (the fade runs stronger at its foot to carry it) — still frosted, one pass.
- **The top blur exists only once the page has scrolled.** At the top there's
  nothing moving under it.
- **`inputScale = Auto`** on both: small, free.
- **Static blurs are made once** (`SoftBitmaps.kt`): the player backdrop and
  show header blur a still cover, which looks identical blurred once or every
  frame. The mirror pair is one bitmap flipped about the seam, so it stays
  continuous.
- **Library tiles carry flight keys only when tapped**, like Discover and
  Search.

To measure rather than guess: Developer options → Profile HWUI rendering →
On screen as bars.

## 1.0.1 — the Google TV version, rebuilt for the Streamer

The TV app (same APK, `tv/` package, launched by `TvActivity`) had fallen a
full design pass behind the phone, and it stuttered on the Streamer's chip.

### Performance

- **Focus is a Modifier.Node** (`TvFocus.kt`). It animated with
  `animateFloatAsState` fed into `border(width = …)` — both composition reads,
  so every frame of the ~300ms focus spring recomposed the whole focused item,
  and each D-pad press animates two items. The animation now lives in a node
  and is read only in `draw()`: a focus change is a few draw calls, never a
  recomposition or relayout. The outline is cached per size and shape.
- **The rail no longer reflows the page.** A spacer under the page used the
  rail's *animated* width, so opening the rail re-measured the five-column grid
  every frame. The spacer is constant; the rail slides over the page.
- **Position is read where it's drawn.** The shell collected the 0.4s tick and
  rebuilt everything; now the player reads it inside `TvWithPosition`, and the
  rail bubble's ring reads it inside its draw lambda only.
- **The player backdrop is blurred once.** The wave keeps the player redrawing
  every frame, so a RenderEffect blur would be recomputed on each. The cover is
  shrunk to 32px and box-blurred off the main thread, then stretched with
  bilinear filtering: one bitmap draw per frame.
- **No perpetual animations while browsing** — the bubble doesn't spin, the
  playing marker is a static waveform — so browse screens are idle when idle.
- **Covers decode near their shown size** (grid tiles 180dp, not 260).
- **Remembered derivations**: show notes stripped once, date lines built once,
  `contentType` on lazy items.
- **R8 on for release** with `-dontobfuscate`: the optimization without
  unreadable crash traces.

### Matching the phone

- **Rail** is a floating panel in the cover's hue (`chromeBar`), like the
  phone's tab bar, with pill selection and a dim over the page when open.
- **Playing** is the phone's cookie bubble with a progress ring tracing the
  scallops.
- **Focus rings and filled buttons** use the pale cover accent (`chromeButton`).
- **Episode rows** are cards: resting surface, progress bar when started,
  check when played, a waveform on the one playing.
- **Show page** has the cover's color washed down from the top (a gradient,
  not a blur).
- **Discover** opens with "Top picks for you" cover cards, then the shelves —
  built by the phone's shared `buildShelves`.
- **Player**: blurred-cover backdrop, the wave, the morphing play button, and
  Up Next / speed / sleep / notes as pills (speed and sleep cycle on OK).

# GlassCast 1.0

A podcast player for Android, sibling to GlassBook. Kotlin + Compose, Media3,
package `com.glasscast.app`.

## Opening it

Unzip, then **Android Studio → Open** and point at the `GlassCast` folder.
Gradle sync will pull every dependency on first run. The wrapper is included
(Gradle 8.9); if Studio offers to use its own bundled Gradle instead, either is
fine.

Nothing here has been compiled or run — the build environment had no access to
Google's Maven, so Gradle could not resolve a single Android artifact. The
sources parse cleanly under the Kotlin 2.0.21 compiler, which catches grammar
but not API drift. Treat the first sync as the real test.

## What milestone 1 does

- Background playback through a `MediaSessionService`, with lock-screen
  controls, audio focus, and becoming-noisy handling
- Add a show by pasting an RSS URL; feeds parsed with `XmlPullParser` including
  the `itunes:` namespace, deduped by `guid` with an enclosure-URL fallback
- Conditional refresh via `ETag` / `If-Modified-Since`, per show and for all
- Episode list with resume position, remaining time, played state
- Player screen: artwork-driven drifting color fields, scrubber, ±30s, speed
  sheet, sleep timer, skip-to-next
- Show page built as a poster: the cover blurred full-bleed behind it, the sharp
  cover, title, and a centered action row over the dissolve
- Player with full-bleed artwork dissolving into an animated mesh backdrop
- Latest tab — everything new across every subscription, newest first
- Library search filters what you already have; the Search tab browses iTunes
  charts by genre
- Frosted floating chrome — glass tab bar and mini player, blur ramp under the
  status bar
- Search reachable three ways from the Library: a resting pill under the title,
  its collapsed self in the top bar once you scroll, and a pull past the top of
  the grid
- Two tabs — Library and Search — with the mini player welded to the top of the
  bar, Apple Podcasts' shape drawn in Material
- Up Next as a sheet off the player: play next, add to queue, swipe left to
  remove, tap to play. Backed by ExoPlayer's own playlist and mirrored to disk
  so it survives the process
- Episode notes sheet with chapters, where a feed publishes them
- Directory search on its own tab; tapping a result opens the show to browse,
  with an explicit Add. Adding by RSS URL is a separate sheet in the Library
- Mini player, splash, in-app light/dark, artwork cache management

## The accent

Purple, sampled from the mark's own gradient rather than picked independently:

| | |
|---|---|
| Default (dark ground) | `#AF52DE` |
| Light mode | `#9B37C3` |
| Pressed | `#7D23A0` |

The three stops hold the same value relationship GlassBook used across
`#FF9500` / `#E07B00` / `#B86200`, so a button carries the same weight in both
apps. Everything else in the palette is unchanged.

## About a dark-mode launcher icon

Android has no direct equivalent of iOS 18's dark app icon. Two things exist,
and they are not the same:

**Themed icons** (`<monochrome>`, API 33+) is the reliable one, and the icon
already ships it. When someone turns on themed icons in Wallpaper & style, the
launcher discards the purple background and tints the RSS glyph from the system
palette, so it tracks light and dark for free. The trade is that the brand
color goes with it, and most people never enable the setting.

**A `-night` qualified icon** is now in `drawable-night/`: the light icon
inverted, near-black tile with a purple glyph, built from your concept.

Only the two *drawable* layers are night-qualified — there is deliberately no
night PNG set. Night-mode qualifiers outrank density in Android's resolution
order, so a `mipmap-night-hdpi` PNG beats `mipmap-anydpi-v26` after dark: the
launcher stopped using the adaptive icon at night and fell back to a flat,
full-bleed image, which looked zoomed in next to the light one. Colors are sampled from it — `#22242D` to
`#111319` on the tile, `#9B4ED0` on the mark, which sits between the light
icon's `AccentPurple` and the dark end of its gradient so the two read as one
brand rather than two purples.

Treat it as a nicety rather than a feature. Launcher icons are resolved and
cached by the launcher process, not the app, and that cache generally isn't
invalidated when the system flips to dark, so the variant may not appear until
the launcher's icon cache is rebuilt, and some launchers won't pick it up at
all. It costs a few files and is harmless when ignored, which is the reason
it's worth having anyway.

There is no supported API for shipping two full icons and choosing between
them at runtime. The usual trick — activity-alias entries swapped with
`setComponentEnabledSetting` — makes the icon vanish from the home screen and
drops any placed shortcuts, so it isn't worth it here.

## Typeface

Figtree, bundled in `res/font`, OFL — license text at `FIGTREE-OFL.txt`.

Google Sans is what the app is reaching for and it can't ship: it's proprietary
to Google, isn't on Google Fonts, and isn't licensed for third-party apps. On a
Pixel it's the system font and you get it for free; everywhere else you'd fall
back to Roboto, which is what you were seeing. Figtree is the nearest open face
— same geometric-humanist construction, single-storey g, tall x-height, open
apertures. It reads as that family without being it.

Bundled rather than fetched via downloadable fonts, which would add a Play
Services dependency, a certificate array, and a frame of fallback text on every
cold start.

## On "AI animated artwork"

Worth separating what Apple Music actually does from what it looks like it does.
It isn't generating video. It pulls four colors out of the cover, draws them as
soft blobs, and drifts them on slow orbits — that's the whole trick, and it's
what `MeshBackdrop` now does behind the player.

Generating real animation per episode would mean a video model, a per-render
cost, a wait before the player could draw anything, and somewhere to cache
thousands of clips for a library that turns over daily. The arithmetic doesn't
work, and the result wouldn't look better than this.

Two things separate the mesh from the older three-field drift: the orbits run on
coprime periods (47s, 61s, 73s, 89s) so the pattern never visibly repeats inside
a listening session, and a real blur pass smears the blobs together so no
individual circle is legible.

## Dragging the player away

**The dismiss flash.** Resetting the drag offset immediately after calling
`onCollapse()` looks harmless and is not: `onCollapse()` only *starts* the exit
transition, and the composable stays alive until it finishes. Snapping the
offset back to 0 on the next line put the player back at full height, on screen,
and the exit then slid it away a second time — dismissed, redrawn, dismissed.

The offset is left parked off-screen and reset on the way *in* instead, which is
the one moment it can't be seen. GlassBook hit this too; same fix.


The full player closes by dragging it down, not only by the chevron. Two more
things make it feel right:

- The offset is an `Animatable` read inside a `graphicsLayer` block rather than
  composed state, so dragging invalidates drawing and not the tree. This screen
  has artwork, a gradient and a marquee in it and cannot recompose per frame.
- Dismissal is decided on release by distance **or** velocity. A short flick
  should close it and a slow drag most of the way down should too; testing only
  distance makes flicks feel ignored, and only velocity makes careful drags
  snap back.

It scales down ~6% on the way out, so it reads as a sheet being put down rather
than a screen sliding off.

## Android TV

`TvActivity` plus the `tv/` package. Same APK, same version, same
`PlaybackService` — a second Activity rather than a second module, because the
data and playback layers are byte-identical and splitting the project would
have bought a build-graph refactor and nothing else.

**What carried over unchanged:** `FeedStore`, `RssParser`, `PodcastSearch`,
`Opml`, `ImageStore`, `EpisodeExtras`, `QueueStore`, `Settings`,
`PlaybackService`, `PlayerConnection`, the palette extraction, and `Artwork`.
Roughly two thirds of the codebase never learned there was a television.

**What could not carry over is every gesture**, because touch is the one thing a
remote doesn't have:

| Phone | TV |
|---|---|
| Swipe down to dismiss player | Back |
| Swipe left to remove from queue | Focused row, D-pad center |
| Pull down to search | Search tab on the rail |
| Long-press for actions | A focusable button on the row |
| Bottom sheets | Full panes — sheets are a thumb idiom |
| Bottom tab bar | Left rail, one Left press from anywhere |
| Haptics | Nothing; TV boxes have no motor |

**Focus requests must wait for their node.** `FocusRequester.requestFocus()`
throws if the requester isn't attached to a composed node, and on TV that is the
*normal* case rather than the exception: a rail tap changes the tab and asks the
new screen for focus in the same frame, before it has composed. Most of these
requesters also live on the first item of a lazy list, which doesn't exist until
that list measures — and stops existing when it scrolls away.

`requestWhenReady()` in `ui/Components.kt` tries, yields 40ms, retries, and
gives up quietly. Focus landing a frame late is invisible; an exception is not.
The phone's two call sites use it now as well — they were the same latent bug
that simply hadn't fired.

Every screen is handed a `FocusRequester` and **something must carry it**,
including the empty states. A screen focus arrives at with nowhere to land
leaves the remote apparently dead.

**Focus is the whole design.** On a phone "where am I" is answered by where the
finger is. On a TV nothing answers it unless the interface does, continuously,
for every element. `tvFocusable` uses three signals together because any one
alone fails at three metres: the item **grows** (reads first in peripheral
vision), gains a **bright ring** (survives on busy artwork where scale doesn't),
and **lifts** (separates it from neighbours of similar color). Focus is
requested explicitly on entry to every screen — without that the first D-pad
press goes nowhere and the app looks frozen.

Other decisions worth knowing:

- **Left/right seek 30 seconds on the player without moving focus.** The
  remote's horizontal axis is the natural scrub axis, and walking focus onto a
  button to move through an episode would be exhausting. The scrubber is
  therefore a read-only progress bar — making it focusable would add a stop for
  an action the D-pad already performs.
- **Light mode isn't offered.** A bright panel in a dark room is the one thing
  every living-room interface agrees on, so Light and System resolve to Dark.
  Lights out is still honoured.
- **48dp × 27dp overscan margin.** Televisions still crop the edges; content
  scrolls *under* that margin but never starts inside it.
- **The rail draws over the content** rather than beside it, so expanding on
  focus doesn't reflow the page.
- **The glass does not survive.** It was the first thing tried and the first
  thing removed. Haze works by copying what is behind a panel into a layer and
  blurring it; on a phone that layer is a few hundred thousand pixels, on a 4K
  television it is eight million — and `hazeSource` was wrapped around the
  entire scrolling page, so every scroll paid for a full-screen readback whether
  a panel was on screen or not. Panels are solid with a hairline edge now. What
  the blur bought on a phone was depth against content sliding underneath, and
  on TV the content behind these panels barely moves.

- **No elevation shadows.** `graphicsLayer.shadowElevation` casts its shadow
  from the *layer's* outline, which is a rectangle unless a shape is set on the
  layer — so every focused pill wore a gray square. `Modifier.shadow(clip =
  false)` produced the same artifact from the other direction. Scale and the
  ring carry focus without either, and dropping them removes a render pass per
  item per frame.

- **One animation per focusable, not two.** Scale and ring always move together,
  so on a grid of twenty tiles that halved the running animations from forty.

- **Appearance settings are gone from TV.** Every surface draws from the artwork
  palette, so Dark and Lights out had nothing left to change — switching to
  Lights out visibly did nothing. An option that does nothing is worse than no
  option.

- **No mini player; a Playing entry in the rail instead.** A strip along the
  bottom is a thumb affordance — reaching it by D-pad meant traveling past
  everything on the page first. The rail entry appears only while something is
  playing, carries the cover art in place of its glyph, and opens the full
  player. Removing the bar also gave every list back about 130dp of height.

- **Left and right are no longer intercepted on the player.** Consuming them for
  seeking meant focus could never travel along the control row, so the ±30
  buttons were visible, focusable in principle, and unreachable in practice.
  The remote's dedicated media keys still seek; the D-pad moves focus.

- **Controls resized to fit.** They were 56/64/86dp with 14dp gaps — 382dp of
  controls in roughly 350dp of column — so the last button was compressed into
  an oval. `Modifier.size` sets a *preferred* size, and a child short of width
  is squeezed on that axis alone.

- **Feeds can be added by URL on TV.** The Search field takes a pasted feed URL
  as well as a name. Typing a URL on a remote is miserable, so it doesn't get
  its own screen — but without it the TV cannot add a show the directory doesn't
  list.

- **The show header returns to the top.** A lazy list scrolls only far enough to
  bring the newly focused item into view, so coming back up focus landed on the
  Play button partway down the header and the cover stayed clipped. Focus
  entering the header now asks for index 0.

- **The search field is the focus target itself.** It was wrapped in a focusable
  Row, which swallowed focus before the field could receive it — so the field
  never focused and the system keyboard never appeared. A focusable container
  around a focusable child is one focus stop too many.

## 1.0

Version bumped to `1.0` (`versionCode 10`). The jump from 0.1 skips the
intermediate numbers deliberately — leaving room under a release build for
hotfixes without colliding with anything already installed.

Two additions in this pass:

- **The player has skip-previous and skip-next.** Both are always drawn; next is
  dimmed to 30% when nothing is queued. Hiding it instead would move the play
  button sideways, and the play button is the one control on that screen people
  hit without looking. Previous restarts the current episode, since the queue
  keeps no history and there is nothing behind the playhead to return to.
- **Latest has its own refresh.** Refreshing belonged only on the Library tab,
  which is the wrong place — Latest is the screen you open to see whether
  anything new arrived.

## The notification row

Two separate mistakes, and the first fix only corrected one of them.

**A custom-layout button must carry a `SessionCommand`, not a player command.**
`DefaultMediaNotificationProvider` walks the custom layout and keeps only
buttons whose `sessionCommand` is a CUSTOM command — anything built with
`setPlayerCommand` is silently dropped. Mine were dropped, so declaring a custom
layout changed nothing at all.

**Next only renders when there is a next item.** `COMMAND_SEEK_TO_NEXT` is
unavailable on a single-item queue, so with nothing in Up Next there is no next
button to draw. Combined with previous being withdrawn, that left play/pause
alone — which is exactly what showed up.

Now: back 30 and forward 30 are real `SessionCommand`s handled in
`onCustomCommand`, with `COMMAND_KEY_COMPACT_VIEW_INDEX` putting them in the
collapsed notification where there is only room for three. Previous is restored
— on a one-item queue `seekToPrevious` restarts the episode, which is a real
action rather than the dead button I took it for.

**The icon resources are crossed over deliberately.** Media3's
`media3_icon_skip_back_30` draws a *clockwise* arrow and
`media3_icon_skip_forward_30` an anticlockwise one — the opposite of what the
names suggest, confirmed on device. The names appear to describe the button's
slot in their reference layout rather than the direction the arrow points. They
are paired here by what they look like. If they ever flip again, that pairing is
the line to revisit.

Worth knowing: button *order* in the row isn't ours to control. Skinned lock
screens re-lay-out media actions themselves, which is why the row isn't in the
sequence the code declares.

## Shake to restart the timer

Ported from GlassBook, where it's already proven on device. Off by default;
the toggle lives in the timer sheet. When the timer runs out and playback
pauses, shake the phone and it re-arms the same duration and resumes.

Three details that matter:

- The accelerometer registers **only** while a timer is armed, plus two minutes
  after it fires. A listener running all night for a once-a-night feature isn't
  worth the battery.
- Two distinct jolts within a second are required, so a phone sliding off a
  pillow doesn't restart your episode.
- Shaking during the 15-second fade works too, so nobody has to wait for silence
  before reaching for the phone.

"Sleep" is now "Timer" throughout, matching GlassBook.

## Smoothness

- **Judge it in the `fast` variant, never `debug`.** A debuggable build keeps
  debugging hooks on and skips the optimizations Compose relies on; it can
  stutter where the real app won't. `fast` is debug-signed (installs over the
  debug build, data intact) but not debuggable. `profileinstaller` installs the
  Compose libraries' baseline profiles so hot paths are compiled ahead of time.
- **Position is read only where it's shown.** It ticks every 0.4s while
  playing; collected at the root it rebuilt every screen at that rhythm. It's
  now read inside a `WithPosition` boundary around the mini player, bubble and
  full player only.
- **The wave stops when paused.** It was an infinite transition that redrew
  every frame even while flat.
- **Refresh rate is requested through both APIs**: the preferred display mode
  (and again on resume, since skins reset it), plus the Android 15
  `REQUESTED_FRAME_RATE_CATEGORY_HIGH` hint.
- **R8 keep rule for `CastOptionsProvider`.** The Cast SDK finds it by name from
  a manifest string; a minified release would strip it and crash on first cast.

## Storage, and the out-of-memory crash

The library used to be one JSON blob in SharedPreferences, and it crashed the
app out of memory (a 75MB allocation against a 256MB cap). Three things
compounded:

- every save serialized the **whole library** — each episode's full HTML show
  notes included — into a single string of tens of megabytes;
- SharedPreferences holds its **entire contents in memory** for the life of
  the process, so a second full copy of the library sat on the heap permanently;
- `savePosition` → `updateEpisode` → save, and position saves fire every few
  seconds during playback, **each launched separately** — so two or three of
  those giant strings could be building at once. A full refresh saved the whole
  library once per show.

With the heap already near its cap, any spike tipped it over — which is why it
looked random, and like the back gesture's fault.

Now each show's episodes live in their own file under `files/library/`, plus a
small `feeds.json`. A change marks only what it touched; a burst of changes is
coalesced into one write about a second later, under a mutex, so writes never
overlap. Files are streamed an episode at a time (no giant string is ever
built) through `AtomicFile`, so a process killed mid-write leaves the previous
copy intact. The old blob is migrated to files on first launch and removed with
`commit()`. The service flushes on destroy; the background job flushes before
reporting success. Room is still the eventual home — this fixes the crash
without that rewrite.

## New-episode notifications

A WorkManager job refreshes every show roughly every three hours, on a network
and not on low battery, and notifies about what's new. Podcasts publish daily at
most, so a tighter interval would only spend battery to tell you sooner about
something you'd hear later anyway.

"New" is deliberately strict, because a loose rule spams: the guid wasn't in
the library before the run **and** the episode is newer than the show's newest
episode before the run. Feeds re-list old episodes and change guids after a
hosting move; a show with nothing stored yet is skipped, so a first successful
fetch never fires two hundred notifications. Episodes the app itself refreshed
while open are never notified — they're already in the library.

Two things the background case needed from the store:
- **`awaitLoaded()`** — the library loads asynchronously, and a job can start
  the process cold. Without waiting it would refresh an empty library.
- **`flushToDisk()`** — normal saves use `apply()`, which writes later; a
  background process can be killed the moment the job reports success.

Notifications carry the cover, group under a summary past one, cap at six per
run, and open the show's page. Permission (Android 13+) is asked once, after the
splash; Settings has the toggle and says plainly when the system is blocking it.

**HyperOS:** Xiaomi stops background work aggressively. For reliable checks,
GlassCast needs Autostart allowed and battery saver set to "No restrictions"
in its app info.

## Casting

The Streamer didn't appear because an app only shows Cast devices if it speaks
Google Cast; the device running Android doesn't enter into it. GlassCast now
includes the Cast SDK with Google's Default Media Receiver, which plays a URL
with a title and artwork — exactly what an episode is — without a receiver app
of our own.

Casting is a player swap, not a second code path. Media3's `CastPlayer` is a
`Player` like any other: when a Cast session starts, the queue, episode,
position and speed move to it and the session is pointed at it, so every
control surface keeps working unaware. When casting ends it runs in reverse.
Items now carry a MIME type, which the Cast converter requires. The sleep-timer
fade is skipped on Cast, which has device volume but no player volume.

The cast button opens GlassCast's own **Play on** sheet rather than the system
picker (which on HyperOS showed only Bluetooth). Owning it is also the Mac
groundwork: see `docs/handoff-sync-protocol.md`.

## The Cider pass, part three

**The backdrop is the cover over its own reflection.** The earlier blurred
field was a separately framed copy — zoomed to the screen's height and centered —
so whatever sat just below the cover's foot came from the middle of the image,
and the color broke at the seam before settling into a mud of the whole. Cider
mirrors the cover vertically underneath itself: the first thing below the foot
is the foot, reflected, so every color carries straight on. The cover and its
reflection are blurred as one image so the blur runs across the seam rather than
stopping at it, and the sharp cover on top dissolves into its own blurred self.

**The controls travel with the panel.** The title, transport and wave are drawn
once each, and each has two rectangles — full layout and compact header — with
position, size, corners and icon sizes interpolated by how far the panel is
open. That fraction follows the finger, so they move with the drag every frame
rather than fading out below and back in above. Restart and next narrow into the
group's ends as they leave. The full layout is computed bottom-up from the
panel's collapsed top so it holds on any screen height.

**The panel is opaque, in the cover's hue,** under a *complete* dark color
scheme. The translucent first version let the artwork show through behind the
queue; and patching the app's scheme left every unnamed color at its light-mode
value, which is why light mode looked worse. It now holds four tabs — Up Next,
Info, Speed, Timer — and the moon appears beside cast only while a timer runs,
opening the Timer tab. The queue rows are built for the panel: the old sheet's
rows had the same always-visible "Remove" reveal the episode rows once had.

**Covers fly between screens.** One `SharedTransitionLayout` spans the app.
The now-playing cover has a fixed key shared by the mini player, the bubble and
the full player, so opening the player is the cover growing into place; a show's
cover is keyed by feed URL, so a library tile, a search result or a Discover
card all fly into the same show header. Scopes are composition locals, so any
artwork opts in with one modifier and no screen threads scopes through its
parameters. Dismissing by drag hands the cover back from wherever the finger
left it.

**Discover** tallies each show's own `<itunes:category>` across the library — a
show played in the last fortnight counts three times — and turns the strongest
genres into shelves of that genre's chart, minus anything followed. Existing
libraries are backfilled once with an unconditional fetch, since conditional
refreshes never re-parse an unchanged feed.

**Chrome takes the cover's hue.** Mini player and tab bar are dark in both
themes and tinted by what's playing, as Cider's are.

**Pull to refresh replaces pull to search.** The "ghost" search pill was the old
pull-to-search indicator: scrolling back up past the top overscrolled, and the
overscroll counted as a pull. The indicator is the bubble's cookie, winding up
as you pull and spinning while it refreshes.

**The notification follows the system.** Three hand-built layouts of back/forward
30 all ended reversed on HyperOS, and the screenshots showed why the icon swap
couldn't work — the glyphs sat in the same places before and after it. HyperOS
draws its own icons there. The session now declares only what it can do and
each surface renders that natively.

## The Cider pass, part two

**The pull-up panel.** Up Next and episode info live *inside* the player now, in
one surface with two states. Collapsed, its header is the strip along the foot;
pulled up, the same header rides to the top and the content comes into view.
Because it's one object throughout, nothing ever closes and reopens — which is
what made the old white sheet feel bolted on. Pulling up anywhere on the player
raises it, as Cider's does; direction is decided at the start of a drag and
held, so once the panel is moving it can't also dismiss the player. As it rises
the full controls fade and a compact header — title, a small transport group,
the wave — takes over at the top, so the player reads as making room rather
than being covered.

It is *tinted*, not painted: a dark translucent layer over the blurred artwork,
so it takes the episode's colors without being given any. The queue and notes
views are reused unchanged under a local color scheme that makes them render
light-on-dark there and normally everywhere else.

Info moved into the panel; its old slot holds speed, timer, and an output
button that opens the system's own output picker (`SystemOutputSwitcherDialog
Controller`). That picker already lists speakers, Bluetooth, and Cast devices
for apps that register them — real Google Cast is the next step, and it needs
the media router this pulls in anyway.

**Swipe to queue, fixed properly.** It froze and snapped because the row was
allowed to *commit* to its dismissed position and then `snapTo(Settled)` —
an instant jump. Now `confirmValueChange` runs the action and returns false, so
the state never settles at "dismissed" and the box springs home by itself.
One direction (left, Cider's), one obvious icon; the right-swipe's
`QueuePlayNext` read as a monitor with a plus on it. Every queueing path goes
through one helper that raises a small confirmation pill with the cover in it.

**The tab bar** is one traveling pill on an underdamped spring, chasing the
selected tab's live bounds while the tab widths themselves animate — the bounce
is the physics of one object arriving, not an animation per item. **Pages
slide** between tabs in the direction of travel and push/pop into shows, and
each page renders from the destination it was created for, never live state —
on the way back from a show the live selection is already null. A saveable
state holder keeps the library's scroll across the round trip.

**Play buttons change shape** with state — a full pill at rest, a squarer tile
while playing — in the player, the compact header and the mini player.

**The bubble's progress** now traces the scalloped outline instead of a circle
around it: one function builds both the clip and the ring, so they can't
disagree, and the progress is a `PathMeasure` segment of that curve from the
top.

**Themes are System / Light / Dark plus a dynamic-color switch.** The old
five-way list made artwork color and brightness mutually exclusive. Stored
values migrate: Lights out → Dark; Show colors → System with dynamic on.

## The Cider pass

A deliberate step away from strict Glass conformance toward Material 3
Expressive, modelled on Cider's Android client.

**The player's background is the artwork, blurred.** Every earlier version
*derived* a background from the cover — a palette, then a four-swatch mesh, then
clamps on the mesh — and every one had a seam somewhere, because a derived color
approximates the picture and approximations disagree with the original along
some edge. A blurred copy of the same image can't disagree with it. The sharp
cover dissolves (an alpha mask, as always) into an enlarged, blurred version of
itself, so the transition is the picture losing focus rather than one surface
meeting another. `MeshBackdrop` is gone for good.

The blur layer decodes at 96px. It throws away all detail anyway, and on Android
versions without `RenderEffect`, where `Modifier.blur` is a no-op, a tiny bitmap
upscaled with bilinear filtering still reads as soft rather than as a sharp
duplicate of the cover.

**The wavy scrubber** (`WavySlider`) is Material 3 Expressive's wavy *progress*
indicator, not an audio waveform — the wave carries no information about the
audio, only that it is playing. It animates while playing and calms to a flat
line on pause, which makes it the clearest play-state signal on the screen. The
mini player carries a smaller one.

**Connected button groups** (`ButtonGroup`) for the transport and for a show's
actions. Only the group's outer ends get the large radius; inner corners are
tight, so the parts read as one control. Pressing squashes a segment rather than
rippling it, which survives on artwork where a ripple would vanish. The
transport has five parts rather than Cider's three: a podcast needs the 30-second
jumps more than track skipping, so those take the full-size slots.

**The mini player collapses to a bubble while you scroll.** Driven by a
`NestedScrollConnection` at the root, so every screen gets it without knowing:
scroll down and the card folds toward its bottom-right as a cookie-shaped cover
grows in beside the tab bar; scroll up and it comes back. The bubble's scalloped
edge turns slowly while playing and stops where it is on pause — only the
outline turns, the cover stays upright.

**Episode rows are cards**, with an equaliser on whichever one is playing.

**The swipe-to-queue bug, and why it happened.** The first version painted its
"Add to Up Next" label on every row permanently and tinted the whole list.
`SwipeToDismissBox` always composes its background and relies on the row above
it being opaque to hide it. My rows were transparent. Two fixes: the background
now draws nothing unless a swipe is in progress, and rows are opaque cards — the
played-row fade applies to their contents only, since fading the card would let
the reveal show through. The reveal is an icon in a tile, not a sentence.

**The show page's doubled title.** The bar faded the show's name in as the
header's scrolled away, and with no opaque surface under the bar both were
visible at once, one sliding under the other. It's a floating back button now;
the header already names the show.

## Transcript follow-along

`EpisodeExtras` has parsed SRT and VTT since the chapters work and nothing ever
surfaced it. The info sheet is now tabbed — Notes, Chapters, Transcript — and
only shows a tab with something behind it.

The transcript follows the audio: the line being spoken is the only one at full
strength, its neighbours recede to 42%, and the list keeps itself two lines
ahead so you never hunt for your place. **Tapping a line seeks to it**, which is
the real point — it turns a transcript from a wall of text into the navigation
surface an hour-long episode most lacks.

The spoken line also fills left to right, using a gradient brush on the text
itself. That is honest here rather than decorative: SRT and VTT give a start and
end per cue, so the fill is measured elapsed time within the line, not a guess
at individual words.

Coverage is the catch. `<podcast:transcript>` is rarer than chapters, so most
episodes will show no Transcript tab at all. That's the correct outcome — a tab
that opens onto "no transcript" is a worse answer than no tab.

## Swipe a row to queue it

Right to play next, left to add to the end, on both the show page and Latest.
The long-press sheet stays; this is the shortcut for the two things people
actually pick from it, and queueing was the only frequent action on a row that
cost two taps and a read.

The threshold is a quarter of the row rather than the default half — this is a
flick, not a dismissal — and the row springs back afterwards, because nothing is
being removed from the list and it has to say so.

## Haptics

`ui/Haptics.kt`. Call sites name what a touch *meant* — `Haptic.Resume`,
`Haptic.SkipForward`, `Haptic.Select` — and the module decides the shape of the
buzz and what the motor under it can reproduce. Without that split every call
site ends up hardcoding a vibration pattern and they drift apart.

Patterns are built from the three genuinely short primitives (tick, low tick,
click). The platform also offers rises, falls and thuds, and all of those run
80–500ms — long enough that a two-beat pattern would still be vibrating after
the screen had finished responding. The longest pattern here is three beats
inside ~50ms.

Three tiers, resolved once per process: primitive **composition** on API 30+
where supported, **amplitude waveform** where the motor has volume control, and
a **plain waveform** otherwise — where pulses are stretched to be felt at all
and three-beat patterns drop to their outer two rather than becoming a rumble.

Placed on: play/pause (rising vs. falling pair), ±30s (accelerating triplet and
its reverse), tab changes, pill toggles, scrub release, mini-player expand, and
the pull-to-search threshold arming. Not on drag movement — a tick per pixel is
a rattle, not feedback.

## Loading skeletons

`ui/Skeletons.kt`. Content-shaped gray placeholders with a highlight sweeping
across, laid out to the same metrics as the real rows.

A spinner says "something is happening". A skeleton says what is about to be
there and how much of it, and removes the layout shift a spinner guarantees,
because the spinner occupies nothing like the space the content will.

The sweep is read inside the draw block, not the composable body — a screenful
of these would otherwise recompose every animation frame when all any of them
needs is a fresh gradient. Placeholder widths are ragged so a run of rows reads
as text rather than as a barcode.

## Coming from another podcast app

Settings → Subscriptions → Import / export OPML, or share the file straight to
GlassCast from the other app's share sheet — the manifest accepts `ACTION_SEND`
and `ACTION_VIEW`, and a shared file opens the import sheet directly.

For AntennaPod specifically: Settings → Import/Export → OPML export.

**Subscriptions transfer; play positions don't.** OPML has no field for them and
nobody has agreed on an extension. The alternative is reading AntennaPod's
SQLite database out of its own backup, which does carry positions but means
depending on the private schema of an app that's free to change it in any
release. Not a trade worth making for a one-time migration.

Two details that matter in practice: the file picker filters on `*/*` rather
than an OPML MIME type, because exporters label these files as `text/xml`,
`application/xml`, `application/octet-stream` or nothing at all and a strict
filter grays out the very file you came to pick; and the import runs
sequentially with visible progress, because firing a few hundred feed fetches in
parallel from a phone gets you rate-limited by the larger hosts.

## Four themes, not three

`System`, `Light`, `Dark`, `Lights out`. Dark is a gray (`#17171B` base); Lights
out is true black for OLED.

`System` resolves to Dark, never Lights out. True black is something a
particular kind of person seeks out for their panel — it isn't a default anyone
should be handed by their phone's night setting.

Because "dark" now has two flavours, nothing may hardcode a near-black ground.
The artwork wash, the drifting field and the splash all read
`MaterialTheme.colorScheme.background` instead; a fixed `#0A0A0C` would show as
a seam against gray and as a slightly-wrong black against true black.

## The color system: hue carries identity, the theme pins lightness

The palette used to preserve each cover's own lightness and clamp it into a wide
band. That is unpredictable by construction — a dark cover gives a legible page,
a bright one gives highlighter red, and every failing cover earns another clamp
bolted onto the last.

Inverted now. **Hue and saturation carry the identity; lightness is pinned by
the theme.** Dark lands near L 0.13, light near L 0.91, always. The page is
legible by construction, and the hue is what makes it this show's page — which
is the part anyone recognizes.

`ArtworkColors` is a finished set — `background`, `wash`, `elevated`, `accent`,
`content`, `contentVariant`, `divider` — rather than raw swatches each caller
re-interprets. Two details inside it:

- **`wash` is the flat mean of the artwork's bottom 18%**, which is what a blur
  wide enough to lose the picture actually leaves at that edge. A mean and not a
  quantised swatch, deliberately: a blur has no notion of which color is
  important, so the page has to match what the blur *produced*, not what the
  picture is about. Starting the page from that color is what removes the seam
  under the artwork.
- **The accent is scored by saturation × √population.** The square root is the
  whole trick: without it a cover that's four-fifths black sky accents in black,
  and with the area term gone entirely a single vivid pixel wins.

`contentVariant` sits at 0.80 alpha, not the usual 0.60 — a tint is a colored
ground, not black, so secondary text needs more of the content color to
separate from it.

## Two surfaces, two grounds

The show page and the player deliberately do **not** share a ground, and the
reason is worth stating because it looks like an inconsistency.

The **show page** is a list. It has to stay legible next to the rest of the app,
so it gets the theme-pinned gradient: `wash` settling into `background`, quiet
by construction.

The **player** is a poster. It fills the screen, carries no list, and its entire
job is to look like the thing you're listening to. Pinning its lightness
flattened exactly what made it worth looking at, so it keeps the mesh: four
distinct swatches over the cover's own dominant color, at its own lightness.

`ArtworkColors` therefore carries both sets — `background`/`wash`/`content` for
the app's grounds, `mesh`/`meshBase`/`onMesh`/`meshAccent` for the player. Same
extraction, two clamps, because they answer different questions.

## Matching the cover, not deriving from it

The mesh used to clamp every color into a 0.28–0.58 lightness band and multiply
saturation. That's borrowed from a music player, where covers are photographs
with no single flat field and a rich mesh is the goal. Podcast covers are not
photographs — they're illustrations sitting on one flat background color, and
that color is how you recognize the show.

Measured against real covers, the clamp was doing this:

| Cover background | Mesh it produced |
|---|---|
| `#FCF5FD` pale lavender | `#20158F` deep indigo |
| `#3F6067` teal | `#63614C` olive |
| `#6E251F` red | `#3F3832` brown-gray |

Derived from the artwork, and visibly nothing to do with it.

The mesh now leads with the cover's **dominant** swatch, kept close to true —
saturation nudged 1.12×, lightness only pulled back from the extremes that can't
hold text at all. Blob alpha dropped from 0.85 to 0.55 so the base stays
recognizable through them.

What used to be handled by forcing everything dark is handled by picking the
text color instead: dark type on a pale cover, light type on a deep one, with
the scrim following suit and the status-bar icons with it. Forcing dark was the
other half of the problem — it darkened the very color the page was trying to
match, to protect white text that never had to be white.

## The player ignores the theme

Not an oversight. Its background is the artwork's colors, not the theme's, and
those land wherever the cover lands — so "is this readable" cannot be answered
by a setting. The fix both references use is the same: a dimmed floor under the
mesh, a black scrim over it, white type on top, in every theme. It is also why
dark mode looked right and light mode looked broken; the surface was never
really light, so light-mode text colors were being asked to work over a dark
picture.

Everything else still follows the theme. Only the player opts out, and it
restores the status-bar icon colors on the way out.

## Why the mesh was one flat color

Three causes, all showing as the same symptom:

- **Radii were measured against the screen's longest side.** At 0.46 of the
  height every blob covered the whole screen, and four screen-sized gradients at
  high alpha average to a single tone. They are sized against the layer now, and
  the layer is scaled 1.3× so the blur's clamped edges fall off-screen.
- **The colors weren't actually different.** `vibrantSwatch` and friends are a
  convenience over the full swatch set, and on a dark or desaturated cover most
  come back null — so three "different" field colors were often one color
  three times. The whole swatch list is read by population now, near-duplicates
  dropped by hue and lightness distance, and any shortfall derived from the art
  itself rather than borrowed from the brand.
- **Saturation and lightness weren't clamped.** Pastel covers gave a mesh you
  couldn't see; near-black ones gave a mesh you couldn't read over.

The blobs also no longer orbit forever. Re-blurring a full-screen layer at
120Hz for as long as the player is open was the most expensive thing in the
app, for motion nobody watches. They drift on open and on episode change, then
settle. The resting frame is identical.

Only the mesh gets the saturation tuning. The show-page wash and the mini player
sit on the app's own ground, where boosting them would overshoot a wash that
already works.

## A bug the reference found for me

The scrubber had two `pointerInput` blocks — `detectTapGestures` and
`detectHorizontalDragGestures`. That does not work: the drag detector claims the
pointer on the way down, and a tap has no drag to report. **Tapping the bar to
seek silently did nothing.** Dragging worked, which is why neither of us noticed
for several rounds.

It is one `awaitEachGesture` loop now, taking the position from the initial
down — so a tap seeks, and a drag starts from where your finger actually landed
rather than waiting for the first movement.

## The seam, third time — and the general rule

The show page grew the line back, at the header's bottom edge. Same family as
the player's, and the diagnosis generalises:

> An overlay gradient runs 0→1 over **its own composable**. The page ground runs
> 0→1 over **the screen**. Two gradients in different coordinate spaces cannot
> agree at their shared boundary, whatever colors you give them.

The backdrop now masks the blurred image's own alpha with `BlendMode.DstIn` and
paints no color at all, so the page ground is the single color source across
the whole screen and there is nothing left to disagree. The player learned this
first; the show page had to learn it separately because the fade there was
introduced as a color, not as a mask.

The rule, stated once: **when something sits over a live or full-screen
background, erase it — never tint it.**

## The seam, and why an overlay can never fix it

Twice I tried to blend the player's artwork into the mesh by painting a
gradient *over* it — first ending on the theme's ground color, then on the
mesh's dark base. Both produced a hard line exactly one screen-width down the
page, which is the artwork's bottom edge.

An overlay cannot work here. Whatever color it ends on at the artwork's last
row is a fixed color, and the mesh one pixel below it is a live, moving,
artwork-derived one. They will never agree.

The artwork's own alpha is masked instead: `CompositingStrategy.Offscreen`, then
a vertical gradient drawn with `BlendMode.DstIn`. The gradient supplies alpha
and no color, the artwork's bottom rows become genuinely transparent, and the
mesh shows through both halves because it was always the only thing back there.

Also worth naming: resource qualifiers have a fixed order, and `night` comes
before density. `mipmap-hdpi-night` is silently not a valid folder;
`mipmap-night-hdpi` is.

## A fix worth remembering

The player's artwork used to fade to the *ground color* at its foot. The mesh
behind it is a saturated, moving field, so the artwork ended in flat `#F7F7F9`
while the page underneath carried on in olive or purple — and the two met as a
hard line at exactly the artwork's bottom edge, one screen-width down.

The dissolve now fades to transparent and lets the same mesh carry through both
halves. Anything drawn over an artwork-derived background has to dissolve into
*nothing*, never into a color, or it will find an edge to disagree on.

The mesh itself was also averaging into a single flat wash: blob radii at 0.72
of the screen with a 44dp blur on top leaves no variation to see. Radii are now
0.46 with less blur, which is what makes it read as a field rather than a tint.

## The glass pass

The chrome is frosted and floating rather than welded to the screen edges: a
tab bar and a mini player as two stacked rounded panels sharing a gutter and a
corner radius, with the page scrolling underneath them, and a blur ramp under
the status bar instead of a background that switches on at some scroll offset.

Real backdrop blur in Compose needs `dev.chrisbanes.haze` (Apache 2.0) — doing
it by hand with `RenderEffect` doesn't compose with scrolling content. Only the
scrolling page is tagged as the haze source; tagging the chrome too would have
the panels blurring each other.

Three things that make it read as glass rather than as a smudge:

- **A hairline top edge** on every panel. A real pane catches light along its
  edge; without it a blurred panel just looks out of focus.
- **The top ramp stops short of full blur.** A blur has nothing to sample past
  the top of its own layer, so pushed all the way it becomes a band of flat
  material color spreading down the page — the exact artifact it was added to
  remove. It is also keyed to the *page's* color, which on a show page means
  the artwork wash, not the theme background.
- **The mini player's artwork tint stays low.** The point of glass is seeing the
  page move underneath; a heavy tint turns the panel back into a solid.

Below API 31 there is no `RenderEffect` and Haze falls back to a translucent
scrim. That's expected — the layout is identical either way.

The BitChord source that inspired this is GPL-3.0. Nothing was copied from it;
what's here is the design language and the choice of blur library, both of which
are free to take. Keep it that way unless GlassCast goes GPL.

## Pull-to-search, and the gesture it spends

Pulling down past the top of the Library jumps to Search, with the keyboard
already up. On Android that gesture normally means refresh, and it is free here
only because refreshing is an explicit button in the top bar.

**That has to stay true.** Adding pull-to-refresh later would put two meanings
on one gesture and the wrong one would win. If background refresh via
`WorkManager` lands as planned, manual refresh matters less anyway.

Two details make it feel deliberate rather than twitchy: dragged distance is
halved, so the indicator lags the finger and reads as weighted; and the trigger
fires on release rather than on crossing the threshold, so an overscrolled
fling can't launch Search by accident.

The morph from the concept is approximated rather than literal — the resting
pill scrolls away while a collapsed search button fades into the bar on the same
scroll fraction the large title already animates on. A true continuous morph
would need the pill to live outside the list and be positioned by hand against
scroll offset, which is a lot of fragile arithmetic for a difference you'd have
to be looking for.

The pill sits *below* the title, not above it as in the reference. The page
should say what it is before it offers to leave.

## Divergences from GlassBook

- **No Coil.** `ImageStore` already downloads, disk-caches, downsamples and
  memoises artwork, and Palette needs a real `Bitmap` anyway. Running two image
  pipelines side by side would have meant the caching rule holding in one of
  them and not the other. Coil is not in the dependency list.
- **`PlayerConnection` is a new file** in `player/`. A `MediaSessionService`
  needs a `MediaController` on the UI side; leaving the future-and-listener
  plumbing in composables would have spread Media3 across the whole `ui`
  package.
- **`GlassCastRoot.kt` and `Components.kt`** are additions — routing state and
  the `Pill` / formatting helpers respectively.
- **No Room yet**, per "no Room in the first milestone". It contradicts the note
  about introducing Room the moment episodes are involved, so: the JSON blob is
  a milestone-1 convenience and `FeedStore`'s surface is entirely StateFlows and
  suspend functions, so milestone 2 can swap the guts without the UI noticing.
  Do not let the blob survive past milestone 2 — a few thousand episodes
  serialized on every position write will not hold.
- **Chapters are cut, not deferred.** They were pulled from the player at your
  call — an episode isn't a book, and there's no second scope to switch to, so
  the scrubber is simply the episode and the label under it is gone.
- **Directory search pulled forward from milestone 2.** `data/PodcastSearch.kt`
  is an interface with an iTunes implementation, scoped to the device locale's
  store so local shows actually appear. Podcast Index slots in beside it when
  you want credentials.
- **Queue pulled forward from milestone 3.** It is ExoPlayer's playlist, not a
  list kept beside it — a parallel list only gives you two things that can
  disagree about what's playing. `data/QueueStore.kt` mirrors the guids and the
  current index so the queue survives a cold start; positions already live
  per-episode in `FeedStore`.
- **No manual reordering.** The drag handle was cut, and with it reordering —
  swipe-to-remove and tap-to-play cover the cases that come up, and the queue is
  short by design.
- **The queue has no history.** The playing item is always index 0 and
  everything after it is Up Next; anything you move away from is dropped. That
  invariant is what stops finishing an episode from walking backwards into one
  you already heard.
- **Browsing is not subscribing** — but playing is. A show opened from search
  is fetched into transient state and never touches the library until Add is
  tapped. Hitting play does subscribe, because the queue and the resume position
  need a row in the store to live in.
- **Chapters are best-effort.** `<podcast:chapters>` first, then timestamps
  parsed out of the show notes. Nothing in the iTunes API carries chapters or
  transcripts, and only a minority of feeds publish either, so the notes parser
  does most of the real work. `EpisodeExtras` can also read SRT/VTT transcripts
  where a feed declares one; nothing surfaces them yet.
- **Sleep timer pulled forward from milestone 4.** It runs in the service
  (lesson 6) and talks to the UI through `player/SleepTimer.kt`, a process-wide
  object rather than session commands — the service is in the app's own process.
  If it ever moves to `:playback`, that object becomes a `SessionCommand` pair
  and nothing else changes.
- `ShakeDetector.kt` is not present; it belongs to milestone 4.

## Hard-won lessons, where each one landed

1. `LocalContentColor` — `PlayerScreen` draws into a bare `Box`, so it wraps in
   a `CompositionLocalProvider`. Everything else sits under a `Surface`.
2. Both schemes fully specified — `ui/theme/Theme.kt`, including every
   `surfaceContainer*` step and `onSurfaceVariant`.
3. Image decoding — `data/ImageStore.kt`. Keyed by URL *and* size, synchronous
   peek for the first frame, and the cache re-checked after decode so two
   racing coroutines return the same instance.
4. Duration — `PlaybackService.reconcileDuration()` on `STATE_READY`, written
   back to the store. `formatRemaining` returns `--:--` rather than `-0:00` when
   duration is still zero.
5. Artwork on the `MediaItem` — `PlayerConnection.mediaItemFor()`, with
   `refreshMetadata()` doing the capture-replace-seek dance.
6. Sleep timer in the service — `PlaybackService.startSleepTicker()`, fading
   volume over the last 15 seconds. End-of-episode waits for `STATE_ENDED`
   rather than computing a deadline, which gets playback speed right for free.
7. `LinearProgressIndicator` — `drawStopIndicator = {}` and `gapSize = 0.dp` at
   both call sites.
8. Refresh rate — `MainActivity.requestHighRefreshRate()`, in `onCreate` and
   `onResume`, noting the system may refuse.

## Worth knowing before you run it

- `usesCleartextTraffic` is on. A meaningful share of podcast enclosures are
  still plain HTTP and would otherwise fail silently.
- Notification permission is requested on first launch. Playback works without
  it; you just lose the notification controls.
- Feeds vary wildly. If one parses badly, the URL is the thing to send back.

## Next

Next: OPML import and export, `WorkManager` background refresh, downloads with
`SimpleCache`, per-show settings — and Room, before the JSON blob starts
hurting. The queue restoring a few thousand episodes off a JSON blob at launch
is the first place that will show.
