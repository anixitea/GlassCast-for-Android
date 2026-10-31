# GlassCast

![GlassCast: every show, in its own color](docs/images/banner.jpg)

GlassCast is a podcast app for Android phones and Google TV. I started it after
moving over from an iPhone, because I couldn't find an Android podcast app I
actually liked using.

You can download the latest APK from the
[releases page](https://github.com/anixitea/GlassCast-for-Android/releases/latest).
The app checks that page for updates itself, so you only need to install it once.
If you use Obtainium, you can add it there instead.

[<img src="docs/images/obtainium.png" alt="Get it on Obtainium" height="48">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.glasscast.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fanixitea%2FGlassCast-for-Android%22%2C%22author%22%3A%22anixitea%22%2C%22name%22%3A%22GlassCast%22%7D)

There's more about it, in English and Korean, at
[anixitea.github.io/glasscast](https://anixitea.github.io/glasscast/).

<p align="center">
  <img src="docs/images/player.webp" width="24%" alt="The player, in the colors of the episode's cover">
  <img src="docs/images/latest.webp" width="24%" alt="Latest, with new episodes from every show you follow">
  <img src="docs/images/show.webp" width="24%" alt="A show's page, tinted by its cover">
  <img src="docs/images/speed.webp" width="24%" alt="Speed and sound, with Skip silence and Boost voices">
</p>

## What it does

The player takes its colors from the cover of whatever you're listening to, and
so do the mini player and the tab bar.

There are three looks to choose from in Settings. Besides the original one,
there's Sea Glass, where the pages are paper, the titles are set in a serif,
and the buttons are pieces of frosted glass in the colors of your shows.

![Sea Glass, the second look](docs/images/sea-glass-screens.jpg)

And there's Glass. The tab bar, the mini player, the player's controls and the
buttons on your pages are made of glass that bends what's behind it, and the
pages take on Apple's layout. You can have it frosted, or liquid, which is
clearer. When you scroll, the mini player shrinks into a bubble beside the tab
bar.

![Glass, the third look](docs/images/glass-screens.jpg)

You can also pick your own app icon in Settings. There are fifteen, in three
sets that match the looks.

Your library opens with Continue Listening, the episodes you're part way
through with the time you have left, and the clock button above it shows
everything you've listened to, day by day.

You can queue episodes by swiping them, read show notes, jump between chapters,
follow along with transcripts, change the speed, skip silences, boost quiet
voices, and set a sleep timer. Shaking the phone restarts the timer. You can
bookmark a moment in an episode and write a note on it.

Discover suggests shows based on the ones you already follow. That's worked out
on your phone. You don't need an account, and nothing about what you listen to
is sent to me. The [privacy policy](https://anixitea.github.io/glasscast/privacy/)
has the details.

It can also tell you when new episodes come out, download episodes for offline
listening (or download a show's new episodes by itself on Wi-Fi), cast to a
Chromecast or Google TV, sync with a gPodder or Nextcloud server, and import or
export OPML if you're bringing your shows from another app. You can sign in
with Google so your shows, progress and bookmarks follow you to every phone
(they're kept in a hidden folder in your own Google Drive, not on a server of
mine), or save them to a backup file instead. There's a home screen widget, and
long-pressing the icon gets you straight to Resume, Latest, Search or Downloads.

The same APK installs on Google TV, where it has its own interface made for the
remote, in all three looks. You can sign in with Google there too, by scanning
a QR code with your phone, and the TV picks up where your phone left off.

It's free, and there are no ads. If you'd like to support it, you can
[buy me a coffee](https://buymeacoffee.com/theglassmaker).

## Android Auto

GlassCast works in Android Auto. In the car there are four tabs: Home, with
Continue Listening and Up Next; Library, with your shows and your history;
Latest; and Downloads. Episodes show how far in you are, and you can add one
to Up Next or mark it played from the list. What you play in the car is still
there when you open the app afterwards.

Since it's installed from GitHub and not the Play Store, Android Auto hides it
until you allow unknown sources. Open Android
Auto's settings, tap the version number about ten times to unlock developer
settings, then open the menu in the top corner, choose Developer settings, and
turn on Unknown sources.

## Xiaomi and HyperOS

HyperOS stops apps from running in the background unless you tell it not to.
For new-episode notifications, open GlassCast's App info, turn on Autostart,
and set Battery saver to No restrictions.

## OPPO and ColorOS

ColorOS closes background apps even more eagerly. Open GlassCast's App info,
go to Battery usage, and turn on Allow background activity and Allow auto
launch.

## If it crashes

The next time you open GlassCast, Settings has a Crash report row under About.
Tapping Send lets you share the report however you like, by email or in a
[GitHub issue](https://github.com/anixitea/GlassCast-for-Android/issues/new?template=crash-report.yml).
It stays on your device unless you send it, and it's deleted once you do.

## Building it

Open the folder in Android Studio and run it. It needs Android 8.0 or newer.
My notes on how it's put together, and why some things are done the way they
are, are in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## License

GlassCast is free software under the GNU General Public License v3.0. You can
use it, change it and share it, as long as what you share stays under the same
license with its source available. The full text is in [LICENSE](LICENSE).

## Credits

The typeface is [Figtree](https://github.com/erikdkennedy/figtree) by Erik
Kennedy, used under the SIL Open Font License (see FIGTREE-OFL.txt).

The Glass look is set in [Inter](https://github.com/rsms/inter) by Rasmus
Andersson, also under the SIL Open Font License (see INTER-OFL.txt). The way
its glass bends what's behind it is adapted from
[Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android), used
under the MIT License (see LIQUID-GLASS-ANDROID-LICENSE.txt).

The Sea Glass look sets its titles in
[Newsreader](https://github.com/productiontype/Newsreader) by Production Type,
also under the SIL Open Font License (see NEWSREADER-OFL.txt). Its glass is adapted
from the [Sea Glass](https://github.com/mttmcknn/sea-glass) library for
Compose, used under the Apache License 2.0 (see SEA-GLASS-LICENSE.txt).
