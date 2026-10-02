# GlassCast

GlassCast is a podcast app for Android phones and Google TV. I started it after
moving over from an iPhone, because I couldn't find an Android podcast app I
actually liked using.

You can download the latest APK from the
[releases page](https://github.com/anixitea/GlassCast-for-Android/releases/latest).
The app checks that page for updates itself, so you only need to install it once.

## What it does

The player takes its colors from the cover of whatever you're listening to, and
so do the mini player and the tab bar.

You can queue episodes by swiping them, read show notes, jump between chapters,
follow along with transcripts, change the speed, skip silences, boost quiet
voices, and set a sleep timer. Shaking the phone restarts the timer.

Discover suggests shows based on the ones you already follow. That's worked out
on your phone. There's no account, and nothing about what you listen to is sent
anywhere.

It can also tell you when new episodes come out, download episodes for offline
listening, cast to a Chromecast or Google TV, sync with a gPodder or Nextcloud
server, and import or export OPML if you're bringing your shows from another app.

The same APK installs on Google TV, where it has its own interface made for the
remote.

It's free, and there are no ads.

## Android Auto

GlassCast works in Android Auto. Since it's installed from GitHub and not the
Play Store, Android Auto hides it until you allow unknown sources. Open Android
Auto's settings, tap the version number about ten times to unlock developer
settings, then open the menu in the top corner, choose Developer settings, and
turn on Unknown sources.

## Xiaomi and HyperOS

HyperOS stops apps from running in the background unless you tell it not to.
For new-episode notifications, open GlassCast's App info, turn on Autostart,
and set Battery saver to No restrictions.

## Building it

Open the folder in Android Studio and run it. It needs Android 8.0 or newer.
My notes on how it's put together, and why some things are done the way they
are, are in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Credits

The typeface is [Figtree](https://github.com/erikdkennedy/figtree) by Erik
Kennedy, used under the SIL Open Font License (see FIGTREE-OFL.txt).
