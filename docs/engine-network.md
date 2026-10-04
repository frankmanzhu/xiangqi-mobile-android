# Pikafish network: provenance and terms

The app bundles one neural-network file for the Pikafish engine, `app/src/main/assets/engine/pikafish.nnue`.

| | |
| --- | --- |
| Size | 50,706,378 bytes |
| SHA-256 | `7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e` (verified at runtime before the engine opens it, and by `scripts/check_release.py`) |
| Engine revision it runs with | Pikafish `6a59ee2f7b105bff64d9efc2692591107787e2b1` (19 September 2026, "Simplify TT cutoff and penalize condition checks"), vendored in `app/src/main/cpp/pikafish` |
| How it was obtained | Fetched with the Pikafish tree's own `scripts/net.sh` (`make net`), which downloads `pikafish.nnue` from the official `official-pikafish/Networks` repository, release `master-net`. The file is byte-identical to `src/pikafish.nnue` in the iOS app's Pikafish checkout, dated 20 September 2026 |
| Same file as iOS | Yes: identical bytes and SHA-256 in `Resources/Engine/pikafish.nnue` of the iOS repository |

## Why the current download differs

The official `master-net` release asset is replaced whenever a new network is published, and it was replaced on **1 October 2026** (the asset now has SHA-256 `6b74ac7bbd299dc26a17803135b616eda9248bef0cbfc7b811bfcf981832ba29` and is 49,982,985 bytes). The bundled file is the network that was current when the pinned engine revision was integrated and tested; the app's tests and the iOS app's tests were run against exactly these bytes. Do **not** swap in today's download without re-running the engine tests, because a network is only guaranteed to match the engine revision it was trained for. The bundled bytes are stored in this repository, so the corresponding source and weights are always available together.

## Published terms

From the Networks repository's README ("NNUE-License"), checked 4 October 2026: the weights, and weights derived from them, are for **legal use only** and carry **no commercial use without permission** (a list of permitted organisations is published at https://pikafish.org/list.html). The weights Fairy-Stockfish trains for xiangqi are CC0 and are not covered by this licence.

Xiangqi Mobile is **free, with no advertising and no in-app purchases**, which is non-commercial use. Any change to that (ads, paid app, purchases) needs the weight owners' permission first. The in-app Licences screen carries the same notice (`assets/licenses/Pikafish-NNUE-NOTICE.txt`).
