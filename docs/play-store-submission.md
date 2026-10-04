# Google Play submission package

Prepared 4 October 2026 for version 1.0 (versionCode 1), package `com.frankzhu.xiangqimobile`.

## Store listing draft

- **App name:** Xiangqi Mobile (availability must be checked in Play Console; limit 30 characters).
- **Short description (80 max):** Play and study Chinese chess offline with the Pikafish engine.
- **Category:** Games → Board. **Tags:** Board, Strategy, Puzzle, Offline.
- **Price:** Free; no advertising or in-app purchases.
- **Languages:** English, Simplified Chinese (zh-CN), Traditional Chinese (zh-TW). The app switches language inside Settings regardless of the device language.
- **Developer name / contact:** Frank Zhu. A contact email, and optionally a website, are required by Play Console and are the owner's to enter.
- **Privacy policy URL:** https://github.com/frankmanzhu/xiangqi-mobile-android/blob/main/docs/privacy-policy.md
- **Support URL:** https://github.com/frankmanzhu/xiangqi-mobile-android/blob/main/docs/support.md
- **Source code:** https://github.com/frankmanzhu/xiangqi-mobile-android

### Full description (4000 max)

Play Chinese chess wherever you are. Challenge the bundled Pikafish engine, share your device with a friend, or explore a library of more than 58,000 recorded games and practice lines — all offline.

• Play offline against five computer strengths.
• Play two-player games on one device.
• Choose casual play or 10- and 15-minute clocks.
• Save and resume your active game, review every move, and share portable game records.
• Use staged hints and undo in casual games.
• Browse openings, endgames, tactics, matches, and mating exercises, and practise them move by move.
• Choose the Classic, Tournament (dark), or Calm board theme.
• Use English, Simplified Chinese, or Traditional Chinese, with Traditional or Simplified piece labels.
• Works with TalkBack: every point on the board is labelled.

No account, ads, tracking, or in-app purchases. Games, progress, and settings stay on your device, and the app does not use the network. New games use the pinned Pikafish Computer Rule, including repetition, perpetual checking and chasing, and draw adjudication.

Xiangqi Mobile is free software (GPL-3.0-or-later). Source code and licence notices are linked from the app.

### What's new (500 max)

First release for Android: play Pikafish offline, two-player mode, 58,000+ games and puzzles to study, three themes, and English / 简体中文 / 繁體中文.

## Graphic assets (this repository)

| Asset | File | Play requirement |
| --- | --- | --- |
| App icon | `docs/play-store/icon-512.png` | 512×512 PNG |
| Feature graphic | `docs/play-store/feature-graphic.png` | 1024×500 (a crop of the app artwork — replace with marketing art if desired) |
| Phone screenshots | `docs/play-store/phone/*.png` | 8 captures, 1080×2400, from the real app on an Android 15 emulator |
| 10-inch tablet screenshots | `docs/play-store/tablet-10in/*.png` | 8 captures, 2560×1600 |

The screenshots are raw captures (no device frames or captions) taken with demo-mode status bars by `StoreScreenshotsTest`; regenerate them with the command in that file. The launcher icon is the iOS app's artwork as an adaptive icon (with a themed monochrome layer and PNG fallbacks for Android 7).

## Play Console declarations

- **App access:** all functionality is available without an account or credentials.
- **Ads:** the app contains no ads.
- **Content rating (IARC):** complete the questionnaire accurately. The app has no violence beyond abstract board-game captures, no user-generated content or chat, no gambling or simulated gambling, no web browsing, no location sharing, and no purchases. The historical game corpus contains player names and event titles from public records. Expect an "Everyone"-class rating, but use the rating the questionnaire produces.
- **Target audience:** choose an adult or 13+ audience. The game is suitable for children, but do not opt into the Designed for Families program merely because of that.
- **Data safety:** *No data collected, no data shared.* The app declares no `INTERNET` permission, has no analytics, advertising or crash-reporting SDK, and stores games, learning progress and settings only on the device. "Share game record" hands text to the app the user picks through Android's share sheet, which is user-initiated and not collection by the developer. Android's automatic backup is disabled. Data deletion: uninstalling or clearing storage removes everything; there is no account to delete.
- **Permissions:** only `VIBRATE` (a normal permission that needs no runtime prompt). No sensitive permissions, background location, SMS or call-log access.
- **Government, news, health, financial features:** none.
- **Export / encryption:** the app implements no network protocol or custom cryptography; a SHA-256 checksum verifies the bundled network file.

## Build and signing

**The upload key is not in this repository, and must never be.** It lives in `~/.xiangqi-signing/` on the owner's machine (`xiangqi-upload.jks` and a private `signing.env`; back both up somewhere safe and offline) and, encrypted, in this repository's GitHub Actions secrets (`XIANGQI_KEYSTORE_BASE64`, `XIANGQI_KEYSTORE_PASSWORD`, `XIANGQI_KEY_ALIAS`, `XIANGQI_KEY_PASSWORD`). Because Play App Signing holds the real app-signing key, a lost upload key can be reset through Play support; a leaked one can be revoked the same way.

### Releasing (CI does everything)

```sh
git tag v1.0.0 && git push origin v1.0.0
```

The **Release** workflow (`.github/workflows/release.yml`) then: checks out the code with Git LFS (the CCPD database), derives the version from the tag (`v1.2.3` → versionName `1.2.3`, versionCode `10203`), runs the localization check, unit tests and lint, builds the **signed** bundle and APK from the secrets, deletes the keystore from the runner, runs `scripts/check_release.py` on the result, attaches `xiangqi-mobile-<version>.aab`, the APK and `SHA256SUMS.txt` to a GitHub release (so the binary matches a public source tag), and — only if a `PLAY_SERVICE_ACCOUNT_JSON` secret exists — uploads the bundle to Play's **internal testing** track as a **draft**. Promoting to production is always a manual click in Play Console. Run it by hand from the Actions tab to rehearse without publishing.

Pull requests never receive the secrets, and the workflow only runs on tags and manual dispatch in the owner's repository.

### One-time Play Console setup (owner's account — not something CI or an assistant can do)

1. Create a Google Play developer account (one-time registration fee, identity verification).
2. **Create app** → name *Xiangqi Mobile*, default language English, *Game*, *Free*; accept the declarations.
3. Enable **Play App Signing** when prompted at the first upload.
4. **Upload the first bundle by hand** (Play requires the first upload through the Console): download `xiangqi-mobile-1.0.0.aab` from the GitHub release produced by the workflow and add it to an internal-testing release. Use the CI-built bundle so versions line up.
5. Fill in **Store listing** (text and graphics from this document and `docs/play-store/`), **Privacy policy URL**, **Content rating**, **Target audience**, **Data safety** and **App access** from the declarations above, plus your contact email.
6. Optional, to let CI upload later bundles: *Setup → API access* → create a service account in Google Cloud, give it **Release manager** rights for this app in *Users and permissions*, create a JSON key, and store it as the `PLAY_SERVICE_ACCOUNT_JSON` repository secret.
7. New personal accounts must run a **closed test** with a minimum number of testers for a minimum period before **Production** access; confirm the current rule in Play Console. Then promote the release to Production and **Send for review**.

### Building locally

```sh
. ~/.xiangqi-signing/signing.env          # sets XIANGQI_KEYSTORE and the passwords
git lfs pull                              # the real learning database, not a pointer
./gradlew :app:bundleRelease
python3 scripts/check_release.py --aab app/build/outputs/bundle/release/app-release.aab
```

**Size.** The release bundle is about 101 MB (APK 99.6 MB), comfortably under Play's 200 MB limit for a base module. Allow roughly 300 MB of free storage on the device: the installed package plus the 90 MB database and 50 MB network copied out of it on first use.

**Devices.** Android 7.0 and newer, 64-bit ARM (`arm64-v8a`) and x86-64 (`x86_64`). The Play Console device catalogue will therefore exclude 32-bit-only phones, which is expected: Pikafish requires a 64-bit build.

## Review notes (for the Play Console "App access / instructions" field)

The app works fully offline and needs no reviewer account. On the home screen, *Play Computer* starts a game against the bundled engine, *Two Players* starts a shared-device game, and *Learn and practice* opens the bundled library (the first open takes a few seconds while the database is copied out of the package). *Settings* contains the privacy policy, the rules explanation, licence notices, the source-code link and the support link. No executable code, neural-network weights, or learning data are downloaded at runtime.

## Licensing

The licence is **GPL-3.0-or-later**, identical to the iOS app (`scripts/check_release.py` verifies the `LICENSE` file and the bundled notices byte-for-byte against the iOS repository). For every distributed version the complete corresponding source is this repository at the matching tag, including the Pikafish sources at the pinned revision, the C++ bridge and JNI layer, build scripts and the bundled network.

The learning database holds 58,456 records, all from the CC BY 4.0 Chinese Chess Practical Dataset; attribution, source revision and modification notices are in `app/src/main/assets/learning/CCPD-source.json` and the in-app Licences screen. Pikafish publishes separate terms for its network weights; the app is free with no ads or purchases, and the network's origin, hash and terms are recorded in [`engine-network.md`](engine-network.md).

## Verification

```sh
python3 scripts/l10n.py check
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest      # emulator or device
./gradlew :app:assembleRelease :app:bundleRelease
python3 scripts/check_release.py
```

Current outcomes and the remaining gates are recorded in `release-readiness.md`. Passing these checks is evidence for a release candidate, not a promise of Play review acceptance.
