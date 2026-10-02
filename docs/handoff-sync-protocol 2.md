# GlassCast handoff & sync protocol — v1 (draft)

The contract between GlassCast on Android and GlassCast for Mac. Written before
the Mac app exists so both sides are built against one definition instead of
one side being reverse-engineered from the other.

## What it does

1. **Handoff** — move what's playing from one device to the other, at the same
   second, from the phone's device list ("Play on → Jonathan's MacBook").
2. **Remote control** — the phone drives the Mac's player (and vice versa)
   without moving the audio.
3. **Library sync** — subscriptions, played state and positions agree on both.

Local network only in v1. Syncing away from home needs a server both apps can
reach, and that's a separate decision.

## Discovery

- The Mac advertises a Bonjour service: `_glasscast._tcp`.
  - TXT: `name` (device name), `proto` (`1`), `app` (app version).
- Android finds it with NSD (`NsdManager.discoverServices("_glasscast._tcp", …)`).
- Discovered Macs appear in the phone's **Play on** sheet beside Cast devices.

## Transport

- The **Mac hosts**; the phone connects. The Mac is usually on, plugged in and
  on one network — the better server. (Network.framework `NWListener`.)
- A single WebSocket per connection; every message is one JSON object with a
  `type` field.

## Pairing

First connection only. The Mac shows a 6-digit code; the phone enters it.
Both derive and store a shared token. Every later `hello` carries the token; an
unknown token is refused, so nothing on the Wi-Fi can drive your player.

## Identity

An episode is `(feedUrl, guid)`. Feed URLs are compared lower-cased.

## Messages

```json
{ "type": "hello", "proto": 1, "device": "Pixel of Jonathan", "token": "…" }

{ "type": "state", "nowPlaying": {
    "feedUrl": "…", "guid": "…", "positionMs": 1834000,
    "durationMs": 5460000, "playing": true, "speed": 1.2 } }

{ "type": "handoff", "feedUrl": "…", "guid": "…",
  "positionMs": 1834000, "speed": 1.2, "play": true }

{ "type": "control", "action": "play" | "pause" | "seekBy" | "seekTo" | "next",
  "valueMs": 30000 }

{ "type": "library.snapshot",
  "feeds": [ { "url": "…", "title": "…" } ],
  "episodes": [ { "feedUrl": "…", "guid": "…", "positionMs": 0,
                  "durationMs": 0, "played": false, "updatedAt": 1790000000000 } ] }

{ "type": "library.delta", "episodes": [ … same shape, changed rows only … ] }
```

## Rules

- **Handoff:** the sender pauses *after* the receiver acknowledges with a
  `state` showing the episode playing — never before, so a failed handoff
  doesn't leave silence.
- **Conflicts:** per episode, the newer `updatedAt` wins. Marking played is
  treated like any other change — no special stickiness, so "mark unplayed"
  syncs too.
- **Subscriptions:** a feed present on one side and absent on the other is
  offered, not silently added — the Mac shows "3 shows from your phone".
- **Versioning:** `proto` in `hello`; a peer that doesn't understand the
  version says so and closes, rather than guessing.
