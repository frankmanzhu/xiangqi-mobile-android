# Xiangqi Mobile for Android

A native, offline Android app for playing xiangqi (Chinese chess): Player vs Computer, two-player hot-seat, full move replay, atomic save/resume, clocks, undo, hints, three board themes, a learning library of 140,000+ recorded games and puzzles, and portable game sharing.

This is the Android sibling of the iOS app, [frankmanzhu/xiangqi-mobile](https://github.com/frankmanzhu/xiangqi-mobile). The two share the same game record, rules engine revision, localization files and learning data, so a game saved or shared on one platform opens on the other.

> The canonical game record is `starting FEN + ordered UCI moves + versioned rules policy`. Human-readable notation is derived from it.

## Features

- **Pikafish engine, on-device** — the same pinned Pikafish revision and NNUE network as iOS, built with the NDK and called over JNI. Five strength levels; no network access.
- **Pikafish rule judge** decides repetition, perpetual check/chase and other computer-rule results, so adjudication matches the engine playing the game.
- **Play modes** — vs. computer (choose side, strength, time control) and two players on one device; 10/15-minute clocks; undo (untimed games), two-step hints, board flip, replay from any ply.
- **Learn** — browse and search the CCPD corpus (openings, middlegames, endgames, mating puzzles, master games), study a record move by move, or practise a puzzle against the recorded line. Bookmarks and progress are kept locally. Searching in Simplified finds Traditional records and vice versa.
- **Themes** — Classic, Tournament (dark) and Calm; piece glyphs in Traditional or Simplified characters; optional coordinates.
- **Languages** — English, 简体中文, 繁體中文, switchable inside the app without a restart.
- **Synthesized sound and haptics** — no audio assets are bundled.
- **Accessible board** — every point is a labelled touch target for TalkBack.

## Requirements

- Android 7.0 (API 24) or newer, 64-bit (`arm64-v8a`, plus `x86_64` for emulators). Pikafish does not support 32-bit ARM.
- Building: JDK 17, Android SDK 36, NDK 27.2.12479018, CMake 3.22.1, Python 3.

## Build and test

```bash
./gradlew :app:assembleDebug            # debug APK in app/build/outputs/apk/debug
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest  # needs an emulator or device; exercises the real engine and SQLite
python3 scripts/l10n.py check            # translations complete, referenced, and in sync
```

Release builds are minified with R8 and signed from the environment (nothing secret is committed):

```bash
export XIANGQI_KEYSTORE=/path/to/keystore.jks XIANGQI_KEYSTORE_PASSWORD=… XIANGQI_KEY_ALIAS=… XIANGQI_KEY_PASSWORD=…
./gradlew :app:assembleRelease :app:bundleRelease
```

## Project layout

| Path | Contents |
|---|---|
| `core/` | Pure-Kotlin (JVM) game logic: rules, FEN/UCI, Chinese-notation PGN parser, game session, puzzle session, learning progress, sound synthesis. No Android dependencies, so it is fast to test. Port of iOS `XiangqiMobile/Core` + `GameSession`. |
| `app/` | Android app: Compose UI, themes, localization runtime, SQLite learning library, audio/haptics, JNI engine client. |
| `app/src/main/cpp/` | Pikafish sources, the shared `PikafishBridge`, and the JNI shim, built by CMake. |
| `app/src/main/assets/` | Pikafish network, learning database, localization catalogs, licences. |
| `localization/` | `.strings` files — the same format and keys as the iOS app. |
| `scripts/` | `l10n.py` (catalog generator and checker), `build_learning_db.py`. |
| `docs/` | Porting notes and parity matrix. |

## Localization

`localization/{en,zh-Hans,zh-Hant}.lproj/Localizable.strings` hold the strings, with English as the source of truth for the key set. After editing them run `python3 scripts/l10n.py generate`, which rewrites the typed `L10n` Kotlin accessors and the runtime catalogs in `assets/l10n/`. `scripts/l10n.py check` runs in CI and fails on drift, a missing translation, a placeholder mismatch, or an unreferenced key.

## Learning database

The iOS app bundles the full 205 MB CCPD corpus. GitHub rejects files over 100 MB, so this repository commits a deterministic **lite** build (about 12 MB: every study and puzzle category in full, plus 2,500 games from each game collection). To bundle the complete corpus into a local build:

```bash
python3 scripts/build_learning_db.py --full   # copies ../xiangqi-mobile/Resources/Learning/ccpd.sqlite3
```

Do not commit the result. Rebuild the lite database with `python3 scripts/build_learning_db.py`.

## Licences

Original source is GPL-3.0-or-later (see `LICENSE`). Bundled third-party work:

- [Pikafish](https://github.com/official-pikafish/Pikafish) — GPL-3.0, with its NNUE network under its own terms (see the in-app Licences screen and `assets/licenses/`).
- [Chinese Chess Practical Dataset](https://github.com/Yvonne761/Chinese-Chess-Practical-Dataset) (CCPD) — CC BY 4.0, plus validated ICCS match collections.
