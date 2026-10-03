# Porting notes: iOS → Android

How each part of [xiangqi-mobile](https://github.com/frankmanzhu/xiangqi-mobile) maps here, and where the platforms deliberately differ.

## Parity matrix

| Area | iOS | Android | Notes |
|---|---|---|---|
| Rules, FEN, UCI | `Core/Domain, Position, Rules` | `core/Domain.kt, Position.kt, Rules.kt` | Same algorithms; the standard position has the same 44 legal moves. |
| Game record JSON | `Codable`, ISO-8601 | kotlinx.serialization | Byte-compatible key names, uppercase UUIDs, whole-second dates, omitted nils. Tested against an iOS-shaped record (`GameRecordTest`). |
| Portable game share | `PortableGame` | `PortableGame` | Same `xiangqi-uci-json` format. |
| Chinese-notation PGN | `XiangqiPGN.swift` | `XiangqiPGN.kt` | Big5 / Big5-HKSCS decoding via JDK charsets. All iOS fixtures pass. |
| Engine | `PikafishBridge` + Swift client | same bridge + JNI + `PikafishEngine` | Identical Pikafish revision, network (SHA-256 verified), thinking budgets and rule judge. |
| Game session | `GameSession` (ObservableObject) | `core/GameSession.kt` (StateFlow) | UI-framework free; tested with a fake engine. |
| Learning library | SQLite3 + CFStringTransform | `android.database.sqlite` + ICU | See below. |
| Puzzle practice | `CCPDPuzzleSession` | `CCPDPuzzleSession` | Same state machine. |
| Learning progress | JSON actor | JSON store, atomic write | Same schema. |
| Sound | AVAudioPlayer + synthesized WAV | AudioTrack + the same synthesis | Deterministic recipes ported verbatim. |
| Haptics | UIKit feedback generators | Vibrator predefined effects | API 29+ predefined effects, plain one-shots below. |
| Localization | `.strings` + typed `L10n` | same files, generated Kotlin `L10n` + JSON catalogs | In-app language switch, no restart. |
| Themes | `Theme` values + registry | `XiangqiTheme` + registry | Same palettes and metrics. |
| Navigation | `NavigationStack` path | back stack in `AppModel` | Same routes. |

## Intentional differences

- **Learning database size.** iOS ships 205 MB; Android commits a 12 MB lite subset (git's 100 MB file limit). `--full` bundles everything locally.
- **Traditional/Simplified search.** iOS uses `CFStringTransform`. Android uses ICU `Transliterator` on API 29+ and, on older releases, a per-character table dumped from the same ICU data (`assets/learning/chinese-variants.tsv`, produced by `GenerateChineseTableTest`). The fallback is character-level, so it does not do ICU's phrase-level conversions.
- **minSdk 24.** `java.time` is desugared; vibration APIs are guarded by level.
- **64-bit only.** Pikafish needs 64-bit; 32-bit ARM phones are not supported.
- **Privacy text** refers to Android settings and Google Play instead of iOS and the App Store.
- **Piece identity.** iOS gives each piece a `UUID` for SwiftUI animation; the Android domain `Piece` is a plain value.

## Verification

- `core`: 47 unit tests (rules, record JSON compatibility, PGN fixtures, sound, puzzles, progress, session flows).
- `app` unit: localization completeness and placeholder parity for all three catalogs, language resolution, preference migration.
- Instrumented (real engine + SQLite + UI): Pikafish rule fixtures ported from the iOS C++ smoke test, search cancellation, the CCPD library tests, and an end-to-end "play a move, engine replies" UI test.
