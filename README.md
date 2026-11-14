# 4096

A 2048-style puzzle game for Android: 4x4, 5x5 and 6x6 grids, five modes (Classic, Zen,
Time attack, Limited moves, Versus AI), generated audio, no ads, no analytics, no network.

**Status: milestone 7 of 8.** All five modes, audio, settings, three quality levels plus
Auto, a 60 fps limiter and an in-app performance test. What remains is the signed release
build and final polish.

| Milestone | State |
|---|---|
| 1. Rules engine and tests | done |
| 2. Playable Classic on all grids | done |
| 3. Saving and high scores | done |
| 4. Zen, Time attack, Limited moves | done |
| 5. Versus AI | done |
| 6. Audio | done |
| 7. Quality levels and performance tuning | done |
| 8. Polish and release build | next |

## Building

Requires JDK 17 or newer and the Android SDK (platform 36). The Gradle wrapper downloads
everything else.

```
./gradlew test                 # rules tests and board-view tests
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Every push to `main` runs the tests and builds the APK in GitHub Actions
(`.github/workflows/ci.yml`). To get a build without a development setup, open the latest run
under the repository's **Actions** tab and download the `4096-debug-apk` artifact.

Debug builds are signed with a throwaway key that differs between CI runs, so Android refuses
to install one over another: uninstall the old build first. A stable signing key comes with
the release build in milestone 8.

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

What these do and do not show:

- Both phones held their refresh rate with at most 0.8% of frames late, at every level.
- Neither is the reference device. The Xiaomi 17T is a current high-end phone and the
  S21 FE was an upper mid-range phone with a flagship processor when new. **Nothing has been
  measured on a 3 to 4 GB mid-range phone, so the target is not yet shown to be met there.**
- In version 0.7.0 the Xiaomi reported every frame twice, so its frame counts read 240 per
  second on a 120 Hz screen; the timings above are not affected. Later versions ignore the
  duplicate reports and count drawn frames directly.
- The S21 FE did not switch to 60 Hz when the limiter asked, so its second run is also at
  120 Hz. Later versions additionally make the board draw on every other refresh.

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
  scale; from 128 an octave joins in, and from 1024 the note becomes a small rising chord.
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
