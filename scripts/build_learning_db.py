#!/usr/bin/env python3
"""Build the learning database bundled with the Android app.

Builds app/learning-lite/ccpd.sqlite3, the small fallback committed to git.

The real app bundle uses the full 205 MB corpus straight from the iOS repo (see
app/build.gradle.kts, `learningDatabase`). The lite subset only exists so that
a clone without the iOS checkout - CI, a contributor - still builds and runs:
every study/puzzle category in full plus a deterministic, equal-sized sample from each game collection
(CCPD master, CCPD computer, WXF, Dongping) so every filter in the app has data.

    scripts/build_learning_db.py [--source PATH] [--games-per-group N]
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
    args = parser.parse_args()

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    if os.path.exists(OUTPUT):
        os.remove(OUTPUT)
    work = OUTPUT + ".work"
    shutil.copyfile(args.source, work)
    db = sqlite3.connect(work)
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
    kept = db.execute("SELECT COUNT(*) FROM records").fetchone()[0]
    db.execute("INSERT OR REPLACE INTO metadata (key, value) VALUES ('imported_files', ?)", (str(kept),))
    db.execute("INSERT OR REPLACE INTO metadata (key, value) VALUES ('bundle', 'lite')")
    db.commit()
    db.execute("VACUUM INTO ?", (OUTPUT,))
    db.close()
    os.remove(work)
    print(f"{kept} records, {os.path.getsize(OUTPUT) / 1e6:.1f} MB -> {os.path.relpath(OUTPUT, ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
