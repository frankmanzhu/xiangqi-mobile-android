# Goal

Port [xiangqi-mobile](https://github.com/frankmanzhu/xiangqi-mobile) (native offline SwiftUI xiangqi app, sibling checkout at `../xiangqi-mobile`) to a native Android app (Kotlin + Jetpack Compose) with feature parity, kept as a sibling project.

## Definition of done
- Player vs Computer and two-player hot-seat games, move replay, undo, hints, clocks, atomic save/resume.
- Pikafish engine running on-device via NDK/JNI (port of `EngineBridge/` + `Vendor/Pikafish`).
- Canonical game record preserved: starting FEN + ordered UCI moves + versioned rules policy, so games are interchangeable between iOS and Android.
- Three visual themes, en / zh-Hans / zh-Hant localization (reuse `.strings` as source of truth), learning/puzzle content (CCPD library).
- Portable game sharing (PGN/FEN) compatible with iOS.
- Domain logic covered by unit tests ported from `Tests/`; shared test vectors prove rules/notation parity with iOS.

## Milestones
1. Gradle/Compose project skeleton, CI (build + unit tests).
2. Port `Core/` domain (Domain, Position, Rules, Notation, PGN) to Kotlin; port tests.
3. Board UI + hot-seat play.
4. Pikafish via NDK/JNI; Player vs Computer, hints.
5. Persistence, clocks, undo, replay, sharing.
6. Themes, localization, sound, learning/puzzles.
7. Release readiness (Play Store listing, privacy policy, screenshots).

## Source map (iOS -> Android)
| iOS | Android |
|---|---|
| `XiangqiMobile/Core` | Kotlin module `core` (pure JVM, no Android deps) |
| `XiangqiMobile/Features`, `App` | Compose UI, ViewModels |
| `EngineBridge`, `Vendor/Pikafish` | NDK/CMake + JNI |
| `Resources/Localizations` | `res/values*/strings.xml` (generated) |
| `docs/` | reuse specs; Android deltas noted here |
