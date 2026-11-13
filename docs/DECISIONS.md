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
- **Kotlin 2.2.20, Gradle 8.14.3**, JVM target 17.
