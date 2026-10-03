# Xiangqi Mobile for Android

A native, offline Android app for playing xiangqi (Chinese chess): Player vs Computer, two-player hot-seat, full move replay, atomic save/resume, clocks, undo, hints, three board themes, a learning library of 145,000+ recorded games and puzzles, and portable game sharing.

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
| `app/src/main/assets/` | Pikafish network, localization catalogs, licences (the learning database is added at build time). |
| `localization/` | `.strings` files — the same format and keys as the iOS app. |
| `app/learning-lite/` | The small fallback learning database (see below). |
| `scripts/` | `l10n.py` (catalog generator and checker), `build_learning_db.py`. |
| `docs/` | Porting notes and parity matrix. |

## Localization

`localization/{en,zh-Hans,zh-Hant}.lproj/Localizable.strings` hold the strings, with English as the source of truth for the key set. After editing them run `python3 scripts/l10n.py generate`, which rewrites the typed `L10n` Kotlin accessors and the runtime catalogs in `assets/l10n/`. `scripts/l10n.py check` runs in CI and fails on drift, a missing translation, a placeholder mismatch, or an unreferenced key.

## Learning database

The app bundles the **full 205 MB CCPD corpus (145,065 records)**, linked straight from the iOS repo rather than copied: the Gradle build picks up `../xiangqi-mobile/Resources/Learning/ccpd.sqlite3`, so check the two repos out side by side and run `git lfs pull` in the iOS one (the database lives in Git LFS there). Both apps then always ship the same data.

| Situation | What gets bundled |
|---|---|
| iOS repo checked out next to this one (default) | Full corpus |
| `-Pxiangqi.learningDb=/path/ccpd.sqlite3` or `XIANGQI_LEARNING_DB` | Full corpus from that file |
| No iOS checkout (CI, a fresh clone) | A small committed subset (`app/learning-lite`, ~12 MB) so the project still builds and runs |
| `-Pxiangqi.liteLearningDb=true` | The subset, for faster debug installs |

Release builds (`assembleRelease`, `bundleRelease`) **refuse to use the subset** unless you pass `-Pxiangqi.allowLiteLearningDb=true`, so a release can never silently ship the cut-down library. A Git LFS pointer file is detected and rejected too. The first time Learn is opened the app copies the database out of the APK once (a few seconds); the APK stays under Play's size limit because the database compresses in the APK.

The subset is rebuilt with `python3 scripts/build_learning_db.py` (GitHub rejects files over 100 MB, which is why the full file is not committed here).

## Licences

Original source is GPL-3.0-or-later (see `LICENSE`). Bundled third-party work:

- [Pikafish](https://github.com/official-pikafish/Pikafish) — GPL-3.0, with its NNUE network under its own terms (see the in-app Licences screen and `assets/licenses/`).
- [Chinese Chess Practical Dataset](https://github.com/Yvonne761/Chinese-Chess-Practical-Dataset) (CCPD) — CC BY 4.0, plus validated ICCS match collections.
