# Decisions

Choices confirmed by the owner, and choices made where the build brief was silent.

## Confirmed by the owner (2026-11-13)

- App name **4096**, package id `io.github.camilus123.game4096`.
- Minimum Android 8.0 (API 26).
- Distribution: sideloaded APK only.
- Win tiles: 2048 on 4x4, 4096 on 5x5, 8192 on 6x6, with "keep playing" after the win.
- Light theme (warm classic) and dark theme (neon), following the system setting by default.
- Languages: English and Slovenian.
- Portrait only, phones only.
- No ads, analytics, accounts or online features; only permissions the game needs to
  function (so no network permission).
- Licence stays GPL-3.0. Commits go straight to `main`.
- GitHub Actions is the build machine. Test device: the owner's Xiaomi 17T, which is not
  a low-end phone, so results on it do not prove the mid-range target.

## Proposed in the question round, not objected to

- Undo: unlimited in Zen, none in the other modes.
- Zen keeps its own score table (best score and best tile).
- Time attack: 1, 3 and 5 minutes, one table each.
- Limited moves: 50, 100 and 200 moves, one table each.
- Versus race target is selectable: 256, 512, 1024 or 2048.
- Versus timer: 2, 3 or 5 minutes.
- Audio is synthesized once by a generator in the repo and the output is committed.

## Made while building

- **Boards store exponents, not values** (1 is the "2" tile). A 6x6 board can pass 2^31, and
  the AI benefits from small integers.
- **Own random generator (SplitMix64)** instead of `kotlin.random.Random`: its whole state is
  one `Long`, so it can be saved and restored exactly.
- **Undo restores the random generator too.** Undoing and repeating the same swipe gives the
  same new tile, so undo cannot be used to re-roll a bad spawn.
- **Unit tests use JUnit 4**, because it is the one test framework that can also run on the
  network-restricted machine this was written on (`tools/test-core-offline.sh`).
- **Toolchain: Gradle 8.14.3, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Compose BOM
  2025.12.01, compile and target SDK 36.** These are a year behind the newest releases
  (AGP 9.5, Kotlin 2.4) on purpose: it is a combination known to work together. Upgrading
  is a separate, low-risk task for later.
- **The board is a classic `View`, not a Compose Canvas.** It gives direct control over
  invalidation and makes "no allocations per frame" easy to keep and to check.
- **A move is applied to the game the instant the swipe is recognised**, and the view then
  animates from the old board to the new one (100 ms slide, 130 ms merge pop / spawn).
- **Swipe queue of two.** A swipe made while tiles are sliding is held and applied when the
  slide ends, skipping the rest of the pop. A third swipe replaces the second.
- **A swipe fires during the drag**, once the finger has moved about 16 dp mostly along one
  axis, and re-arms without lifting. Clearly diagonal drags are ignored.
- **Swipes work anywhere below the header**, not only on the board.
- **No navigation library and no icon library**, to keep the APK small (release build is
  about 0.8 MB at milestone 2).
- **Android backup is left on**, so saved games and scores will follow the owner to a new
  phone through the system's own backup. This needs no permission.
