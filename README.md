# 4096

A 2048-style puzzle game for Android: 4x4, 5x5 and 6x6 grids, five modes (Classic, Zen,
Time attack, Limited moves, Versus AI), generated audio, no ads, no analytics, no network.

**Status: version 1.0.0.** Everything in the build brief exists, the optimised release build
has been run on a phone by the owner, and releases are signed with the owner's key and
published on the repository's Releases page.

| Milestone | State |
|---|---|
| 1. Rules engine and tests | done |
| 2. Playable Classic on all grids | done |
| 3. Saving and high scores | done |
| 4. Zen, Time attack, Limited moves | done |
| 5. Versus AI | done |
| 6. Audio | done |
| 7. Quality levels and performance tuning | done, measured on four phones (see Performance) |
| 8. Polish and release build | done |

## Known issues

- **The 60 fps limit is uneven on phones that will not switch their screen to 60 Hz.** The
  limit first asks the phone for a 60 Hz mode. Where the phone agrees (measured: Xiaomi 17T)
  it works cleanly. Where it does not (measured: Galaxy S21 FE) the board draws on every
  other refresh instead, which halves the work but leaves up to a quarter of frames, at
  High quality, arriving one refresh (about 8 ms) late. If motion looks slightly uneven with
  the limit on, turn it off or lower the quality level. Details under Performance.
- **120 fps has only been measured on fast phones.** 60 and 90 fps held on mid-range and
  entry-level phones; no mid-range phone with a 120 Hz screen was available.
- **The audio has only been judged by the owner's ear on one phone**, and the in-memory
  music player has not been tried on Android 8 to 12.
- **The baseline profile is written by hand**, not recorded on a device, and its effect on
  start-up time has not been measured.

## Building

Requires JDK 17 or newer and the Android SDK (platform 36). The Gradle wrapper downloads
everything else.

```
./gradlew :core:test :app:testDebugUnitTest   # rules tests and app tests
./gradlew :app:assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease                # optimised with R8, about 2.8 MB
```

Every push to `main` runs the tests and builds both APKs in GitHub Actions
(`.github/workflows/ci.yml`); they are attached to the run as artifacts.

## Releases and signing

Android only installs an update over an app signed with the same key, so releases need one
permanent key. It is never stored in this repository.

- The key lives in two repository secrets: `KEYSTORE_BASE64` (the keystore file, base64
  encoded) and `KEYSTORE_PASSWORD`. The build expects the key under the alias `key4096`.
- With the secrets set, every Actions run signs the release APK with that key, and pushing
  a tag such as `v1.0.0` publishes it on the repository's Releases page.
- Without them, the release APK is signed with a throwaway key and named `...-TEST-KEY.apk`,
  and the tag job refuses to publish.
- To sign on your own machine, set `KEYSTORE_FILE` and `KEYSTORE_PASSWORD` in the
  environment before `./gradlew :app:assembleRelease`.

**Keep a copy of the keystore file and its password somewhere safe.** If they are lost, no
future version can update an installed copy; players would have to uninstall first and lose
their saved games and scores.

The release build is shrunk and optimised with R8 and carries a baseline profile
(`app/src/main/baseline-prof.txt`) that lets Android compile the game's own code ahead of
time.

## Architecture

- `core/` is pure Kotlin with no Android dependencies. It holds the rules (`Rules`), a
  reproducible random generator (`SeededRandom`) and one board in play (`Game`, with undo and
  save/restore). Boards are flat `IntArray`s of tile exponents, and the move path allocates
  nothing, so the opponent AI can search with the same code the player uses.
- `Session` (also in `core/`) adds what differs between modes on top of `Game`: the Time
  attack clock, the move budget, Zen's undo and "stuck" state, and when a game counts as won
  or over. `GameRules` names a mode plus its duration or budget and gives each its own save
  slot and score table.
- `Bot` is the computer opponent and `VersusMatch` is a match against it: two boards under
  the same rules and spawn odds, plus the rule that picks the winner. Easy looks one move
  ahead and often plays at random; Medium searches two moves deep and sometimes slips; Hard
  runs a deeper expectimax search (two to four moves, deeper as the board fills) over a
  heuristic that likes big tiles along a snake from one corner, empty cells and smooth
  neighbours. On 4x4 Easy typically tops out at 128 to 256, Medium at 256 to 1024, and Hard
  at 2048 to 4096.
- `app/` is the Android app. Menus and the game screen frame are Jetpack Compose; the board
  itself is `BoardView`, a plain `View` drawn on a Canvas. It allocates nothing per frame,
  takes its clock from the Choreographer's vsync timestamp, and asks for frames only while
  tiles are animating. `MainActivity` requests the display's highest refresh rate.
- `Storage` keeps saved games, high-score tables and the last screen in the app's private
  preferences. Reads come from memory; writes go to disk on one background thread. The text
  formats (`SaveCodec`, `ScoreTable`) live in `core/` and reject damaged data instead of
  crashing. One save slot and one score table exist per grid size and mode.
- In Versus the opponent thinks on its own thread against a copy of its board. The move is
  applied on the main thread after a human-like pause (about 0.9 s on Easy, 0.6 s on Medium,
  0.4 s on Hard), so the search cannot cost the player a frame. The opponent's board is a
  second, smaller `BoardView`.
- The board view is tested on the JVM with Robolectric's native graphics: the tests render
  real boards, check tile colours pixel by pixel, and drive swipes and the animation clock.
  Other tests restart the view model against the same storage to check that saves and scores
  survive, and draw whole screens to `app/build/screen-renders/` for a human to look at.

Decisions that the build brief left open are recorded in [docs/DECISIONS.md](docs/DECISIONS.md).

## Performance

The target is a locked 60 fps on 60 Hz screens and 120 fps on 120 Hz screens on a mid-range
phone several years old.

How the game is built for that:

- The board allocates nothing per frame and draws only while something moves. At Low and
  Medium quality a resting board requests no frames at all.
- Moves, saving, the opponent's search, audio decoding and audio output all happen off the
  main thread or outside the frame path.
- The window asks for the display's highest refresh rate; a setting limits it to 60 Hz to
  save battery.
- **Quality levels** change decoration only; gameplay and animation timing are identical.
  Low is flat tiles. Medium adds tile shadows, a ring on merges and a springier pop. High
  adds merge particles, a pulsing glow on tiles of 128 and up, and slow lights drifting
  behind the board (the only level that draws at rest, at about 30 fps).
- **Auto** starts from the phone's memory and core count (Low under about 3 GB or with four
  cores or fewer, Medium up to about 6 GB, High above) and steps down one level whenever
  more than 12% of the last 240 frames of play missed their deadline.

How it is measured:

- `FrameMonitor` reads the platform's per-frame report (`Window.OnFrameMetricsAvailable`,
  the source behind `dumpsys gfxinfo` and JankStats): the full time of each frame including
  the GPU side, compared with the frame's deadline.
- Settings has **Show frame timing while playing**, a one-line live readout, and a
  **Performance test** that plays the same 6x6 game for ten seconds at each quality level
  and reports frames, median, 90th and 99th percentile and worst frame time, and the share
  of slow frames, ready to copy.

Measured results, from the in-app performance test (version 0.7.0, 6x6 grid, ten seconds
per level, run by the owner on 2026-11-14). Times are the system's end-to-end figure for a
frame, which includes waiting in the display pipeline, so they are not the cost of drawing.
"Late" is the share of frames the system judged to have missed their deadline.

| Device | Screen | Level | Median | p99 | Worst | Late |
|---|---|---|---|---|---|---|
| Xiaomi 17T (2602DPT53G), Android 16 | 120 Hz | Low | 6.7 ms | 13.2 ms | 29.3 ms | 0.0% |
| | | Medium | 6.5 ms | 12.9 ms | 28.0 ms | 0.0% |
| | | High | 6.8 ms | 12.1 ms | 19.3 ms | 0.0% |
| | 60 Hz (limiter on) | Low | 11.6 ms | 20.9 ms | 29.2 ms | 0.0% |
| | | Medium | 11.9 ms | 21.3 ms | 27.6 ms | 0.0% |
| | | High | 12.1 ms | 21.5 ms | 25.7 ms | 0.0% |
| Samsung Galaxy S21 FE (SM-G990B), Android 16 | 120 Hz, first run | Low | 7.4 ms | 15.9 ms | 21.5 ms | 0.3% |
| | | Medium | 15.6 ms | 24.1 ms | 53.0 ms | 0.8% |
| | | High | 15.8 ms | 24.2 ms | 24.9 ms | 0.1% |
| | 120 Hz, second run | Low | 7.3 ms | 10.8 ms | 19.2 ms | 0.1% |
| | | Medium | 4.8 ms | 12.3 ms | 23.6 ms | 0.3% |
| | | High | 5.6 ms | 9.3 ms | 15.9 ms | 0.1% |

Mid-range and entry-level phones, measured by the owner later the same day:

| Device | Screen | Level | fps | Median | p99 | Worst | Late |
|---|---|---|---|---|---|---|---|
| Samsung Galaxy A52 (SM-A525F), Android 14, version 0.7.1 | 60 Hz | Low | 60 | 12.6 ms | 28.3 ms | 37.9 ms | 0.2% |
| | | Medium | 60 | 12.6 ms | 27.3 ms | 37.6 ms | 0.2% |
| | | High | 60 | 13.5 ms | 26.4 ms | 32.7 ms | 0.2% |
| Xiaomi Redmi A3 (23129RN51X), Android 16, version 0.8.0 | 90 Hz | Low | 90 | 11.3 ms | 20.7 ms | 29.3 ms | 0.1% |
| | | Medium | 90 | 10.6 ms | 18.9 ms | 27.2 ms | 0.0% |
| | | High | 90 | 11.7 ms | 24.1 ms | 46.9 ms | 0.0% |
| | 60 Hz (limiter on) | Low | 60 | 13.4 ms | 20.8 ms | 26.0 ms | 0.2% |
| | | Medium | 60 | 13.9 ms | 20.8 ms | 31.1 ms | 0.0% |
| | | High | 60 | 14.2 ms | 20.3 ms | 23.6 ms | 0.0% |

What these do and do not show:

- **60 Hz: target met on the phones tried.** A 2021 mid-range phone and an entry-level
  phone both held 60 fps with at most 0.2% of frames late, at every quality level.
- **90 Hz: held on the entry-level phone** (90 fps, at most 0.1% late).
- **120 Hz: held on two fast phones** (Xiaomi 17T, Galaxy S21 FE) with at most 0.8% late.
  **No mid-range phone with a 120 Hz screen has been measured**, so that half of the target
  is shown only on stronger hardware.
- Every run is ten seconds of scripted play on one unit of each phone. It is evidence, not
  a guarantee across devices, temperatures or long sessions.
- The Redmi A3 switched to 60 Hz when the limiter asked, and paced evenly there.
- In version 0.7.0 the Xiaomi reported every frame twice, so its frame counts read 240 per
  second on a 120 Hz screen. Version 0.7.1 ignores the duplicate reports and counts drawn
  frames directly; re-run on the Xiaomi it measured 120 fps at 120 Hz and 60 fps with the
  limiter on, 0.0% late at every level, with medians of 5.8 to 6.2 ms and 10.3 to 10.9 ms.
- The S21 FE did not switch to 60 Hz when the limiter asked, so its second run above is
  also at 120 Hz. From 0.7.1 the board additionally draws on every other refresh.
- S21 FE re-run on 0.7.3: 119 to 120 fps at 120 Hz with 0.3 to 0.4% late at every level,
  and no duplicate reports (those are specific to the Xiaomi).
- S21 FE on 0.7.3 with the limiter on (screen still at 120 Hz, board drawing every other
  refresh): 62 fps, medians of 6.5 to 7.2 ms, but 7.0% (Low), 10.6% (Medium) and 26.9%
  (High) of frames judged late. Frame times are no worse than without the limiter; the
  system holds these frames to a one-refresh deadline and the slower ones land one 120 Hz
  refresh (8 ms) later than intended. **So on a phone that refuses to switch to 60 Hz, the
  limiter halves the drawing work but its 60 fps is not evenly paced.** On the Xiaomi, which
  does switch, the limiter measured 0.0% late.

## Audio

Every sound in the game is synthesized by code in this repository. Nothing is sampled,
recorded or downloaded, so there are no licences to track.

- `audiogen/` is a small Kotlin program with no dependencies. `Synth.kt` has the building
  blocks (an FM electric piano, a bell, a pad, a bass, a kick, noise, a reverb), `Effects.kt`
  the sound effects and `Music.kt` the three tracks.
- `tools/generate-audio.sh` runs it and encodes the result with ffmpeg into
  `app/src/main/res/raw/*.ogg` (about 1.8 MB in total). **Those files are committed**; the
  app build does not run the generator, so building the game needs neither ffmpeg nor time.
  Run the script only to change the audio. The generator is deterministic.
- Generation happens ahead of time rather than on the phone because real-time synthesis
  would compete with the game for the CPU, and the frame rate matters more.

What is generated:

- **Effects:** tile slide, new tile, button tap, undo, win, game over, and twelve merge
  sounds, one per tile from 4 to 8192. Each bigger tile is a step higher on a pentatonic
  scale. Merges up to 64 are short soft knocks; from 128 the note rings longer with an
  octave on top, and from 1024 it becomes a small rising chord. The big ones are matched
  in loudness to the 64 merge, so they are richer and longer, not louder.
- **Music:** a menu track (69 s), a gameplay track (77 s) and a calmer Zen track (96 s).
  Each is written into a circular buffer: notes and reverb that run past the end wrap
  around onto the start, so the loop point is part of the music rather than a cut.

In the app, `AudioEngine` plays effects through `SoundPool` (the low-latency path). Music is
decoded once into memory and written to an `AudioTrack` by a small thread that goes from the
last sample straight to the first, so the loop is exact on every phone; changing track fades
the two across each other. Music requests audio focus, pauses for phone calls and other apps,
and stops in the background. With music off, focus is never requested, so whatever was
already playing on the phone keeps playing.

## Permissions

One: `VIBRATE`, for vibration on merges at a strength chosen in settings. No network access,
no storage access, nothing else.

## Licence

GPL-3.0, see [LICENSE](LICENSE).
