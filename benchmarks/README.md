# Whole-modpack measurements

`1.0.26/summary.json`: 2 October 2026, latest measured release, server C2ME on.
`1.0.21/summary.json`: 30 September 2026, retained clean same-scene FPS test;
C2ME on the client, off on the dedicated server.

Each summary contains per-run data and comparisons. Main comparisons use four
runs per arm in ABBA-ABBA order on the same PC and seed. Attribution arms are
single runs. Lower method cost does not imply a proportional FPS gain.
The 1.0.26 saved-world absent arm froze its integrated server in all four runs:
its rendering comparisons are confounded and must not be used as clean FPS gains.
The new-world arms draw different amounts of terrain and are also unsuitable
for a like-for-like FPS claim.

Baseline optimization mods, settings, hardware, individual runs, workload
definitions, stability observations and world-identity checks are documented
in the [wiki](https://github.com/BonsUnleashed/bons-and-furious/wiki/Whole-modpack-benchmark).
