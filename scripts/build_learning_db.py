#!/usr/bin/env python3
"""Build the learning database bundled with the Android app.

The iOS app ships the full 205 MB CCPD corpus, which is too large for a git
repository (GitHub rejects files over 100 MB). The Android app commits a "lite"
subset instead: every study/puzzle category in full, plus a deterministic sample
of the game collections. Use --full to bundle the whole corpus into a local build.

    scripts/build_learning_db.py [--source PATH] [--games-per-group N] [--full]
"""

import argparse
import os
import shutil
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SOURCE = os.path.join(ROOT, "..", "xiangqi-mobile", "Resources", "Learning", "ccpd.sqlite3")
OUTPUT = os.path.join(ROOT, "app", "src", "main", "assets", "learning", "ccpd.sqlite3")
GAME_CATEGORY = "對局"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default=DEFAULT_SOURCE)
    parser.add_argument("--games-per-group", type=int, default=2500)
    parser.add_argument("--full", action="store_true", help="copy the whole corpus (do not commit)")
    args = parser.parse_args()

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    if os.path.exists(OUTPUT):
        os.remove(OUTPUT)
    if args.full:
        shutil.copyfile(args.source, OUTPUT)
        print(f"copied full corpus -> {OUTPUT}")
        return 0

    work = OUTPUT + ".work"
    shutil.copyfile(args.source, work)
    db = sqlite3.connect(work)
    # Keep the first N games of each source group, ordered by path so the
    # sample is identical on every run.
    db.execute(
        """DELETE FROM records WHERE category = ? AND id NOT IN (
               SELECT id FROM (
                   SELECT id, ROW_NUMBER() OVER (
                       PARTITION BY substr(source_path, 1, instr(source_path, '/') - 1)
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
