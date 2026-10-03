#!/usr/bin/env python3
"""Pre-submission checks for the Android release artifact.

Run after `./gradlew :app:assembleRelease :app:bundleRelease`:

    python3 scripts/check_release.py [--apk PATH] [--aab PATH] [--allow-lite]

It validates the built APK (manifest, permissions, ABIs, 16 KB alignment, bundled
network and notices, learning database) and repository hygiene (licence parity with
the iOS repo, localization sync). It proves files and declarations only: it does not
establish licensing rights, store-listing readiness or Play review acceptance.
"""

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SDK = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")
IOS_REPO = os.path.join(ROOT, "..", "xiangqi-mobile")
NNUE_SHA256 = "7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e"
PIKAFISH_REVISION = "6a59ee2f7b105bff64d9efc2692591107787e2b1"
APP_ID = "com.frankzhu.xiangqimobile"

failures, notes = [], []


def check(condition, message):
    (notes if condition else failures).append(("ok   " if condition else "FAIL ") + message)


def build_tool(name):
    tools = sorted(
        (d for d in os.listdir(os.path.join(SDK, "build-tools")) if re.match(r"\d", d)),
        key=lambda v: [int(x) for x in re.findall(r"\d+", v)],
    )
    for version in reversed(tools):
        path = os.path.join(SDK, "build-tools", version, name)
        if os.path.exists(path):
            return path
    return shutil.which(name)


def run(*args):
    return subprocess.run(args, capture_output=True, text=True)


def sha256(stream):
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1 << 20), b""):
        digest.update(chunk)
    return digest.hexdigest()


def check_apk(path, allow_lite):
    check(os.path.exists(path), f"APK exists: {os.path.relpath(path, ROOT)}")
    if not os.path.exists(path):
        return
    aapt2 = build_tool("aapt2")
    if aapt2:
        badging = run(aapt2, "dump", "badging", path).stdout
        check(f"package: name='{APP_ID}'" in badging, f"application id is {APP_ID}")
        version_code = re.search(r"versionCode='(\d+)'", badging)
        version_name = re.search(r"versionName='([^']+)'", badging)
        check(bool(version_code and version_name), f"version {version_name and version_name[1]} ({version_code and version_code[1]})")
        sdk = re.search(r"minSdkVersion:'(\d+)'", badging)
        target = re.search(r"targetSdkVersion:'(\d+)'", badging)
        check(sdk and int(sdk[1]) <= 24, f"minSdk {sdk and sdk[1]} (supports Android 7.0+)")
        check(target and int(target[1]) >= 35, f"targetSdk {target and target[1]} meets Play's current requirement (35+)")
        permissions = sorted(re.findall(r"uses-permission: name='([^']+)'", badging))
        permissions = [p for p in permissions if not p.startswith(APP_ID + ".")]  # AndroidX's own signature permission
        check(permissions == ["android.permission.VIBRATE"], f"only the VIBRATE permission is requested: {permissions}")
        check("android.permission.INTERNET" not in badging, "no network permission (the app is fully offline)")
        check("application-debuggable" not in badging, "release build is not debuggable")
        check("native-code: 'arm64-v8a' 'x86_64'" in badging or "native-code: 'x86_64' 'arm64-v8a'" in badging,
              "native libraries: arm64-v8a and x86_64 only")
        xml = run(aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", path).stdout
        check("allowBackup" in xml and re.search(r"allowBackup.*=false", xml) is not None, "automatic backup is disabled")
    else:
        failures.append("FAIL aapt2 not found; install Android build-tools")

    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        check("assets/engine/pikafish.nnue" in names, "Pikafish network is bundled")
        if "assets/engine/pikafish.nnue" in names:
            with apk.open("assets/engine/pikafish.nnue") as stream:
                check(sha256(stream) == NNUE_SHA256, "network SHA-256 matches the pinned revision")
        for notice in ["Pikafish-GPL-3.0", "Pikafish-AUTHORS", "Pikafish-NNUE-NOTICE", "CCPD-CC-BY-4.0", "Apache-2.0"]:
            check(f"assets/licenses/{notice}.txt" in names, f"licence notice bundled: {notice}")
        for lang in ["en", "zh-Hans", "zh-Hant"]:
            check(f"assets/l10n/{lang}.json" in names, f"localization catalog bundled: {lang}")
        for manifest in ["CCPD-source.json", "CCPD-merged-sources.json"]:
            check(f"assets/learning/{manifest}" in names, f"learning source manifest bundled: {manifest}")
        db = [i for i in apk.infolist() if i.filename == "assets/learning/ccpd.sqlite3"]
        check(bool(db), "learning database is bundled")
        if db:
            size_mb = db[0].file_size / 1e6
            if size_mb < 100:
                (notes if allow_lite else failures).append(
                    ("note " if allow_lite else "FAIL ") + f"learning database is the {size_mb:.0f} MB subset, not the full corpus"
                )
            else:
                check(True, f"full learning corpus bundled ({size_mb:.0f} MB)")
        libs = [n for n in names if n.endswith("libpikafish_jni.so")]
        check(sorted(libs) == ["lib/arm64-v8a/libpikafish_jni.so", "lib/x86_64/libpikafish_jni.so"], f"engine library present for both ABIs: {libs}")

    zipalign = build_tool("zipalign")
    if zipalign:
        aligned = run(zipalign, "-c", "-P", "16", "4", path)
        check(aligned.returncode == 0, "native libraries are 16 KB aligned (required for Android 15+ devices)")
    apksigner = build_tool("apksigner")
    if apksigner:
        verify = run(apksigner, "verify", "--print-certs", path)
        if verify.returncode == 0:
            notes.append("ok   APK is signed")
            if "CN=Android Debug" in verify.stdout:
                failures.append("FAIL APK is signed with the DEBUG key; sign with the upload key before submitting")
        else:
            notes.append("note APK is unsigned (sign with your upload key, or let Play App Signing handle the AAB)")


def check_repository():
    ios = os.path.join(IOS_REPO, "LICENSE")
    mine = os.path.join(ROOT, "LICENSE")
    check(os.path.exists(mine), "LICENSE present")
    if os.path.exists(ios) and os.path.exists(mine):
        same = open(ios, "rb").read() == open(mine, "rb").read()
        check(same, "LICENSE is identical to the iOS repository's (GPL-3.0-or-later)")
    else:
        notes.append("note iOS repository not found next to this one; skipped licence parity check")
    for name in ["Pikafish-GPL-3.0.txt", "Pikafish-AUTHORS.txt", "Pikafish-NNUE-NOTICE.txt", "CCPD-CC-BY-4.0.txt"]:
        a = os.path.join(ROOT, "app/src/main/assets/licenses", name)
        b = os.path.join(IOS_REPO, "Resources/Licenses", name)
        if os.path.exists(a) and os.path.exists(b):
            check(open(a, "rb").read() == open(b, "rb").read(), f"{name} matches the iOS copy")
    check(os.path.exists(os.path.join(ROOT, "app/src/main/cpp/pikafish/Copying.txt")), "Pikafish source ships with its GPL text")
    revision = open(os.path.join(ROOT, "app/src/main/cpp/bridge/PikafishBridge.cpp")).read()
    check(PIKAFISH_REVISION in revision, f"engine bridge reports Pikafish revision {PIKAFISH_REVISION[:10]}")
    l10n = run(sys.executable, os.path.join(ROOT, "scripts/l10n.py"), "check")
    check(l10n.returncode == 0, "localization catalogs are complete and in sync")
    for doc in ["docs/privacy-policy.md", "docs/support.md", "docs/play-store-submission.md", "docs/release-readiness.md",
                "docs/play-store/icon-512.png", "docs/play-store/feature-graphic.png"]:
        check(os.path.exists(os.path.join(ROOT, doc)), f"{doc} present")
    shots = []
    for folder in ("phone", "tablet-10in"):
        path = os.path.join(ROOT, "docs/play-store", folder)
        if os.path.isdir(path):
            shots += [f for f in os.listdir(path) if f.endswith(".png")]
    check(len(shots) >= 4, f"store screenshots present ({len(shots)})")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk", default=os.path.join(ROOT, "app/build/outputs/apk/release/app-release-unsigned.apk"))
    parser.add_argument("--aab", default=os.path.join(ROOT, "app/build/outputs/bundle/release/app-release.aab"))
    parser.add_argument("--allow-lite", action="store_true", help="accept the small learning-database subset")
    args = parser.parse_args()

    check_repository()
    check_apk(args.apk, args.allow_lite)
    if os.path.exists(args.aab):
        size = os.path.getsize(args.aab) / 1e6
        check(size < 200, f"AAB is {size:.0f} MB (Play base-module limit is 200 MB)")
    else:
        notes.append("note no AAB found; run ./gradlew :app:bundleRelease to check its size")

    for line in notes + failures:
        print(line)
    print(f"\n{len(notes)} passed, {len(failures)} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
