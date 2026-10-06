# Qualification — 2026-10-06

**PASS** for `bons_and_furious-neoforge-1.0.33+mc1.21.1.jar`.

SHA-256: `ff5e8bf07015e91b6cb55da17db6ed77610b8e5df55b5f73e6b08ae84ac8aad3` (1,347,485 bytes)

Tested with Minecraft 1.21.1, NeoForge 21.1.252 and Temurin Java 21.0.12.1. The same packaged JAR was used in all
18 production runs below. The development probe is a separate mod and is absent from the release JAR.
Evidence is in `../evidence/qualification.json` in the source bundle.

The JAR was built and every run took place on one test PC (AMD Ryzen 9 3900X, RTX 3080); each stage receipt in the qualification
receipt names it. This version brings the port to Bons and Furious 1.0.33. Compared with the published
1.0.30.1+mc1.21.1 (SHA-256 `ebcd2416c3451e90…`), the JAR adds 20 entries, changes
18 and removes none (`../evidence/jar-diff-1033.json`); the changes are listed under "New in this
version" below.

It was reconciled with the frozen Forge 1.0.33 release (SHA-256 `dbb479dd0151538c904bec668413b3e71ccc989d9caf33522243ecf3eb2a4683`): every one of
its 253 controls is accounted for in PORT-COVERAGE.md, and every
switch this port ships has the Forge release's default value.

| Check | Result |
| --- | --- |
| Gradle build and pinned guard verification | Pass: 165 guarded switches, 1,320 method/class-shape checks, 246 guarded mixins (250 mixin classes in all), 33 foreign mixins cancelled where a switch replaces them, 32 foreign patches stepped aside for |
| Original Forge and NeoForge name audits | Both pass; a deliberately wrong mod ID is rejected |
| Static mixin signature audit | 451 checks over 250 mixin classes; the 3 known raw-class gaps are Embeddium's runtime-added methods, verified in its client run |
| Production fingerprints (installed 21.1.252 server and client jars) | No guard differs; 21 guard entries with reviewed layout variants (below) |
| Mixin crash-class sweep over 472 mod jars | No crash class: 24 overlaps, all allowed (lower-priority overwrites, stand-downs) or overwrite warnings |
| Overwrite access audit over 472 mod jars (the Biolith crash class) | 87 overwrites; none is narrower than another mod's access transformer |
| Production dedicated server, base game, switches on vs off | Identical blocks and biome hashes across 3,670,016 blocks in the Overworld, Nether and End |
| Production server, 56 target mods without C2ME and Dynamic Trees (TerraBlender, Oh The Biomes We've Gone and others), on vs off | Identical blocks and biome hashes in all three dimensions |
| Base-game server, shadow mode, 1,200-tick scripted soak | Pass; 0 shadow mismatches |
| Server with 60 optional target mods incl. C2ME, shadow, 1,200-tick soak | Pass; 0 shadow mismatches; the C2ME step-asides logged as designed, now including the three NoiseChunk switches |
| Server with 57 target mods without C2ME, shadow, 2,400-tick soak | Pass; 0 shadow mismatches |
| Production server with ServerCore 1.5.19 | Starts and runs; `vanilla_spawn_gate_visibility_memo` steps aside because ServerCore redirects the same call |
| Production server with Generator Accelerator 1.6.2 | Starts and runs; `vanilla_beardifier_influence_bounds` and `vanilla_climate_sample_xz_parts` step aside because Generator Accelerator overwrites both methods; terrain identical with the switches on and off. The published 1.0.30.1+mc1.21.1 stops in the same run (below) |
| Production server with Bye Pregen 1.1.3.0 | Starts and runs; the terrain switches keep working next to it; terrain identical with the switches on and off |
| Production Iris client, 76 mods, Complementary Reimagined r5.9.3, shadow, 600 world ticks with scripted entities, drawers and block entities | World loaded and rendered with shaders (screenshot inspected); 0 shadow mismatches |
| Production Embeddium client without Iris or Sodium, 74 mods, shadow, 600 world ticks | World loaded and rendered (screenshot inspected); 0 shadow mismatches |
| Production Iris client with Accelerated Rendering 1.0.14 and shaders, 77 mods, shadow, 600 world ticks | World, death-screen and toast text rendered with shaders (screenshot inspected); 0 shadow mismatches |
| GeckoLib easing and Iris vertex outputs | Hashes identical to the qualified 1.0.27 port |
| JEI tooltip words (differential test) | 200,000 random strings: the helper gives the same words in the same order as JEI 19.51's own split, and the same text as Minecraft's formatting removal |
| Target coverage | Every guarded switch obtains an APPLY decision in at least one production run |

## New in this version

- Four switches from Bons and Furious 1.0.31 to 1.0.33: `jei_brewing_lookup`, `jei_hidden_menu_sync`, `jei_tooltip_words`, `vanilla_climate_sample_xz_parts`. The JEI switches are
  adapted to JEI 19.51 (its tooltip word split differs from JEI 15's; the helper follows 19.51, proven by the
  differential test above). The climate switch remembered 8 and 9
  two-dimensional parts per column in the base game and keeps the original evaluation for samplers it does not know
  (such as C2ME's compiled density functions, met in every run with C2ME); its built-in
  comparison of the first samples with the original evaluation never disagreed.
- Retired: `forge_custom_payload_heap_copy` (a Forge 1.20.1 payload copy; Minecraft 1.21.1 decodes custom payloads
  before they reach that code, so there is nothing to copy).
- Call-site stand-down (the Forge 1.0.33 crash fix): `terrain_density_memo`, `terrain_final_density_reuse` and
  `vanilla_noise_wrap_presize` check, once every mod's mixins are applied, that no other mod hooks the NoiseChunk calls
  they prepare. Next to C2ME 0.4's density-function compiler all three stand down (one log line each); with nothing else
  on those calls they work as before. Next to Generator Accelerator: the NoiseChunk switch `terrain_final_density_reuse` stood down at its hook on the same call.
- Generator Accelerator 1.6.2 (NeoForge 1.21.1): it overwrites `Beardifier.compute` and `Climate.Sampler.sample` at the
  default priority. The published 1.0.30.1+mc1.21.1 does not get through the first world load next to it; Mixin
  refuses our handler with `InvalidInjectionException: @At("FIELD") on Beardifier::bons$rigids with priority 1000 cannot inject into Beardifier::compute ... merged by MixinBeardifier`. Both switches now step aside when Generator Accelerator lists those mixins.
- Game-layer resource index (`distanthorizons_sql_script_lookup_index`): the version check that the Forge 1.0.33
  release fixed also kept this switch off in 1.0.30+mc1.21.1 and 1.0.30.1+mc1.21.1, because the installed loader
  libraries carry a build suffix ("3.0.8+main…"). It is on now: no client run logged it off, and its helper made
  4,446 lookups with 0 mismatches in shadow mode.
- Guard start-up work from Forge 1.0.32/1.0.33: each target class is read once for all of its fingerprints, and the
  MixinSquared refinement scan searches faster, skips mods that are not loaded and logs a mod file it cannot read
  instead of failing.

## In-game shadow comparisons

With a switch's `-Dbons_and_furious.<name>.shadow=true` property set, its helper also computes the original answer
for every call and counts disagreements. Totals over the shadow runs (all mismatches 0):

| Helper (package) | Comparisons |
| --- | ---: |
| BlockStateAirFlag (mesh_air) | 2,230,624,169 |
| AdjacentFaceSkip (dh_loader) | 861,592,247 |
| BiomeBlendMemo (distanthorizons) | 697,215,468 |
| WrapperAirFlag (dh_loader) | 389,922,810 |
| NamespaceRuleMemo (terrablender) | 323,602,817 |
| LodBiomeMemo (distanthorizons) | 220,299,532 |
| AquiferCandidates (worldgen_aquifer) | 115,078,544 |
| TagIds (tag_ids) | 101,654,327 |
| BeardifierBounds (worldgen_beardifier_bounds) | 39,026,688 |
| LazyNamespaceRules (terrablender) | 29,021,677 |
| TickingChunkMemo (chunk_tick) | 12,968,569 |
| PooledStringIndex (dh_loader) | 10,880,271 |
| SpawnGate (spawn_gate) | 1,891,865 |
| GoalFlags (vanilla_goal_flags) | 1,706,349 |
| TickerGate (ticker_gate) | 1,668,730 |
| CuriosTagKeys (curios_tooltip) | 906,336 |
| SeaLifeWaterlogged (state_memo) | 696,869 |
| BlockScans (vanilla_entity) | 641,935 |
| ArtifactsTickOrder (artifacts) | 428,005 |
| SpriteFrameTimes (embeddium_sprites) | 283,775 |
| EmptyBeardifiers (beardifier) | 283,392 |
| LionfishFluidWalk (fluidwalk_c2) | 268,209 |
| PartEntityCollisions (radium_fixes) | 244,005 |
| GameEventRegistries (vanilla_game_events) | 191,538 |
| QuadSortKeys (dh_loader) | 81,486 |
| DrawBatchCache (embeddium_draw) | 56,623 |
| MergedDraws (embeddium_draw) | 44,000 |
| StripFormatting (vanilla_text) | 43,886 |
| CountLabels (storagedrawers) | 16,583 |
| EmptyShoulderSkip (mutantmonsters_c2) | 12,582 |
| QuadKeySort (embeddium_sorting) | 6,922 |
| EntityClassCounts (crittersandcompanions_c2) | 6,100 |
| ClipFast (vanilla_raycast) | 4,508 |
| DhSqlScriptLookup (module_resources) | 4,446 |
| LongJumpPicks (vanilla_long_jump) | 3,653 |
| DuplicateGroups (almostunified) | 3,282 |
| SelectorPrefilter (datapack_selectors) | 1,483 |
| MergeCandidates (vanilla_item_merge) | 1,382 |
| CheckMemo (structurify_c2) | 774 |
| SunBurnOrder (mob_sunburn) | 449 |
| TurtleEggSearch (vanilla_search) | 90 |
| RepellentSearch (vanilla_search) | 11 |
| BackgroundGrams (jei_search) | 6 |
| SortKeys (jei_search) | 6 |

Total: 5,041,386,399 comparisons, 0 mismatches.

9 helpers had no work in any run, although their mods were installed: nothing in the scripted scenes reached
their code. They are BakeLocations, CubeBakeMemo, CucumberTileDispatch, EmptyFilters, FramedMaps, RedPandaGate, SearchReplay, SortedConnections, SpawnerPresence. Their switches are guarded and applied where installed, and rest on static and
code-comparison evidence.

## Reviewed fingerprint variants

The production (Mojang) and NeoForm development versions of these methods have equivalent code with different
return, temporary-local or constant-pool layouts. Their bytecode was inspected and one extra fingerprint is accepted for
each: `Climate.Parameter.distance`, `CubicSampler.gaussianSampleVec3`, `NoiseChunk.wrapNew`, `EntityLookup.add`,
`ClassInstanceMultiMap.iterator`, `EntityGetter.getNearestEntity`, `MapItemSavedData.tickCarriedBy`,
`EuclideanGameEventListenerRegistry.visitInRangeListeners`, `LongJumpToRandomPos.pickCandidate`,
`LongJumpToPreferredBlock.getJumpCandidate`, `SingleValuePalette.idFor`, `PiglinSpecificSensor.isValidRepellent`,
`BlockPos$3.computeNext`, `Aquifer$NoiseBasedAquifer.computeSubstance` and `calculatePressure`, `Mth.clampedLerp`,
`Shapes.create`, `VertexBuffer.upload` and `ByteBufferBuilder$Result.close` (21 guard entries in all). The
methods the new switches guard needed no variant. The bytecode comparisons (made for 1.0.30+mc1.21.1, unchanged) are in
`../evidence/variant-review/` and `../evidence/*-bytecode-diff.txt`; the first two methods were reviewed for the 1.0.27
port and are in its source archive.

## Crash fixes checked in production

- Generator Accelerator 1.6.2: see above. The fixture with this JAR starts, generates and passes; the same fixture
  with the published 1.0.30.1+mc1.21.1 does not (`../evidence/production-p33-ga-control-10301.log`).
- Accelerated Rendering with Iris, Embeddium without Iris, Bye Pregen and ServerCore: the fixes of 1.0.30+mc1.21.1 stay
  in place, and their runs above pass again with this JAR. The negative controls for those fixes were run for
  1.0.30+mc1.21.1 and are in its source archive.
- Biolith: `vanilla_climate_rtree_flat_bounds` keeps the public overwrite of 1.0.30.1+mc1.21.1; the overwrite access
  audit above finds no other overwrite of that kind.

## Integration decisions

- The published 1.0.30.1+mc1.21.1 is the starting point. The 1.0.31 to 1.0.33 changes came from the Forge source of the
  frozen 1.0.33 release, translated to Mojang names and adapted per target. The Forge sources were not changed.
- Of the 5 switches that 1.0.31 to 1.0.33 added, 4 are ported and 1 retired.
  PORT-COVERAGE.md gives each reason. In all: 169 controls ported, 42
  retired on 1.21.1, 42 without a 1.21.1 NeoForge build of their mod.
- Two step-asides were added after a static crash-class sweep of this version: `vanilla_beardifier_influence_bounds`
  and `vanilla_climate_sample_xz_parts` for Generator Accelerator. The Forge line steps aside for Generator
  Accelerator's 1.20.1 build in the beardifier only; that build does not overwrite the climate sampler.
- The Forge release keeps Bye Pregen 1.1.2.4 (Forge 1.20.1) working next to the NoiseChunk switches with a reviewed
  pass-through entry. Bye Pregen 1.1.3.0 on 1.21.1 changes the result after the call instead, so this port needs none.

## Limits and unrelated upstream messages

These are smoke tests, deterministic on/off comparisons and in-game shadow comparisons. They do not establish every
gameplay path, every option combination, long-session stability or a speed improvement.

- Citadel 2.7.1 has no 1.21.1 NeoForge consumer to render through; its switch is guarded and applies, but its model
  path did not run on a real model. LionfishAPI's ran through L_Ender's Cataclysm.
- Switches without a shadow mode (GeckoLib bone queues and quad vectors, Citadel/LionfishAPI vertices, FancyMenu,
  Pehkui, climate tree keys and spans, climate column parts, Distant Horizons byte stream, the JEI start-up switches)
  rest on the 1.20.1 proofs, the 1.21.1 code comparisons made for this port, the terrain comparisons, the differential
  test and clean in-game behaviour.
- The Collections Of Optimizations step-aside rules are kept; no build of that mod exists for 1.21.1 NeoForge to test
  them against.
- Generator Accelerator 1.6.2 sometimes stopped generating chunks during these tests: spawn preparation or a chunk load
  never finished, every thread was idle and nothing was logged. It happened with Generator Accelerator alone
  (2 of 4 runs stopped), with every Bons and Furious switch off (3 of 4 runs stopped) and with them on (2 of 3 runs stopped); details in
  `../evidence/ga-hang-diagnostics.json`. The Generator Accelerator results above come from the runs that completed.
- The scripted client scene summons hostile mobs next to a survival player. On this test PC they killed the probe
  player in 5 of 6 client runs, and also with the published 1.0.30.1+mc1.21.1 (2 of 2) and with
  every switch off (1 of 2); the 1.0.30+mc1.21.1 runs on the other test PC never died. Other test servers shared this PC during the client runs
  (not during the server runs). The probe, its checks and the
  shadow comparisons ran to the end in every run.
- The optional fixtures show upstream warnings that also appear with the switches off, as in 1.0.30+mc1.21.1.

## Test environment

All runtime tests used isolated, disposable game instances and worlds created for this qualification.
