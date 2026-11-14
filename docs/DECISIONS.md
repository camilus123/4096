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
  animates from the old board to the new one (110 ms slide, 143 ms merge pop / spawn; slowed 10% at the owner's request).
- **Swipe queue of two.** A swipe made while tiles are sliding is held and applied when the
  slide ends, skipping the rest of the pop. A third swipe replaces the second.
- **A swipe fires during the drag**, once the finger has moved about 16 dp mostly along one
  axis, and re-arms without lifting. Clearly diagonal drags are ignored.
- **Swipes work anywhere below the header**, not only on the board.
- **No navigation library and no icon library**, to keep the APK small (release build is
  about 0.8 MB at milestone 2).
- **Android backup is left on**, so saved games and scores will follow the owner to a new
  phone through the system's own backup. This needs no permission.
- **The game is saved after every move**, not only when the app goes to the background, so
  a crash or a dead battery loses nothing. The disk write happens off the main thread.
- **Closing the app mid-game reopens that game on the next launch.** Leaving through the
  Menu button keeps the game saved but opens the menu next time; the Play button then reads
  "Continue".
- **A game reaches the high-score table once**: when it ends, or when the player abandons
  it with "New game". A game with zero points is not recorded. Tables hold the best 10.
- **"Best" during play includes the current game**, so it rises with the score once the old
  record is beaten.
- **A finished game is not resumed.** After game over the next Play starts a new board.
- **Time attack's clock starts with the first move** and runs only while the game is on
  screen: not in the menu, not in the background, not behind the game-over panel.
- **The board asks before every move** whether play is still on, queued swipes included, so
  a swipe cannot slip in after the clock hits zero or the last budgeted move is made.
- **Time attack and Limited moves do not stop for the win tile.** The score is the goal
  there; a "you win" panel would only cost time.
- **A Zen game is scored when the player leaves it for a new one**, not when it gets stuck,
  because a stuck Zen game can be undone and continued. Zen's "Best" box still tracks the
  game in play.
- **Zen keeps the newest 200 undo steps across an app restart**; within one sitting undo is
  unlimited.
- **Picking Time attack or Limited moves starts on the middle option** (3 minutes, 100 moves).
- **The high-score screen lists only tables that have entries.**
- **Versus: the opponent does not move until the player's first move**, and the timed
  rule's clock starts then too. The opponent and the clock stop in the background and in
  the menu.
- **Versus race: running out of moves before reaching the target loses.** Without this a
  stuck side would leave the match hanging.
- **Versus timed: a stuck side keeps its score.** The match ends early only once the other
  side has passed it; otherwise it runs to the clock.
- **Versus results are counted per grid and difficulty**, across all three win rules, as the
  brief asks. Abandoning a match with "New match" counts as nothing.
- **One unfinished Versus match is kept per grid.** "Continue" appears only if the win rule,
  target or duration, and difficulty chosen in the menu match it; otherwise Play starts a
  new match and replaces it.
- **Versus opens on Race to 512 against Easy.**
- **The opponent's strength was set by simulation**, not by feel; see README for typical
  results. On 5x5 and 6x6 every level survives far longer than on 4x4 (so does a human), so
  "Last standing" on the big grids can be a very long match.
- **Audio is generated ahead of time and committed** (the brief's recommended approach),
  as OGG Vorbis: stereo music at quality 2, mono effects at quality 4.
- **Twelve separate merge sounds** rather than one sample played at different speeds, so
  timbre as well as pitch can grow with the tile.
- **A move plays the slide at once and the merge 95 ms later**, when the tiles arrive.
  Several merges in one move play only the biggest. With no merge, the new tile's blip
  plays instead.
- **The opponent's moves in Versus are silent.** Two boards' worth of effects would be noise.
- **Music is off the moment its volume is 0 or everything is muted**, and audio focus is
  then released, so the player's own music or podcast is left alone.
- **Default volumes: effects 50, music 50** (effects were 80 until the owner asked for 50).
- **Vibration has three settings: Off, Normal, High** (owner's request, after the first
  version was too weak to notice). It drives the motor directly, which needs the `VIBRATE`
  permission, the app's only one. Normal is a short tap at about half strength, High is
  full strength and twice as long; merges of 128 and up are longer at both levels. The
  system's view haptics, used at first, have one fixed light strength and no permission.
- **Language: on Android 13+ the in-app choice is the system's per-app language** and the
  game is listed under the phone's app language settings. On Android 8 to 12 the choice is
  stored by the app and applied at start-up.
- **Theme choice (System / Light / Dark) is in settings**; status bar icons follow it.

## Changed after the owner listened to it (2026-11-14)

- **Music loops through an in-memory AudioTrack, not MediaPlayer.** The loop restart was
  audible in the menu and gameplay tracks. Two causes were possible and both were removed:
  MediaPlayer's looping can leave a short gap, so tracks are now decoded once and looped
  sample-exactly; and the arrangement dropped the bass (menu) or the kick (gameplay) at the
  loop point, so those now play through it and only the melody comes and goes. Cost: two
  decoded tracks are kept in memory, about 27 MB.
- **Tracks crossfade** over about 1.4 seconds when going between menu and game.
- **Small merges are soft, short knocks and play quieter than big ones.** The first version
  used the same bright note for every merge, which got tiresome. Volume now rises with the
  tile, so the frequent small merges sit in the background.

## Quality and performance (milestone 7)

- **What each level adds** is listed in the README. The opponent's small board in Versus is
  always drawn at Low; decoration at that size would be wasted work.
- **High draws at rest; Low and Medium never do.** The brief asks both for animated
  backgrounds at higher levels and for a resting board to cost almost nothing. High's
  ambient motion runs at about 30 fps and stops when the app is not visible; the other
  levels stay completely still.
- **Auto's starting level uses memory and core count**, the two things available without
  running a benchmark at start-up. It is deliberately only a starting point.
- **Auto steps down, never up, and remembers it.** A level that stuttered once will likely
  stutter again; choosing Auto again in settings resets it.
- **Slow frames only count during play.** Menus and screen changes are not what the level
  controls.
- **A frame is "slow" when it misses its deadline** as the system reports it (Android 12+)
  or one refresh period (older versions).
- **The performance test uses the 6x6 grid** because it has the most tiles to draw, and
  gives every level the same seeded game and the same moves.
- **Glow and shadows are drawn as plain translucent shapes**, not blurs: no shaders, no
  off-screen layers, nothing allocated.
