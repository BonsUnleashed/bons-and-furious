# Bons and Furious — Minecraft 1.21.1 / NeoForge

This is the Minecraft 1.21.1 port of Bons and Furious 1.0.33. It uses mod ID `bons_and_furious`, version
`1.0.33+mc1.21.1`, and requires Java 21 and NeoForge 21.1.252 or newer. Minecraft is restricted to exactly 1.21.1.
The tested loader is 21.1.252; later loaders are not automatically qualified.

It builds on the published 1.0.30.1+mc1.21.1 (SHA-256
`ebcd2416c3451e9063f04ecea3f40e434445f632ebde4bf339b5f288ee866304`) and adds what 1.0.31, 1.0.32 and 1.0.33 added on
Forge 1.20.1, taken from the Forge 1.0.33 release (SHA-256
`dbb479dd0151538c904bec668413b3e71ccc989d9caf33522243ecf3eb2a4683`). The earlier ports are the qualified 1.0.27 port
(`1.0.27+mc1.21.1`, SHA-256 `04d25a1bbed6f8d3eb40e8c5de71195f0847d886db37b48459c4d6818147ab82`) and 1.0.30+mc1.21.1,
which added what 1.0.28 to 1.0.30 added. This port has its own artifact name and does not replace the Forge 1.20.1
release.

## Install

Put `bons_and_furious-neoforge-1.0.33+mc1.21.1.jar` in the `mods` folder of a Minecraft 1.21.1 NeoForge instance.
Optional target mods are not required; their patches only apply when the relevant targets and method fingerprints
match. MixinSquared is bundled; NeoForge supplies MixinExtras.

The first launch creates `config/bons_and_furious.properties`. Set a switch to `false` and restart to disable it.
Existing JVM disable flags remain supported. Client rendering patches only run on clients. The same JAR works on
dedicated servers, where client-only target mods should not be installed.

## Coverage and compatibility

The port ships **169 controls**: 165 switches guarded by 1,320 method or class-shape checks, three legacy mixin
controls and the Scorched function fix. Bons and Furious 1.0.33 has 253 controls in all; 42 are retired because the
1.21.1 target changed or already does the work, and 42 depend on target mods with no 1.21.1 NeoForge build.
[PORT-COVERAGE.md](PORT-COVERAGE.md) accounts for every one of them.

Of the 121 switches that 1.0.28 to 1.0.30 added, 83 are ported, 28 are retired (most because the 1.21.1 build of the
target already does it: Create 6.0.10, NeoForge's background SavedData writes, Minecraft 1.21.1's own network flush
batching, and others) and 10 have no 1.21.1 target. Of the five that 1.0.31 to 1.0.33 added, four are ported: the
three JEI start-up switches (`jei_brewing_lookup`, `jei_tooltip_words`, `jei_hidden_menu_sync`, adapted to JEI 19.51)
and `vanilla_climate_sample_xz_parts`. `forge_custom_payload_heap_copy` is retired: Minecraft 1.21.1 decodes custom
payloads before that code, so there is nothing to copy.

Six crash fixes are included. `oculus_entity_vertex_reuse` (Iris) runs at priority 999 so Accelerated Rendering's
offset changes apply to it (1.0.27+mc1.21.1 crashes with Accelerated Rendering and Iris). `vanilla_model_bone_lookup`
stands down inside a mod's own overwrite of that method (Embeddium without Iris). A switch that replaces another mod's
mixin steps aside while a third mod refines that mixin through MixinSquared. `terrain_final_density_reuse` overwrites
`MarkerOrMarked.mapAll` at priority 499, so Bye Pregen's injector into that method is accepted and both run.
`vanilla_climate_rtree_flat_bounds` declares its overwrite of `Climate.RTree.Node.distance` public, as Biolith (also
bundled in Quark) needs. New in 1.0.33+mc1.21.1: Generator Accelerator 1.6.2 replaces `Beardifier.compute` and
`Climate.Sampler.sample`; `vanilla_beardifier_influence_bounds` and `vanilla_climate_sample_xz_parts` step aside for it
(1.0.30+mc1.21.1 and 1.0.30.1+mc1.21.1 stop at the first world load next to it).

Also new in 1.0.33+mc1.21.1: `terrain_density_memo`, `terrain_final_density_reuse` and `vanilla_noise_wrap_presize`
check at start-up whether another mod hooks the NoiseChunk calls they prepare, and stand down if one does (C2ME's
density-function compiler does); and `distanthorizons_sql_script_lookup_index` is active again, after a version check
kept it off in 1.0.30+mc1.21.1 and 1.0.30.1+mc1.21.1.

The Oculus-prefixed switches target Iris. The tested Iris pairing is Iris 1.8.12 with Sodium 0.6.13. Embeddium 1.0.15
is tested separately; do not combine Embeddium with Sodium/Iris. Embeddium owns model bone lookup when installed.
Native Lithium owns block-entity state and POI indexing when installed. The Radium compatibility paths apply to
Radium 0.13.1, not native Lithium.

Switches that step aside on purpose, because another mod replaces the same code:
- with C2ME 0.4 (`0.4.0-alpha.0.122+1.21.1`, the reviewed build): `vanilla_ticking_chunk_memo`,
  `worldgen_empty_beardifier_marker`, `terrablender_lazy_namespace_rules`, `vanilla_aquifer_candidate_cache` and
  `vanilla_beardifier_influence_bounds` (C2ME's vanilla worldgen module overwrites Beardifier.compute);
- with Radium 0.13.1 at its defaults: `vanilla_long_jump_weighted_pick` and `vanilla_raycast_fluid_none`; with its
  optional modules on: `vanilla_game_event_registry_lookup`, `vanilla_item_merge_candidates`;
- with native Lithium's game-event dispatch: `vanilla_game_event_registry_lookup`;
- with ServerCore 1.5.19, which redirects the same call in `ServerChunkCache.tickChunks`: `vanilla_spawn_gate_visibility_memo`;
- with Generator Accelerator 1.6.2, which replaces both methods: `vanilla_beardifier_influence_bounds` and
  `vanilla_climate_sample_xz_parts`;
- with any mod that hooks the same NoiseChunk calls (C2ME 0.4's density-function compiler, Generator Accelerator's
  final-density hook): `terrain_density_memo`, `terrain_final_density_reuse` and `vanilla_noise_wrap_presize`, each
  for the call that mod hooks;
- `worldgen_empty_beardifier_marker` also steps aside when any other mod adds code to Beardifier;
- `vanilla_raycast_fluid_none` steps aside when any other mixin is merged into `BlockGetter.clip`.

Distant Horizons direct reads stay disabled when C2ME's replacement chunk IO is active. Restoring ModernFix's
stronghold cache still respects its user options. `artifacts_living_tick_order` keeps Artifacts' own order when
Accessories is installed, and with Curios 9.5.1 rests on one documented assumption (see PORT-COVERAGE.md).
`pipez_empty_filter_fast_path` covers item and fluid pipes; gas pipes keep Pipez's own check.

The foreign-patch coordination mechanism reads NeoForge's `[[mixins]]` metadata, including side lists and
`requiredMods`. Its Collections Of Optimizations rules are retained for matching declared mixins; no 1.21.1 NeoForge
build of that mod exists to test them against. Missing, changed or unknown targets stay unpatched.

Exact dependency versions, authors, licenses and hashes are in [upstream-credits.json](upstream-credits.json). They
describe development targets, including targets inspected and then retired, not required installation lists.

## Validation

See [QUALIFICATION.md](QUALIFICATION.md) and the accompanying qualification receipt for the exact tested artifact and
runtime results. The checks cover production server loading, optional mod integrations, in-world client rendering,
deterministic enabled/disabled comparisons and in-game shadow comparisons. They are not a comprehensive modpack or
long-session test. No 1.20.1 performance claim is reused for this port.

## Build

Use Python 3.11+ and a Java 21 JDK:

```text
python fetch_targets.py
gradlew.bat build
```

On Unix, use `./gradlew build`. The wrapper pins Gradle 9.2.1 and the build pins ModDevGradle 2.0.148 and NeoForge
21.1.252. The first build needs network access. `fetch_targets.py` verifies publisher SHA-1 values from
`targets.lock.json` and extracts nested compile targets. It writes into sibling `targets` and `targets-nested`
directories. It does not install those mods into a game.

`build` checks all fingerprints against the pinned targets and refuses a stale guard table. Do not refresh fingerprints
merely to silence a mismatch: review the target's behavior first. Nineteen methods have explicitly reviewed alternate
fingerprints for the equivalent NeoForm development and Mojang production instruction layouts (21 guard entries). No
wildcard hashes are accepted.

The output is in `build/libs/`. `src/probe` is a separate qualification mod; `gradlew.bat probeJar` builds it
separately. It is excluded from the release JAR. The archive order, timestamps and compression are deterministic.

License: GPL-3.0-only; see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Optional target JARs, Minecraft, shader
packs, game worlds and local account data are not included in the source archive.
