# Release readiness — 4 October 2026

Version 1.0 (versionCode 1) is a tested release candidate. It is **not certified ready for Play submission**: a few steps below can only be done by the owner (Play Console enrolment, signing, a final manual pass on the phone). Everything that can be verified from the repository and on emulators has been.

## Completed preparation

- **Licence parity with iOS.** `LICENSE` is byte-identical to the iOS repository's (GPL-3.0-or-later); the bundled GPL text, Pikafish authors, NNUE notice and CCPD notice are byte-identical to the iOS copies, and `scripts/check_release.py` re-verifies that on every run. The README carries the same "Licensing and source availability" statement, adapted for Android, with rebuild instructions. An Apache-2.0 notice for the Android libraries is bundled and shown in-app.
- **Same engine as iOS, fully licensed data.** Pikafish revision `6a59ee2f…` and the network with SHA-256 `7d13d735…` (verified at runtime and by the checker). The learning database is the 58,456 CC BY 4.0 CCPD records, stored in this repository with Git LFS; release builds refuse the small fallback subset, and the checker confirms the bundled database has no records outside CCPD.
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
| Instrumented suite, **physical OnePlus 6** (Android 11, 1080×2280, arm64) | 63/63 (2 developer tools skipped by design), including the real engine on real hardware and the first-run copy of the learning database |
| Rules agreement | 40 random games of up to 120 plies each (thousands of plies) accepted by both the Kotlin rules and Pikafish; a sample of every corpus category and **every one of the 130 bundled mating puzzles** replay cleanly; an illegal move is rejected by both |
| Engine | Pikafish rule fixtures from the iOS C++ smoke test (repetition, perpetual check/chase, mate, stalemate, bare kings, illegal history), search cancellation under 1.5 s, 16 back-to-back searches |
| UI flows | Every screen and flow: selection and legal-move markers, captures, history/replay, undo, hints, flip, move confirmation, clocks, resign and result, save/resume, rotation, backgrounding; settings (language switch without restart, theme, labels, toggles, licences, privacy); learning browse, search, collection filter, study, bookmark, practice |
| Stress | 15,000 random monkey events on Android 7.0: no crash, no ANR |
| Accessibility sizing | Layouts checked at 160 % (all main screens) and 200 % (game screen) system font size; board points are labelled for TalkBack |
| Layouts | Phone portrait, phone landscape, tablet; Study and Practice reflow to board-plus-panel on wide screens |
| Signed release + CI | Bundle and APK signed with the upload key (certificate SHA-256 `86cf44fe…2269e654`); the tag-triggered **Release** workflow was rehearsed on GitHub and produced artifacts signed with the same key, with 43 release checks passing. The key is not in the repository (private on the owner's machine and in encrypted GitHub secrets) |
| Physical phone, signed release build, **airplane mode** (OnePlus 6, Android 11; confirmed offline) | New game, Pikafish replied in about 2 s; Learn opened the 58,456-record library (six categories) and stepped through a master game; all 90 board points labelled and tappable |
| Haptics and sound on the phone | Android's vibration log shows the app's click effects for selecting and moving; `FeedbackInstrumentedTest` shows the audio system consumes the move cue and that muted settings play nothing |
| Long session, physical phone | 10 minutes of strongest-level self-play: 199 moves at the 3 s budget, memory flat (323 MB start and end), battery 28.7 → 34.7 °C. Android's thermal status reached *severe* after about 7 minutes of nonstop thinking; the engine now halves its think time at *severe* and quarters it at *critical* (a second run showed 1.5 s moves, memory flat at 319 MB, battery peak 35.4 °C). OnePlus's background-power manager then ended the test process at the 10-minute mark — an artefact of a test run having no visible screen; the app never searches in the background |
| Release build | R8-minified APK 99.6 MB and AAB 100.7 MB (Play base-module limit 200 MB); release smoke-tested by hand: engine reply, save and resume |
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

These need the owner's Google account or a human ear, so they cannot be done from here:

1. **Play Console** — enrol, create the app, upload the first bundle by hand (take it from the GitHub release the workflow produces), fill in the listing, content rating, data safety and target-audience forms from `play-store-submission.md` (all answers are written out), add a contact email, run the closed test Google requires of new personal accounts, then promote to Production and send for review. The exact click-path is in `play-store-submission.md`.
2. **Release tag** — `git tag v1.0.0 && git push origin v1.0.0` builds, signs and publishes the release through CI.
3. **Hands-on check (optional)** — listen to the sounds and try TalkBack with a real voice; the automated evidence above shows the sound is played and the accessibility labels exist, but not how they sound.
4. **Paperwork** — the Pikafish network's provenance is recorded in [`engine-network.md`](engine-network.md).

Google's acceptance of the app is a separate decision from any of the evidence above.

## Re-run the checks

```sh
python3 scripts/l10n.py check
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease :app:bundleRelease
python3 scripts/check_release.py
```
