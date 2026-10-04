# Release readiness — 4 October 2026

Version 1.0 (versionCode 1) is a tested release candidate. It is **not certified ready for Play submission**: a few steps below can only be done by the owner (Play Console enrolment, signing, a final manual pass on the phone). Everything that can be verified from the repository and on emulators has been.

## Completed preparation

- **Licence parity with iOS.** `LICENSE` is byte-identical to the iOS repository's (GPL-3.0-or-later); the bundled GPL text, Pikafish authors, NNUE notice and CCPD notice are byte-identical to the iOS copies, and `scripts/check_release.py` re-verifies that on every run. The README carries the same "Licensing and source availability" statement, adapted for Android, with rebuild instructions. An Apache-2.0 notice for the Android libraries is bundled and shown in-app.
- **Same engine and data as iOS.** Pikafish revision `6a59ee2f…` and the network with SHA-256 `7d13d735…` (verified at runtime and by the checker); the full 145,065-record learning corpus is linked from the iOS repository at build time, and release builds refuse the small fallback subset.
- **Offline privacy policy** in Settings in three languages, and the public [privacy policy](privacy-policy.md) and [support page](support.md), updated for Android (backup disabled, Play Store wording).
- **Store package:** [listing, declarations, signing and review notes](play-store-submission.md); 512 px icon, feature graphic, 8 phone and 8 ten-inch-tablet screenshots in `docs/play-store/`.
- **Launcher icon:** the iOS artwork as an adaptive icon with a themed monochrome layer and PNG fallbacks for Android 7–8.
- **Platform requirements met:** target SDK 36; native libraries 16 KB-aligned; 64-bit only; no network permission; only `VIBRATE` requested; automatic backup and device transfer disabled; predictive back enabled; edge-to-edge.

## Verified

| Check | Evidence |
| --- | --- |
| `core` unit tests | 48 pass (rules, iOS-compatible record JSON, PGN fixtures, game session incl. the duplicate-search race, puzzles, progress, sound) |
| `app` unit tests | 7 pass (catalog completeness and placeholder parity, language resolution, preference migration, Chinese variant table) |
| Lint | 0 errors (`:app:lintDebug`) |
| Instrumented suite, Android 15 phone (1080×2400) | 63/63 (2 developer tools skipped by design) |
| Instrumented suite, **Android 7.0 (API 24)** phone (1080×1920) | 63/63 |
| Instrumented suite, Android 15 10-inch tablet (2560×1600) | 63/63 |
| Instrumented suite, **physical OnePlus 6** (Android 11, 1080×2280, arm64) | 63/63 (2 developer tools skipped by design), including the real engine on real hardware and the first-run copy of the 205 MB database |
| Rules agreement | 40 random games of up to 120 plies each (thousands of plies) accepted by both the Kotlin rules and Pikafish; a sample of every corpus category and **every one of the 130 bundled mating puzzles** replay cleanly; an illegal move is rejected by both |
| Engine | Pikafish rule fixtures from the iOS C++ smoke test (repetition, perpetual check/chase, mate, stalemate, bare kings, illegal history), search cancellation under 1.5 s, 16 back-to-back searches |
| UI flows | Every screen and flow: selection and legal-move markers, captures, history/replay, undo, hints, flip, move confirmation, clocks, resign and result, save/resume, rotation, backgrounding; settings (language switch without restart, theme, labels, toggles, licences, privacy); learning browse, search, collection filter, study, bookmark, practice |
| Stress | 15,000 random monkey events on Android 7.0: no crash, no ANR |
| Accessibility sizing | Layouts checked at 160 % (all main screens) and 200 % (game screen) system font size; board points are labelled for TalkBack |
| Layouts | Phone portrait, phone landscape, tablet; Study and Practice reflow to board-plus-panel on wide screens |
| Release build | R8-minified APK 176 MB and AAB 177 MB (Play base-module limit 200 MB); release smoke-tested by hand: engine reply, save and resume |
| `scripts/check_release.py` | all checks pass except the expected "unsigned" note |
| CI (GitHub Actions) | build, unit tests, lint, release assemble, l10n check, and the instrumented suite on an emulator |

### Defects found and fixed during this pass

These were found by the new test suite and manual runs, not hypothetical:

- **Duplicate engine search race.** When playing Black, the game screen could request the computer's opening move from a background thread at the instant the first search finished, starting a second, stale search that overlapped the first commit and left the game stuck "thinking" with nothing saved. Fixed with a main-thread, no-overlap guard in the session, serialization of all native calls, and a regression test.
- **Back on Settings** did nothing, so the system Back gesture would have left the app instead of returning Home.
- **Practice board** announced only a bare square name to TalkBack; it now reads piece, side, square and state like the game board.
- **Start game** was off-screen on short phones (now pinned); **game menu and result sheets** opened half-height (now fully expanded).
- **Landscape and tablet:** the game board overflowed in landscape and the Study and Practice screens pushed their controls off-screen on wide displays; all three now fit.
- **Android 7–9 compatibility:** Simplified/Traditional search used an API 29 call (now a bundled table on older releases); vibration and `java.time` use guarded or desugared paths.
- **Privacy wording** still said the OS may back up app data (now accurate: backup is off).

## Remaining steps before submission

1. **Manual pass on the phone.** The automated suite already passes on the OnePlus 6. Still worth a few minutes by hand: play in airplane mode, listen to the sounds and feel the haptics, navigate with TalkBack, and let a long engine session run to watch heat and memory.
2. **Paperwork.** Record where the bundled Pikafish network was originally downloaded (version and terms: https://www.pikafish.com/list.html?lang=zh-CN), and glance at the GPL obligations for Play distribution (the full source is this repository, including the vendored Pikafish sources). The learning data's provenance is recorded in the source manifests exactly as in the iOS app; if you ever want a build with only the CC BY 4.0 CCPD records, it is one command (see the README's "Learning database").
3. **Release tag.** Publish a tag matching the final bundle.
4. **Play Console** (needs your account): enrol, create the upload key and enable Play App Signing, complete the content-rating and Data-safety forms from `play-store-submission.md`, add a contact email, and upload the bundle. Google currently requires new personal developer accounts to run a closed test with a minimum number of testers for a minimum period before production access; confirm the current rule in Play Console.
5. **Marketing art (optional).** The feature graphic is a crop of the app artwork and the screenshots are raw captures.

Google's acceptance of the app is a separate decision from any of the evidence above.

## Re-run the checks

```sh
python3 scripts/l10n.py check
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease :app:bundleRelease
python3 scripts/check_release.py
```
