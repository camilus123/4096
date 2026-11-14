# 4096

A 2048-style puzzle game for Android: 4x4, 5x5 and 6x6 grids, five modes (Classic, Zen,
Time attack, Limited moves, Versus AI), generated audio, no ads, no analytics, no network.

**Status: milestone 6 of 8.** All five modes are playable on all three grids, with sound
effects, three music tracks and a settings screen (volumes, mute, vibration, theme,
language). Quality levels and measured performance tuning come next.

| Milestone | State |
|---|---|
| 1. Rules engine and tests | done |
| 2. Playable Classic on all grids | done |
| 3. Saving and high scores | done |
| 4. Zen, Time attack, Limited moves | done |
| 5. Versus AI | done |
| 6. Audio | done |
| 7. Quality levels and performance tuning | next |
| 8. Polish and release build | |

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
