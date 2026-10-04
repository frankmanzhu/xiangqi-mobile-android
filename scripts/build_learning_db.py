#!/usr/bin/env python3
"""Build the learning database bundled with the Android app.

Builds app/learning-lite/ccpd.sqlite3, the small fallback committed to git.

The real app bundle uses the full 205 MB corpus straight from the iOS repo (see
app/build.gradle.kts, `learningDatabase`). The lite subset only exists so that
a clone without the iOS checkout - CI, a contributor - still builds and runs:
every study/puzzle category in full plus a deterministic, equal-sized sample from each game collection
(CCPD master, CCPD computer, WXF, Dongping) so every filter in the app has data.

    scripts/build_learning_db.py [--source PATH] [--games-per-group N]

To ship only the CC BY 4.0 CCPD records (58,456 - the original corpus, without the added
ICCS collections), write a filtered copy and point the build at it:

    scripts/build_learning_db.py --ccpd-only --output /tmp/ccpd-only.sqlite3
    ./gradlew :app:bundleRelease -Pxiangqi.learningDb=/tmp/ccpd-only.sqlite3
"""

import argparse
import os
import shutil
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SOURCE = os.path.join(ROOT, "..", "xiangqi-mobile", "Resources", "Learning", "ccpd.sqlite3")
OUTPUT = os.path.join(ROOT, "app", "learning-lite", "ccpd.sqlite3")
GAME_CATEGORY = "對局"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default=DEFAULT_SOURCE)
    parser.add_argument("--games-per-group", type=int, default=1500, help="games kept from each collection (CCPD master, CCPD computer, WXF, Dongping)")
    parser.add_argument("--ccpd-only", action="store_true",
                        help="keep only the CC BY 4.0 CCPD records (drops the ICCS/WXF and ICCS/Dongping collections)")
    parser.add_argument("--output", default=OUTPUT, help="where to write the database")
    args = parser.parse_args()
    output = args.output

    os.makedirs(os.path.dirname(os.path.abspath(output)), exist_ok=True)
    if os.path.exists(output):
        os.remove(output)
    work = output + ".work"
    shutil.copyfile(args.source, work)
    db = sqlite3.connect(work)
    if args.ccpd_only:
        db.execute("DELETE FROM records WHERE source_path LIKE 'ICCS/%'")
        label = "ccpd-only"
    else:
        # Keep the first N games of each collection (the first two path components, e.g.
        # 'ICCS/WXF'), ordered by path so the sample is identical on every run.
        db.execute(
            """DELETE FROM records WHERE category = ? AND id NOT IN (
                   SELECT id FROM (
                       SELECT id, ROW_NUMBER() OVER (
                           PARTITION BY substr(source_path, 1,
                               instr(source_path, '/') + instr(substr(source_path, instr(source_path, '/') + 1), '/') - 1)
                           ORDER BY source_path) AS n
                       FROM records WHERE category = ?)
                   WHERE n <= ?)""",
            (GAME_CATEGORY, GAME_CATEGORY, args.games_per_group),
        )
        label = "lite"
    kept = db.execute("SELECT COUNT(*) FROM records").fetchone()[0]
    db.execute("INSERT OR REPLACE INTO metadata (key, value) VALUES ('imported_files', ?)", (str(kept),))
    db.execute("INSERT OR REPLACE INTO metadata (key, value) VALUES ('bundle', ?)", (label,))
    if args.ccpd_only:
        db.execute("INSERT OR REPLACE INTO metadata (key, value) VALUES ('license', 'CC BY 4.0')")
        db.execute("INSERT OR REPLACE INTO metadata (key, value) VALUES ('source_name', 'Chinese Chess Practical Dataset (CCPD)')")
        db.execute("DELETE FROM metadata WHERE key LIKE 'merged_%'")
    db.commit()
    db.execute("VACUUM INTO ?", (output,))
    db.close()
    os.remove(work)
    print(f"{kept} records ({label}), {os.path.getsize(output) / 1e6:.1f} MB -> {os.path.relpath(output, ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
