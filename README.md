# Xiangqi Mobile for Android

A native, offline Android app for playing xiangqi (Chinese chess): Player vs Computer, two-player hot-seat, full move replay, atomic save/resume, clocks, undo, hints, three board themes, a learning library of 58,000+ recorded games and puzzles, and portable game sharing.

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
./gradlew :app:assembleDebug              # debug APK in app/build/outputs/apk/debug
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest  # needs an emulator or device; see below
python3 scripts/l10n.py check              # translations complete, referenced, and in sync
python3 scripts/check_release.py           # pre-submission checks on the release artifacts
```

### What the tests cover

- **`core` unit tests (JVM, fast):** rules and FEN/UCI, iOS-compatible save-file JSON, Chinese-notation PGN fixtures (including Big5), the game session with a fake engine (input, hints, undo, replay, clocks, persistence failures, the duplicate-search race), puzzle sessions, learning progress, sound synthesis.
- **`app` unit tests:** localization completeness and placeholder parity, language resolution, preference migration, the Simplified/Traditional search table.
- **Instrumented tests (on a device, real engine and database):**
  - end-to-end UI flows for every screen: home, settings (language, theme, labels, toggles, licences, privacy), new game, hot-seat and computer games, selection and legal-move markers, captures, history and replay, undo, hints, flipping, move confirmation, clocks, resign and result, save/resume, rotation, backgrounding, learning browse/search/study/bookmark/practice;
  - Pikafish rule fixtures ported from the iOS smoke test, search cancellation, back-to-back searches;
  - rules agreement: thousands of random plies replayed through both the Kotlin rules and Pikafish, plus a sample of every corpus category and every bundled puzzle;
  - persistence round-trips and rejection of unsupported save files; resource and checksum integrity.

Run the whole suite on a phone, a tablet and the oldest supported release (Android 7.0, API 24). CI runs it on an API 34 emulator for every push. `StoreScreenshotsTest` is a developer tool that regenerates the Play Store screenshots; it is skipped unless asked for.

Release builds are minified with R8 and signed from the environment (nothing secret is committed). Tagging `vX.Y.Z` runs the **Release** workflow, which builds, checks and signs the bundle with encrypted GitHub secrets and publishes it to a GitHub release; see [`docs/play-store-submission.md`](docs/play-store-submission.md). To build one yourself:

```bash
export XIANGQI_KEYSTORE=/path/to/keystore.jks XIANGQI_KEYSTORE_PASSWORD=… XIANGQI_KEY_ALIAS=… XIANGQI_KEY_PASSWORD=…
./gradlew :app:assembleRelease :app:bundleRelease
```

Submission material is in [`docs/play-store-submission.md`](docs/play-store-submission.md) (listing, declarations, signing) and [`docs/release-readiness.md`](docs/release-readiness.md) (evidence and the gates still open).

## Project layout

| Path | Contents |
|---|---|
| `core/` | Pure-Kotlin (JVM) game logic: rules, FEN/UCI, Chinese-notation PGN parser, game session, puzzle session, learning progress, sound synthesis. No Android dependencies, so it is fast to test. Port of iOS `XiangqiMobile/Core` + `GameSession`. |
| `app/` | Android app: Compose UI, themes, localization runtime, SQLite learning library, audio/haptics, JNI engine client. |
| `app/src/main/cpp/` | Pikafish sources, the shared `PikafishBridge`, and the JNI shim, built by CMake. |
| `app/src/main/assets/` | Pikafish network, localization catalogs, licences (the learning database is added at build time). |
| `localization/` | `.strings` files — the same format and keys as the iOS app. |
| `app/learning/` | The CCPD learning database (Git LFS). `app/learning-lite/` is the small fallback subset. |
| `scripts/` | `l10n.py` (catalog generator and checker), `build_learning_db.py`. |
| `docs/` | Porting notes, privacy policy, support page, Play Store submission package, release readiness, store graphics and screenshots. |

## Localization

`localization/{en,zh-Hans,zh-Hant}.lproj/Localizable.strings` hold the strings, with English as the source of truth for the key set. After editing them run `python3 scripts/l10n.py generate`, which rewrites the typed `L10n` Kotlin accessors and the runtime catalogs in `assets/l10n/`. `scripts/l10n.py check` runs in CI and fails on drift, a missing translation, a placeholder mismatch, or an unreferenced key.

## Learning database

The app bundles the **Chinese Chess Practical Dataset (CCPD)**: 58,456 validated records under CC BY 4.0, covering openings, middlegames, endgames, full-game tactics, mating puzzles and master and computer games. It is stored in this repository with **Git LFS** at `app/learning/ccpd.sqlite3` (about 90 MB), so a normal build needs nothing outside this repository. Run `git lfs install && git lfs pull` after cloning.

| Situation | What gets bundled |
|---|---|
| LFS file pulled (default) | The full CCPD corpus (58,456 records) |
| `-Pxiangqi.learningDb=/path/ccpd.sqlite3` or `XIANGQI_LEARNING_DB` | The database at that path |
| LFS file not pulled (CI, a quick clone) | A small subset (`app/learning-lite`, ~9 MB; the same study and puzzle categories plus up to 3,000 games per collection) so the project still builds and runs |
| `-Pxiangqi.liteLearningDb=true` | The subset, for faster debug installs |

Release builds (`assembleRelease`, `bundleRelease`) **refuse to use the subset** unless you pass `-Pxiangqi.allowLiteLearningDb=true`, so a release can never silently ship the cut-down library. A Git LFS pointer is detected and treated as "not pulled". CI deliberately does not download the LFS file (GitHub's free LFS bandwidth would not cover two 90 MB downloads per run) and runs against the subset.

Provenance: `app/learning/ccpd.sqlite3` was produced once from the iOS app's merged learning database by keeping only the CCPD records and dropping the added ICCS collections (`python3 scripts/build_learning_db.py --ccpd-only --output app/learning/ccpd.sqlite3`), then marking it `bundle=ccpd-only`, `license=CC BY 4.0`. The source revision and attribution are in `app/src/main/assets/learning/CCPD-source.json` and the in-app Licences screen. The iOS app still ships its larger merged database; see `docs/PORTING.md`. The first time Learn is opened the app copies the database out of the APK once (a few seconds). The subset is rebuilt with `python3 scripts/build_learning_db.py --source app/learning/ccpd.sqlite3`.

## Licensing and source availability

Copyright (c) 2026 Frank Zhu

Xiangqi Mobile for Android is licensed under the GNU General Public License, version 3 or any later version (GPL-3.0-or-later), exactly like the [iOS app](https://github.com/frankmanzhu/xiangqi-mobile); the root [`LICENSE`](LICENSE) file is identical. The app embeds Pikafish in-process through JNI, so the Kotlin application and the linked engine source are provided under GPL-3.0-or-later. Bundled learning data and NNUE weights keep their separate terms; the software licence does not relicense those resources. The complete GPL text, Pikafish attribution, authors list, NNUE notice and CCPD notice are bundled in the app (`app/src/main/assets/licenses`) and shown in **Settings → Licences**, along with the Apache-2.0 notice for the Android libraries (AndroidX, Jetpack Compose, Kotlin, kotlinx).

For every distributed version, the complete corresponding source is the matching release tag or commit of this repository. It contains the Kotlin application and UI source, the Pikafish sources at the pinned revision (`app/src/main/cpp/pikafish`, revision `6a59ee2f7b105bff64d9efc2692591107787e2b1`, the same one the iOS app uses), the C++ bridge and JNI layer, the Gradle build, scripts, and the bundled Pikafish network (`app/src/main/assets/engine/pikafish.nnue`). The learning database is `app/learning/ccpd.sqlite3`, stored with Git LFS (see above).

Anyone may rebuild, modify, sign and install the app with their own keystore; no credentials of this project are needed and a pull request is not required to exercise those rights. To build a modified copy:

1. Install JDK 17, the Android SDK (platform 36), NDK 27.2.12479018 and CMake 3.22.1.
2. Clone this repository at the release tag and run `git lfs install && git lfs pull` so the full learning database is bundled. Without it, debug builds fall back to the small subset.
3. Change `applicationId` in `app/build.gradle.kts` if you want to install it next to the store build, then run `./gradlew :app:assembleDebug` (or `:app:assembleRelease` with the signing environment variables above) and install the APK.

Pikafish's GPL licence cannot be removed by changing this README or the project's licence label. Avoiding GPL obligations would require replacing Pikafish or obtaining relicensing permission from the relevant Pikafish copyright holders. Pikafish publishes separate NNUE weight terms restricting commercial use without permission ([terms](https://www.pikafish.com/list.html?lang=zh-CN)); the app is free and has no ads or purchases.

The learning corpus is separate data, not software. It is the CCPD dataset, available under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), with attribution and modification notices in `app/src/main/assets/learning/CCPD-source.json` and `app/src/main/assets/licenses/CCPD-CC-BY-4.0.txt`. The app does not claim that GPL-3.0-or-later relicenses the game data.
