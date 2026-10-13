# GlassCast — developer notes

## 1.8

**Version 1.8 (versionCode 20).** The released 1.7 was built from 619148b:
Sign in with Google switched off, and none of the fifth and sixth rounds'
fixes. 1.8 is everything since, with sign-in on.

**The crash reports.** Both GitHub issues are the same hang. #1 (Pixel 9 Pro,
Android 17, on 1.7) has the RenderThread inside
`SkBaseShadowTessellator::computeConcaveShadow` with the main thread waiting
on it, "phone library": the pull-to-refresh cookie casting its shadow from a
concave outline, retriangulated every frame. That is the fifth round's fix
(the shadow now comes from a circle inside the scallops), which the 1.7 build
didn't have. #2 (Redmi, Android 13, on 1.6) predates RenderThread stacks in
reports but shows the same main-thread wait. A search of every `shadow(`
found no other concave shape: the update banner, the toast, the color
picker's swatch and the refresh circle are all convex now, and nothing sets
`shadowElevation`.

**Sign-in.** `GOOGLE_SIGN_IN = true`. Google's side on October 13: In
production, External, `drive.appdata` only (non-sensitive, so the user cap
doesn't apply), one Android client "GlassCast release" (release key only, so
debug builds can't sign in). Branding is under review with a logo: privacy
policy and branding guidelines passed, the homepage check failed with "not
registered to you" because Google looked less than 24 hours after the Search
Console verification (October 12, 15:14 UTC). A scheduled check on October 14
looks again and drafts a reply to Google's Trust and Safety thread if one
arrives. Until branding passes, sign-in works, but the consent screen may not
show GlassCast's name and logo.

**License.** GPL-3.0, in `LICENSE` and on GitHub, with a section in the
README.

## 1.7, seventh round: Sign in with Google is on, with a site of its own

Still 1.7 (versionCode 19).

**Why it could go public after all.** The console's "100 user cap" only
counts users of an app asking for sensitive or restricted scopes Google
hasn't approved (the console says so under Audience once it's published).
GlassCast asks for `drive.appdata` alone, which is non-sensitive, so in
production there is no cap and no review, and since every library lives in
its user's own Drive it costs nothing at any number of users. What
publishing needed was a home page and a privacy policy on a domain
registered in Branding.

**The site.** `site/` in this project, published by GitHub Pages from the
public repo `anixitea/anixitea.github.io` (main, root): `/` lists the apps,
`/glasscast/` is the home page, `/glasscast/privacy/` the privacy policy,
plus `style.css`, `favicon.png`, `glasscast/icon.webp` (the launcher art,
WebP, 3 KB) and `google1dad3bd2eb45c4c8.html`, Search Console's ownership
file (keep it, or the verification lapses). It had to be a user site (a repo
named exactly `anixitea.github.io`): Google wants the top private domain
verified, and `github.io` is a public suffix, so the domain is
`anixitea.github.io`, and only a user site serves files at its root. The
privacy policy lists exactly what the app contacts (podcast hosts, Apple's
iTunes Search API for search and charts, GitHub for updates, gPodder or
Nextcloud if set up, cast devices, Google if signed in), the scope, what the
library file holds, the Limited Use statement, and how to sign out and delete
the Drive copy. Contact is the GitHub issues page, not an email address.
Uploaded through GitHub's web upload, folder by folder (this session can't
push to his GitHub); to change the site later, upload the changed files the
same way or push from his Mac.

**Google's side.** Search Console: URL-prefix property `https://anixitea.github.io/`,
verified by the HTML file. Branding: home page and privacy policy links,
authorized domain `anixitea.github.io`, no logo (a logo would need a review).
Audience: published, In production. Brand verification (so the consent
screen shows GlassCast's name) was refused on the first try with "not
registered to you": Google asks for 24 hours between verifying ownership and
retrying. Until it passes, sign-in works but the consent screen doesn't show
the app's name. Retry from Branding > the (i) panel > Verify branding.

**In the app.** `GOOGLE_SIGN_IN = true`; the account card is back. README and
the 1.7 notes say Sign in with Google again, and README links the privacy
policy.

## 1.7, sixth round: a calmer tab bar, switches that show off

Still 1.7 (versionCode 19).

**The tab bar shook as it settled.** Seen in Jonathan's recording, frame by
frame. Three causes, one per symptom:
- *The whole row wobbled sideways.* Every tab's width follows `pill`, the
  traveling index, and `barSpring` was damped at 0.62: the pill ran about 8%
  past its tab and back, and every icon in the row moved with it. Now 0.86
  (stiffness 460), which barely overshoots.
- *The label flickered between cuts* ("Dow…", "Downl…", "Downlo…"). The label
  was measured to the tab's width of that frame, so it was re-ellipsized as
  the width moved. It's now measured to the room it has once settled
  (`labelRoom`, written by the bar as it measures), and only revealed and
  faded by the moving width.
- *"Downloads" stayed cut short at rest.* The selected tab got a fixed 1.4
  tabs' worth of extra width. Now each tab's extra is its own label's width
  (TabItem reports the icon alone as its min intrinsic width and icon plus
  label as its max), scaled by one factor for all tabs only if the bar is
  too narrow for the longest label.
- Also: the tab being left keeps its label and fades it as it narrows (it
  used to vanish the frame another tab was tapped), and the press squash is
  0.92 on a 0.75 spring (was 0.86 on 0.5, which bounced back past full size).

**Off switches were empty ovals.** Material's off switch draws its thumb and
rim in the theme's `outline`, and the light theme's outline is LightGround3,
the same gray as the off track. `glassSwitchColors()` (Components.kt) gives
off a gray thumb in a rimmed track in both themes, and on the accent with a
white thumb (dark on a pale custom accent). Used by Settings' switches and
the sleep sheet's; the show page's and the player panel's already set their
own.

## 1.7, fifth round: the hang on pull-to-refresh, the sideways player's backdrop and panel

Still 1.7 (versionCode 19).

**The hang (two reports from Jonathan's OPPO OPD2515, Android 17).** Both
were not-responding kills on "phone library", each waiting 5 s on a drag
(`action=MOVE`). The main thread was in `nSyncAndDrawFrame`, waiting for the
RenderThread, the same shape as 1.6.1's Redmi report, and this time the
report carried the RenderThread: running, 7 to 9 s of CPU, inside
`SkBaseShadowTessellator::computeConcaveShadow` ← `SkShadowTessellator::MakeSpot`
← `EndReorderBarrierDrawable::drawShadow`. That's a view shadow cast from a
concave outline. The only one in the app was the pull-to-refresh
indicator: `.shadow(10.dp, CookieShape())`, a 217-point scalloped outline,
inside a layer that turns and grows on every frame of the pull and spins
through the refresh. A concave shadow has to be triangulated on the CPU, and
the turning layer meant it was redone every frame. Fixed by casting the
shadow from a plain circle tucked inside the scallops' dips
(`CookieShadowInset`, about 3dp); the cookie is drawn over it, so it looks
the same. Nothing else casts a shadow from a path (checked every `.shadow`
and `shadowElevation`; the TV's cookie is a clip only). The 1.6.1 Redmi
report (a tap waiting 8 s behind the RenderThread) may well have been this,
since the indicator is on the library, Latest and Discover; its report had no
RenderThread stack to say.

**The color block down the right of the sideways player.** The backdrop is
the cover and its mirror image, blurred as one (`softenedMirrorSideways`),
sized to twice the cover's width. A phone on its side is more than twice as
wide as it is tall (about 880 by 405dp here), so the mirror ended about 70dp
short of the right edge and the plain blur under it showed as a strip of
another color. The mirror now reaches the far edge: both builders take a
`reach` (the image's length over the cover's, 2 to 4) and stretch the
reflection to fill it. Portrait phones stay at 2, so nothing changes there;
a very tall portrait screen gets the same fix.

**The open panel on a sideways phone.** The header above the open panel
(title, three buttons, wave) took 104dp under a 40dp gap, so on a phone's
~405dp the panel opened only halfway up, and the sleep timer's chips and
Shake to restart were below its edge. In short landscape the header now sits
4dp under the status bar (the dismiss handle fades as the panel rises; a
drag down closes the panel first anyway) with the wave 52dp down rather
than 60, and the panel starts 80dp below it rather than 104: about 60dp
more panel. The timer tab also scrolls now, as Speed & sound already did, so
nothing in a tab can be out of reach at any height.

## 1.7, fourth round: Sign in with Google switched off for now

Still 1.7 (versionCode 19).

**The Cloud setup is done, but it can't go public yet.** Project
`glasscast-511413` (Jonathan's account): Google Drive API enabled; Google
Auth Platform configured (app name GlassCast, External, support and contact
email his Gmail, the API Services User Data Policy agreed); Data access has
`drive.appdata` (listed as non-sensitive); an Android client "GlassCast
release" for `com.glasscast.app` with the release key's SHA-1
`69:C6:1B:BA:A6:37:63:4A:C3:E0:1B:DA:29:93:14:09:AA:D1:5F:E0` (read from the
built APK's signature block; there's no debug client). Publishing is the
step left, and the console keeps Publish app disabled until Branding has an
application home page and a privacy policy link, hosted on a domain
registered under Authorized domains (and, for brand verification, one he
owns, checked in Search Console). GlassCast has no site, so the app stays in
Testing, where only listed test users can sign in.

**So the feature is off.** Jonathan's call: build a site later, hide sign-in
for now, and say in the update notes that it's coming. `GOOGLE_SIGN_IN`
(top of `data/GoogleAccount.kt`) is `false`: the Settings account card isn't
shown, the app doesn't schedule syncs on open and close, the daily job skips
its sync, and an account no longer asks for the background job. The code,
the dependency and the strings stay. The backup file is unaffected.

**To turn it on:** a home page and a privacy policy (GitHub Pages under his
GitHub account is the free way, at a `*.github.io` address verified in
Search Console), their links and domain in Branding, Publish app in Audience,
then `GOOGLE_SIGN_IN = true`. To try it before that, add the Gmail under
Audience > Test users (access lapses after 7 days there). Debug and fast
builds need a second Android client with the debug key's SHA-1.

## 1.7, third round: the tab bar, a custom color, landscape, Sign in with Google

Still 1.7 (versionCode 19).

**The tab bar stays dark again.** The page-following colors were meant for
the mini player only. The mini player still goes pale over a dark page and
dark over a light one, crossfading; the bar is dark as it always was. It
follows the card only with Settings > Appearance > Adaptive tab bar on (off
by default). Both still read their colors while drawing.

**The selected tab's icon no longer jumps out of the pill.** Seen in
Jonathan's recording (the mini player becoming the bubble, the bar
narrowing to make room): the label faded in and out on its own timing (an
`AnimatedVisibility` fade, full width from the first frame to the last) while
the tab's width moved on the bar's spring. For a few frames the icon and
label were wider than the tab, the row couldn't center them, and the icon was
pushed out past the pill's left edge, then snapped back when the label left.
`TabItem` is now a hand-written layout: the label takes only the share of its
width the bar's own numbers give the tab (the reveal is the same value that
gives the tab its extra width), it's ellipsized to the room there is, and the
pair is centered every frame. Only the selected tab shows a label; tabs the
pill passes over still widen for it but stay icon-only.

**Custom color.** Settings > Appearance > Custom color: a swatch and a
switch; the row opens a sheet with three sliders (hue, saturation,
brightness), each track painted with the colors it moves through, a preview
and the hex value, and Use this color. It becomes the app's accent the way
the cover's does with Dynamic color (clamped for legibility on the page); the
two are exclusive, turning one on turns the other off.

**The player on a phone held sideways.** It used the portrait layout, which
needs about 400dp above the panel strip; a phone sideways has about 390 in
all, so the title went up under the status bar, the controls crowded, and the
cover was only the blur behind them (Jonathan's screenshot). It now gets the
tablet's landscape layout, packed for the height: the cover fills the height
on the left and dissolves into the controls' column; the volume row goes
(the phone's buttons are right there), the transport is 64dp instead of 76,
the gaps tighten, so title to transport is 196dp and centers between the
status bar and the panel strip; the column keeps clear of a camera cutout or
three-button bar on the right. The player now closes sideways as one sheet
on phones too.

**Sign in with Google (`data/GoogleAccount.kt`, `ui/BackupSettings.kt`).**
Jonathan wanted an account rather than a Drive backup: sign in, and the
library, progress and bookmarks are kept for that account and follow it to
other phones. Asked where the data should live, he chose the person's own
Google account over a Firebase backend: no server, and the README's promise
that nothing is sent anywhere holds.
- *The card at the top of Settings*: signed out, "Sign in with Google";
  signed in, the account's photo, name and email, and when it last synced
  (or what went wrong). Tapping it opens the account sheet: Sync now, Sign
  out. Name, email and photo come from Drive's `about` call, under the same
  single permission (`drive.appdata`).
- *Storage*: one library document in the account's app folder (hidden, only
  GlassCast can read it): the backup document plus each show's follow time,
  the unfollows, and deleted bookmark ids.
- *Merging*, both ways, every sync. Shows: the latest of following and
  unfollowing wins by time (a journal of both, kept from
  `FeedStore.onSubscriptionChanged`; the times written after a sync are the
  account's, not the moment this phone caught up). Marks and places:
  whichever is further along (so "mark unplayed" doesn't travel; played
  wins). Bookmarks: by id, the newer memo wins (`editedAt`, new), deletions
  carry everywhere (`BookmarkStore.onRemoved`). Settings and the queue: taken
  from the account once, on a phone's first sign-in.
- *When*: on signing in; when the app opens and when it's left
  (`SyncWorker`, so it finishes after a swipe away; at most every two
  minutes); once a day with the background job; Sync now.
- An empty phone with no account copy writes nothing.
- Sign out revokes GlassCast's access at Google and forgets the account and
  its journal; the library stays on the phone and the account copy stays for
  next time.
- The backup file (Save a backup file, Restore a backup file) stays, for
  moving phones without an account.
- Type-checked against the real play-services-auth 21.4.0 (with
  auth-base 18.0.10 and auth-api-phone 18.0.2), staged from the Mac once
  Gradle had fetched them. The button
  says "Sign in with Google" in GlassCast's own style; Google's branding
  rules ask for its official button artwork with that phrase, which can be
  dropped in if the app ever goes to the Play Store.

**"Code 8".** Jonathan's first try failed with 8, Play services'
INTERNAL_ERROR. Nothing in the app can fix that: Google answers it before
any of GlassCast's own calls run. The usual cause is that the Google Cloud
setup below isn't done yet or doesn't match the installed build (its package
and signing key's SHA-1), or that the consent screen is still in Testing
without the account as a test user. The error now says "Couldn't sign in with
Google (code 8)"; code 10 says the setup is missing outright.

**Google sign-in setup (once, in Google Cloud).**
1. console.cloud.google.com: create a project (GlassCast).
2. APIs and services > Library: enable the **Google Drive API** (it's the
   storage behind the account).
3. Google Auth Platform (the OAuth consent screen): app name GlassCast, a
   support email, audience External. Under Data access add the scope
   `https://www.googleapis.com/auth/drive.appdata`. Then **Publish** the app
   (In production): with only non-sensitive scopes there's no review, and
   while it's left in Testing only listed test users can sign in and their
   access lapses after 7 days.
4. Clients > Create client > **Android**: package `com.glasscast.app`, and
   the SHA-1 of the key that signed the installed build. Release:
   `keytool -list -v -keystore "<the .jks in ~/Documents/App Key>" -alias <alias>`.
   A second Android client with the debug key's SHA-1 for debug and fast
   builds: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`.
5. Nothing goes in the app: Google matches the package and signing key.
   Changes can take a few minutes to reach phones.

## 1.7, second round: chrome that follows the page, backups, update images

Still 1.7 (versionCode 19); 1.7 hadn't gone out yet.

**The chrome contrasts with the page, and fades between versions.** Before,
the mini player swapped dark and pale by the theme, and the tab bar was dark
always, so in dark mode a pale card sat on a dark bar that sank into the
page. Now both follow the page that's actually showing: a show or a preview
by its own ground (from its cover), every other tab by the app's. Over a
light page they're dark (as before); over a dark page both go pale
(`chromeButton` for the card, the new `chromeBarPale` a step lighter for the
bar, `ChromeInk` on them). `chromeMix` animates 0 to 1 over 450 ms and every
chrome color is a blend along it, so they crossfade rather than snap, as
Cider's do. The bar reads its colors as functions while drawing
(`tint: () -> Color`, `content: () -> Color`); only the tab items recompose
during a fade, never the root. Grounds are theme-pinned today, so in practice
light mode keeps the dark chrome and dark mode gets the pale one; the rule is
written against the page so a page with its own ground just works.

**Buy Me a Coffee** in Settings > About (buymeacoffee.com/theglassmaker),
opening the browser.

**Images in update notes.** GitHub writes a dragged-in image as an HTML tag
(`<img … src="https://github.com/user-attachments/assets/…">`), older ones
as Markdown `![alt](…)`, sometimes wrapped in a link; all three showed as
code. `noteBlocks` splits the notes into text and pictures; pictures load
through the artwork pipeline at the sheet's width (cached, downsampled), keep
their shape, and round like the box; one that won't load leaves nothing.
Leftover tags (`<p>`, `<br>`), links (shown as their text) and entities are
cleaned out of the text. Only https images load. Tested on a sample body.

**Backup and restore (`data/Backup.kt`, `ui/BackupSettings.kt`).** Settings >
Backup: Google Drive, Save a backup file, Restore a backup file.
- *The document*: shows (with when each was followed and last played),
  every episode's played mark and place, the queue, bookmarks, settings
  (per-show order and auto-download, hidden Discover shows). Not downloads,
  not the gPodder password.
- *Restoring merges.* Missing shows are followed one at a time (the OPML
  import's path, with progress under the rows); marks and places only move
  forward (`FeedStore.applyProgress`: played wins, the later place wins);
  bookmarks are added beside existing ones, never twice; the queue only if
  nothing is loaded (`PlayerConnection.loadQueue`); auto-download restarts
  from now so nothing published meanwhile pours in. It asks first
  (`ConfirmSheet`, now with a non-destructive accent version).
- *Files* go through the system's Save and Open dialogs
  ("GlassCast backup 2026-10-12.json"); no storage permission.

**Google Drive backups** came next, and were replaced in the third round by
signing in with Google (below): same storage, one account instead of a
backup per phone.

## 1.7 — GlassBook's menus, bookmarks, the car, auto-download, widget, shortcuts

Version 1.7 (versionCode 19).

**Menus and placement, from the GlassBook notes.**
- *Sheets stop below the camera.* Every bottom sheet goes through
  `GlassSheet` (`ui/GlassSheet.kt`): fully expanded or closed, never half,
  and its content capped at the screen height less the status bar and 96dp,
  so a long sheet ends under the cutout instead of behind it. All eleven
  `ModalBottomSheet`s were converted.
- *Confirmations are sheets, not dialogs.* `ConfirmSheet`: the cover, the
  title, a line of detail, one red pill for the destructive action (haptic
  ToggleOff) and Cancel. It replaces the stock dialogs for Discover's Not
  interested, Unfollow, and deleting downloads (one or many, with the size).
- *Settings is the sixth tab*, where GlassBook keeps it; the button on the
  Podcasts title is gone. `SettingsScreen` is grouped cards with a colored
  badge on each row (Apple's palette): Appearance (theme tiles drawn as
  little phones in the icon's gradient, dynamic color), Playback (skip 15/30,
  headphone buttons Skip/Next), Library (show order, OPML, gPodder),
  Notifications, Discover (hidden shows), About (updates with the version,
  the crash report when there is one). The old `SettingsSheet` is deleted.
- *The blur follows the chrome.* `BottomGlassFade` takes its height as a
  lambda read in layout (tab bar, plus the mini player while it's a card), so
  it tracks the collapse without recomposing.
- *The chrome takes its own touches.* The tab bar and the mini-player card
  consume pointer input, so a tap on them never falls through to a row
  underneath.
- *The mini player's card clips what's inside it* in every phase of the
  morph (a reused `Path`, no allocation per frame).
- *Big titles fade by distance* (`rememberTitleCollapse`, 100dp of travel)
  rather than by item index.

**Not taken from GlassBook, and why.**
- Only the tapped cover flies (5a) and the bar drawn over flying covers (2c):
  GlassCast removed page-to-page cover flights in 1.2, because they hung if
  you scrolled mid-flight. Only the mini player to full player flight is left.
- Add-sheet tiles (4c): GlassCast's add sheet is a feed address field, not a
  list of rows.
- No pull-down that opens a sheet (4d): already true; pulling down only
  refreshes.
- Shake to restart is already only in the Timer; Skip silence and Boost
  voices stay in the player's panel only.

**The player.** Tapping the episode title opens its show (closing the
player, Library tab). **Bookmarks** are a panel tab between Info and Speed:
"Bookmark 12:34" adds one at the playhead and opens its memo; each row is a
time pill (tap to seek), the memo (inline edit) and the date; swipe left to
delete. Marked as dots on the wave scrubber. `data/Bookmarks.kt` keeps them
by episode guid in `files/bookmarks.json`, written behind under a mutex
through a temp file; unfollowing a show keeps them.

**Headphone buttons.** Most earbuds send next and previous (double and
triple tap), which in a podcast drop the episode you're in. With Settings >
Playback > Headphone buttons on Skip (the default) they skip back and
forward by the skip setting instead: `AutoLibrary.onMediaButtonEvent`, which
Media3 1.5.1 calls before its own handling. Media button intents that come
through the service (the notification's own buttons on Android 12 and
older, the widget) arrive as the media notification controller and keep
their meaning. A one-button wired headset's double click is still next:
Media3 turns a double play/pause into next after the callback.

**Skip buttons in the car (`player/CarButtons.kt`).** Android Auto draws its
controls from the platform session's custom actions, and since Android 13
the phone's media notification draws from the same place. The earlier lesson
(back/forward custom buttons came out reversed on HyperOS) still holds, so
the buttons are added only while a car is connected:
- on: `onConnect` from Android Auto (`isAutoCompanionController`) or a car
  running Android (`isAutomotiveController`) sets the session's custom
  layout (Media3's `ICON_SKIP_BACK_15/30`, `ICON_SKIP_FORWARD_15/30`) and
  reserves the previous and next slots (session extras), so the skip
  buttons sit beside next and previous rather than taking a slot over;
- off: `PlaybackService.onUnbind` for the media browser interface, i.e. the
  last browser (the car) has gone. It returns true so `onRebind` follows and
  the next drive turns them on again. Media3 never reports a browser
  disconnecting, and drops idle car controllers after five minutes, so its
  own callbacks can't be the off switch.
- `onCustomCommand` answers both with `seekBack` / `seekForward` through
  `SkipPlayer`; every controller gets the two commands, the notification
  controller included (the platform session lists only buttons that
  controller may use). The labels follow the skip setting live.
Untested in a car. Things to watch: where Auto places the two buttons, and
the phone's notification while connected (it shows them too).

**Auto-download, per show (`data/AutoDownload.kt`).** A switch in the show's
info sheet ("Download new episodes", Wi-Fi only); the (i) button now shows
on every followed show. `Settings.autoDownload` keeps feed URL → when it was
turned on. After every refresh (`FeedStore.onRefreshed`, in the app and in
the background job) and only on an unmetered network, each such show's three
newest episodes are checked: published since it was turned on (less 12
hours, for feeds that list an episode late), not played. `Downloads.
startAutomatic` starts each one at most once ever (`auto_tried`, the last
500), so deleting one keeps it deleted, and holds the download to unmetered
networks (`setAllowedOverMetered(false)`). The address is resolved when the
download starts, on Wi-Fi, so a host's short-lived link is fresh. Nothing is
deleted automatically. The background job now runs when notifications are on
or any show auto-downloads (`Settings.backgroundRefreshWanted`), and waits
for the downloads to be queued before it returns. Download polling slows to
every 15 s while downloads only wait for a network.

**Home-screen widget (`widget/NowPlayingWidget.kt`).** 4×1, resizable:
cover, episode, show, back / play-pause / forward, on a card filtered to the
cover's dark muted color. The service updates it on episode changes and
play/pause (`playWhenReady`, so buffering doesn't flicker it); with the
service gone it shows the saved queue's first episode. Buttons are media
button intents to the service, as Media3's notification sends them: play
starts it as a foreground service (from a stopped app Media3 resumes the
saved queue through `onPlaybackResumption`, and playback makes it
foreground); back and forward use a plain service start, so they never
start a service that would have nothing to play. Tapping the card opens the
player.

**Launcher shortcuts (`Shortcuts.kt`).** Resume, Latest, Search, Downloads.
Dynamic, set each time the app opens, so the labels come from `tr()` (an XML
shortcut can only use string resources, and GlassCast's text isn't in
resources). Adaptive icons: the app icon's purple behind a white Material
glyph. Resume plays the saved queue as soon as it's back in the player
(`PlayerConnection.resume`) and opens the player.

**Smaller.**
- Downloads introduce themselves with the installed version (was "1.4").
- TV Settings: the explanatory paragraph at the bottom is gone (it also
  claimed the TV doesn't sync, which gPodder changed).
- Tooling: the type-check now runs here whenever the session is linked to
  the Mac (see tools/typecheck/README.md); `gen_r.py` reads layout ids;
  `owncheck` accepts a same-package class without an import.

## 1.6.1 — the first field report

Version 1.6.1 (versionCode 18).

**What came in.** A user's report from 1.6 on a Xiaomi 2201117TG (Redmi
Note 11, Snapdragon 680, Android 13): a not-responding kill, "Input
dispatching timed out … Waited 8001ms for MotionEvent(action=DOWN)", closed
by the user from the dialog ("user request after error"). The app was already
open and in use (the main thread had used about 15 s of CPU), so this isn't
the pad's start-up hang. Separately, Jonathan hears of crashes on Pixels; no
report from one yet.

**What the trace says.** The main thread wasn't running GlassCast code. It
was inside `ViewRootImpl.draw` → `HardwareRenderer.nSyncAndDrawFrame`, waiting
in `DrawFrameTask::drawFrame` for the RenderThread: the thread that sends
each frame to the GPU had not finished, so the main thread couldn't hand it
the next one, and a tap waited behind that for 8 seconds. The cause is on the
RenderThread (or the GPU, its driver, or the display's buffers), and 1.6's
report only sent the main thread. So the report couldn't say why.

**Changed.**
- Not-responding reports now carry the threads that matter: main,
  `RenderThread` and the `hwui` workers, plus up to four threads running
  GlassCast's own code, each capped (`CrashLog.keyThreads`). Checked on a
  sample dump laid out like a real one, compiled against android-35.
- Which screen was up: `CrashLog.noteState` stores a few words ("phone
  library player playing") with the process through
  `ActivityManager.setProcessStateSummary` (Android 11+, in memory, 128
  bytes, updated only when the screen changes), and the report prints it as
  "showing:". Both roots note tab, show page, preview, player, playing, and
  on the phone whether the launch mark is still up.

**Suspects for a stalled RenderThread, for when the next report has its
stack.** Not changed yet, per the ground rule.
- *Haze's progressive blur.* The glass bars (`ui/Glass.kt`,
  `HazeProgressive.verticalGradient`) on Android 13+ run Haze 1.3.1's own
  runtime shader: for every pixel under the bar, every frame the page moves,
  a loop of up to 150 taps sampling twice each, in two passes. That's the
  heaviest GPU work in the app and lands hardest on a phone like the Redmi.
  Haze 1.4.0 changed that shader from `half` to `float` because `half`
  overflowed on some GPUs (Haze #520/#528, Samsung, seen as clipping). If the
  RenderThread stack shows GPU waits (`kgsl`/`gsl` fences, `GrGLGpu`,
  `SkiaOpenGLPipeline`) with the library or a show page up, options in
  order: a plain (non-progressive) blur on weaker GPUs, then Haze's later
  versions (check their Compose requirement first).
- *Full-screen offscreen layers* (the player cover's dissolve, the show
  page backdrop), on a 1080×2400 panel.
- *Display buffers*: a RenderThread stuck in `dequeueBuffer` points at the
  display side, not at our drawing. The exit's importance was 325 (top
  activity, device sleeping), so the screen went off around the hang.

## 1.6 (in progress) — the OPPO Pad mini, and what came over from GlassPlay

Version 1.6 (versionCode 17).

On Jonathan's OPPO Pad mini (8.8", 2520×1680, 60–144 Hz, Snapdragon 8 Gen 5,
ColorOS 16, possibly a China-market unit) GlassCast often crashes or sticks on
the opening logo. His Xiaomi 17 Pro Max never has. The work below follows
his brief: get evidence first, then fix the suspects the code itself shows.

**The cause, as far as the pad has shown it.** No crash report or log (adb
isn't an option for Jonathan). His answers:

- *It sticks on the launcher icon on a plain field*, Android's own launch
  screen in 1.5. So the main thread is busy or blocked before GlassCast's
  first frame. The splash replaying (2b) would show the purple mark instead,
  so it isn't what sticks, though it was still wrong.
- *The Play Store works*, so Play services is installed and running.

What 1.5's main thread did before the first frame, checked file by file:
open six small SharedPreferences, check every finished download's file
through `getExternalFilesDir`, and call `CastContext.getSharedInstance(this)`
synchronously, in `GlassCastApp.onCreate` and again in the playback
service's `onCreate`. Everything else (the feed library, palettes, images)
was already off it. The Cast call is the only one that waits on another
process: it binds into Play services and loads the Cast module from it. On
the Xiaomi's global ROM Play services is a privileged system app that's
always up. On a China-market ColorOS unit it's an add-on that ColorOS
manages like any other app, freezing it and controlling when one app may
wake another. When Play services wasn't running at the moment GlassCast
cold-started, the main thread waited on it with nothing drawn; long enough,
and Android or ColorOS kills the app, which looks like a crash. That fits
"often", not "always", and fits the phone never doing it. It's the most
likely cause, not a confirmed one: the confirmation is ten clean cold starts
on 1.6, or, if it still sticks, the exit record's not-responding stack
(below), which 1.6's first launch can also read for 1.5's hangs.

In 1.6 nothing waits on another process before the first frame: both Cast
calls are asynchronous, and the download check runs on IO. If it still
sticks, 1.6's launch screen is a plain field with no icon.

### Evidence

**Crash log** (`data/CrashLog`, from GlassPlay). The uncaught-exception
handler writes the stack, version, device, Android version and thread to
`files/crash/last.txt` as the process dies. Settings > About shows a "Crash
report … Send" row only when there's a report; Send opens the share sheet
with the text and then deletes it. Nothing leaves the device otherwise.

Two changes from GlassPlay's version:

- *Hangs.* A main thread that's stuck never throws, so the handler never
  runs. The system kills the app for not responding (or ColorOS kills it
  quietly), and from the outside that looks like a crash, which is exactly
  the pad's symptom. Android 11+ records why each of the app's processes
  ended (`ActivityManager.getHistoricalProcessExitReasons`), and for a
  not-responding kill it keeps the stack of every thread. Each launch reads
  that record off the main thread and keeps the unusual endings in
  `files/crash/exits.txt`: crashes, native crashes, not responding, failed
  starts, excessive resource use, and kills or low-memory kills while the app
  was on screen (in the background those are routine on ColorOS). Leaving on
  purpose, force stops, updates and permission changes don't count. For the
  two newest not-responding kills the report includes the main thread's
  stack only (the whole dump is far too long to share). The record survives
  app updates, so the first launch of 1.6 can report how 1.5 died; those
  lines say "(previous version)". Sending the report sets a cut-off, so the
  same exits aren't reported twice.
- *Installed in `attachBaseContext`*, not as the first line of `onCreate`.
  The app's content providers (WorkManager's start-up, the profile
  installer, the artwork provider) are created between the two, and a
  failure there would otherwise go unrecorded.

`CrashLog` and the StrictMode setup type-check against API 35's android.jar
(compiled with kotlinc in the workspace, the only parts that could be).

**StrictMode, debug builds only** (`FLAG_DEBUGGABLE`, so not `fast` or
release). Thread policy: disk reads, disk writes, network, custom slow calls,
logged under `GlassCastStrict` (a penalty listener on Android 9+, plain
`penaltyLog` before). Also set in `attachBaseContext`, so it sees the
providers' main-thread work too. `adb logcat -s GlassCastStrict` during a cold
start lists what the main thread did before the first frame.

### Cast on the main thread (brief 2a, the top suspect)

`GlassCastApp.initCast()` called `CastContext.getSharedInstance(this)` in
`onCreate`, before the first frame, and `PlaybackService.setUpCast` called it
again when the service started. That call is synchronous: it loads the Cast
module out of Google Play services on the main thread. `runCatching` stopped
it from crashing where Play services is missing; it didn't stop it from
blocking while Play services tried. The slow case is Play services that's
present but switched off or out of date, which is common on China-market
ColorOS units (Jonathan's other OPPO devices are China ROMs). A main thread
stuck there is "stuck on the logo", and a long enough stall is a
not-responding kill or, once the playback service has been asked to go
foreground, a "did not call startForeground" crash.

Now `player/CastSupport` (from GlassPlay) starts Cast with the asynchronous
overload, `CastContext.getSharedInstance(context, Dispatchers.IO.asExecutor())`,
which returns a `Task<CastContext>`; the module loads on the IO pool and the
Task's listeners come back on the main thread. Every step is guarded, so a
device without Cast never calls back and the phone player stays in charge.
The playback service starts it (once per process) and builds its
`CastPlayer` in the callback, if the service is still alive. The device sheet
also asks for it, in case it opens first. The output-switcher params are now
built on the router's existing ones (`MediaRouterParams.Builder(current)`)
instead of replacing them. `initCast()` is gone.

Verifying the overload: Google Maven is blocked from the workspace, so the
21.5.0 jar couldn't be opened. Google's own Cast sample (CastVideos-android)
calls `getSharedInstance(context, executor).result` against
cast-framework 21.4.0, and GlassPlay compiles the same call against 21.5.0.
`getRouterParams()` and `MediaRouterParams.Builder(MediaRouterParams)` are in
AndroidX's public mediarouter API.

### The launch mark replaying (brief 2b)

`showSplash` was a plain `remember` in `GlassCastRoot`, and the real UI only
composed after it flipped, inside a `Crossfade`. So any activity recreation
the manifest didn't cover played the mark again from the start: a second, the
420ms crossfade, and only then the first real frame's work. Several in a row
look like being stuck on the logo. MainActivity's `configChanges` lacked:

- `smallestScreenSize`: split screen, ColorOS floating windows
- `density`: a display-size or app-scaling change
- `keyboard`, `navigation`: a keyboard cover attaching

All four are in now. Compose follows each of them without a recreation
(`AndroidComposeView.onConfigurationChanged` rebuilds its density). Locale
is still not listed on purpose: `tr()` relies on the recreation.

The mark plays once per process (`GlassCastApp.splashPlayed`, set as it
starts), so a recreation goes straight to the app. And the app now composes
underneath it from the first frame: `SplashScreen` is the last child of the
root `Box`, takes every touch until it's gone, and fades out over 420ms
after its second. The update check, the notification-permission prompt and an
OPML arriving from a share all wait for `opened` (when the fade starts)
instead of the splash being removed.

### The launch screen (brief 2c)

Android 12+ draws its own launch screen until the first frame. GlassCast had
no `values-v31` theme, so that was the launcher icon on the system's color.
Now `values-v31` and `values-night-v31` set
`windowSplashScreenBackground` to the ground and
`windowSplashScreenAnimatedIcon` to transparent: a plain field, then the mark
fades in on the same field. It also makes the two stages easy to tell apart:
a blank field is Android still waiting for the first frame, the mark is the
app drawing.

While matching the grounds: `ground_dark` (the window background in dark
mode) was `#0A0A0C`, but the in-app dark ground is `DarkGround0`, `#17171B`.
That was a small flash on every dark cold start. It's `#17171B` now. The
launch screen follows the system's dark setting, not the app's, so with the
app forced to the other theme there's still one change from the launch
screen to the mark. The bar icons follow the in-app theme and the mark's
field is the in-app ground, so GlassPlay's `SplashBars` flag isn't needed.

### Other startup work (brief 2d)

- `Downloads` checked `File.exists` for every finished download, through
  `getExternalFilesDir`, in its constructor, on the main thread. That check
  now runs on IO; missing files are dropped when it returns.
- `BackgroundRefresh.apply` (WorkManager, which opens its database) and the
  crash log's exit scan run on an IO coroutine from `onCreate`. gPodder's
  launch sync shares the same scope.
- Left as they are until StrictMode says otherwise: the SharedPreferences
  opened by `Settings`, `QueueStore`, `Downloads`, `AppUpdater`, gPodder's
  status and `FeedStore`'s migration check.

### Not changed yet, waiting on evidence

- *Memory (2e).* Only if a report shows `OutOfMemoryError`: then cap
  `ImageStore`'s cache at an eighth of the heap on large screens and decode
  at the tile's own size.
- *144 Hz (2f).* Measure first with `adb shell dumpsys gfxinfo
  com.glasscast.app framestats` on the `fast` build, then try Haze off, the
  refresh-rate request off, and offscreen layers only while needed, one at a
  time. Keep only what measurably helps.
- *ColorOS (2g).* Look for `ForegroundServiceStartNotAllowedException` and
  `RemoteServiceException` in a report. Asked Jonathan to allow background
  activity and auto launch; the README now says how.

### Remembering what was playing

Jonathan noticed GlassPlay still shows the last song after the app is closed
and reopened, and GlassCast doesn't. GlassCast did have the code for it
(`QueueStore` mirrors the queue's guids, `restorePersistedQueue` puts them
back), but it never worked after a cold start. On connecting,
`PlayerConnection` called `syncQueue()` first, which saved the new service's
empty playlist over the saved queue, and only then `restorePersistedQueue()`,
which read back the empty list it had just written. The same empty write
broke Android Auto and headset resumption, which read `QueueStore` too.

Now:

- The saved queue is read once, when `PlayerConnection` is built, before
  anything can write to it. It becomes the current episode and Up Next right
  away, so the mini player is back as soon as the library has loaded (with
  the saved position on its ring), without waiting for the service.
- On connecting to an empty player, the queue goes back into it first.
  `syncQueue` won't save an empty playlist while that's pending, and the
  ticker and `pullState` leave the remembered progress alone while the
  player is empty. Restored, not resumed: nothing plays until you press play.
- A controller whose service has gone is replaced. When the app was swiped
  away while paused, the service stopped itself but the process could live
  on, holding a dead controller; `connect()` saw "already connected" and did
  nothing, so play did nothing either. `MediaController.Listener.onDisconnected`
  now drops it, and `connect()` also checks `isConnected`.

Playing an episode from a show you don't follow subscribes to it first (that
was already so), so every queued episode is in the library to restore from.

### Skip buttons: 15 or 30 seconds

Settings > Skip buttons (phone and TV): 15 or 30 seconds, 30 by default
(`Settings.skipSeconds`). The player's back and forward buttons use it, on
the phone and the TV, with matching icons and descriptions ("Back {0}
seconds").

- *Icons.* Material has 5, 10 and 30 second skip icons but no 15.
  `ic_replay_15` and `ic_forward_15` are built from Material's own shapes:
  the 10s icon's arrow and 1, with the 5s icon's 5 moved into the 0's place
  (centered on it, same baseline), so they match `Replay30`/`Forward30`
  exactly. `ui/SkipIcons.kt` picks the pair.
- *Everything outside the app.* ExoPlayer and CastPlayer take their skip
  amounts when they're built, so a setting can't change them. The
  notification, a headset's rewind and fast-forward keys (most headsets
  send next and previous instead) and Android Auto skip through the
  session's `seekBack`/`seekForward`, so the session now gets a `SkipPlayer`
  (a `ForwardingPlayer`) around whichever player is active, which answers
  those (and the increments it reports) from the setting each time. Checked
  against Media3 1.5.1: `ForwardingPlayer`'s `seekBack`, `seekForward` and
  the increment getters are overridable, and it forwards
  `canAdvertiseSession`, which `MediaSession` requires.
- The TV scrubber's left and right stay at 10 seconds; that's scrubbing, not
  the skip buttons.

### TV, from GlassPlay

- **Focus without the bounce.** `TvFocusIndicationNode` animated with
  `spring(DampingRatioLowBouncy, StiffnessMediumLow)`, about half a second
  to settle on every press, so with the D-pad held the whole screen looked
  like slow motion. Now `tween(140 in, 110 out, FastOutSlowInEasing)`. The
  ring and the lift are unchanged.
- **Scrolling that keeps its speed.** Every TV list gets `TvBringIntoView`,
  provided once at the root of `TvRoot`: scroll only as far as needed to
  keep a 48dp margin, with `TvScrollSpring` (`spring(dampingRatio = 1f,
  stiffness = 650f)`), so a new focus mid-scroll carries the speed on. Before,
  every list but the show page had leanback's default, which pins focus 30%
  in, jumps rows and slices the first cards. The show page keeps
  `TvHoldStillSpec` (holds while the header has focus, 10–85% band) and now
  scrolls with the same spring; before, it set only the distance.
  `BringIntoViewSpec` and `LocalBringIntoViewSpec` are still
  `@ExperimentalFoundationApi` in foundation 1.7 (checked against 1.7.3's
  source); `scrollAnimationSpec` is a member there.
- **The backdrop holds still while the D-pad is in use.** A key-down in the
  root's `onPreviewKeyEvent` sets `navigating` (only when it changes, so a
  held key recomposes nothing) and restarts a job that clears it 900ms after
  the last press. `LocalTvBackdropMoving provides (!playerOpen &&
  !navigating)`.
- **Level bars** on the playing episode's row (GlassPlay's `TvLevelBars`):
  not ported. Asked Jonathan first.

### Workflow

- `tools/typecheck/`: a real type-check with the Compose plugin against the
  actual jars. Needs the jars from the Mac's Gradle cache through a linked
  session (`tools/typecheck/README.md` lists them, Cast in `extra/`). Not
  run yet: this session wasn't linked to the Mac. The harness was
  smoke-tested: with the wrong jars it stops and says so, and it only
  reports clean when the app's classes were actually produced.
- `tools/l10n.py` regenerates `ui/L10n.kt`'s tables from
  `docs/translations.txt`; `--check` lists `tr()` text with no translation.
  Its first run found the two had drifted: translations.txt had straight
  closing quotes in eleven German strings (`„Suchen"`) where L10n.kt had the
  right ones (`„Suchen“`). Fixed in translations.txt; the tables are now
  generated from it, so table order follows the file.
- Release notes live in `docs/release-notes/<version>.md`, in the README's
  voice; the text is the GitHub release body.
- Packaging is `git archive` of a committed tree, so build output never
  rides along.

## 1.5 — tablets, TV focus, Downloads

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

## 1.4 — Google TV fixes, the living backdrop, the palette, Android Auto, Boost voices

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
