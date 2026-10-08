# Qualification (2026-10-09)

**PASS** for `bons_and_furious-neoforge-1.0.35+mc1.21.1.jar`.

SHA-256: `9c846abbf874ee01a7df2500b13363737afa4faa81f1800c6f0ca23275ce481a` (1,369,510 bytes)

Tested with Minecraft 1.21.1, NeoForge 21.1.252 and Temurin Java 21.0.12.1. The same packaged JAR was used in all
15 production runs below. The development probe is a separate mod and is absent from the release JAR.
Evidence is in `../evidence/qualification.json` in the source bundle.

The JAR was built and every run took place on one test PC (AMD Ryzen 9 3900X, RTX 3080); each stage receipt in the qualification
receipt names it. This version brings the port to Bons and Furious 1.0.35. Compared with the published
1.0.34+mc1.21.1 (SHA-256 `28ce16731f1ef419…`), the JAR adds 3 entries (the new control's
mixin, helper and mixin configuration), changes 3 (`META-INF/neoforge.mods.toml`, the default
properties and the guard table) and removes none; every other entry is byte-identical (`../evidence/jar-diff-1035.json`).

It was reconciled with the frozen Forge 1.0.35 release (SHA-256 `a424ab941346ac4709b1664f781739621d3ca83bc2d817aa92a2b6f8860f847d`): every one of
its 255 controls is accounted for in PORT-COVERAGE.md, and every
switch this port ships has the Forge release's default value.

| Check | Result |
| --- | --- |
| Gradle build and pinned guard verification | Pass: 167 guarded switches, 1,326 method/class-shape checks, 251 guarded mixins (255 mixin classes in all), 33 foreign mixins cancelled where a switch replaces them, 32 foreign patches stepped aside for |
| Original Forge and NeoForge name audits | Both pass; a deliberately wrong mod ID is rejected |
| Static mixin signature audit | 452 checks over 255 mixin classes; the 3 known raw-class gaps are Embeddium's runtime-added methods, verified in its client run |
| Production fingerprints (installed 21.1.252 server and client jars) | No guard differs; 21 guard entries with reviewed layout variants (below) |
| Mixin crash-class sweep over 470 mod jars | No crash class: 23 overlaps, all allowed (lower-priority overwrites, stand-downs) or overwrite warnings. No change from 1.0.34+mc1.21.1 |
| Overwrite access audit over 471 mod jars (the Biolith crash class) | 87 overwrites; none is narrower than another mod's access transformer |
| Fields added to record classes | 9 instance fields on 4 record types, all transient: none is visible to Gson (4,160 records known in the tested mods and game jars); the same fields as 1.0.34+mc1.21.1 |
| Dragon Mounts Remastered 1.9.2 with a world data pack that sets a dragon breed (client) | Checked in game on 1.0.34+mc1.21.1: the published 1.0.33+mc1.21.1 stays at 0 % (Gson check FAIL), 1.0.34+mc1.21.1 loads the world (Gson check PASS). This JAR has the same record fields and changes no class that 1.0.34+mc1.21.1 had |
| Production dedicated server, base game, switches on vs off | Identical blocks and biome hashes across 3,670,016 blocks in the Overworld, Nether and End |
| Production server, 56 target mods without C2ME and Dynamic Trees (TerraBlender, Oh The Biomes We've Gone and others), on vs off | Identical blocks and biome hashes in all three dimensions |
| Base-game server, shadow mode, 1,200-tick scripted soak | Pass; 0 shadow mismatches |
| Server with 60 optional target mods incl. C2ME, shadow, 1,200-tick soak | Pass; 0 shadow mismatches; the C2ME step-asides logged as designed, including the three NoiseChunk switches |
| Server with 57 target mods without C2ME, shadow, 2,400-tick soak | Pass; 0 shadow mismatches |
| Production server with ServerCore 1.5.19 | Starts and runs; `vanilla_spawn_gate_visibility_memo` steps aside because ServerCore redirects the same call |
| Production server with Generator Accelerator 1.6.2 | Starts and runs; `vanilla_beardifier_influence_bounds` and `vanilla_climate_sample_xz_parts` step aside because Generator Accelerator overwrites both methods; `terrain_final_density_reuse` stands down at its hook on the same NoiseChunk call; terrain identical with the switches on and off. Generator Accelerator stopped by itself in 5 of 7 runs (below) |
| Production server with Bye Pregen 1.1.3.0 | Starts and runs; the terrain switches keep working next to it; terrain identical with the switches on and off |
| Production Iris client, 76 mods, Complementary Reimagined r5.9.3, shadow, 600 world ticks with scripted entities, drawers and block entities | World loaded and rendered with shaders; 0 shadow mismatches |
| Production Embeddium client without Iris or Sodium, 74 mods, shadow, 600 world ticks | World loaded and rendered; 0 shadow mismatches |
| Production Iris client with Accelerated Rendering 1.0.14 and shaders, 77 mods, shadow, 600 world ticks | World and text rendered with shaders; 0 shadow mismatches |
| Mob class warm-up (`vanilla_mob_class_warmup`, new) | Applied and merged into NeoForge's `ServerLifecycleHooks` in all 11 runs with the switches on (8 dedicated servers, 3 clients' singleplayer servers): 1,290 to 2,967 classes loaded and linked per run in 289 to 1,563 ms on a background thread; none skipped; no WARN or ERROR line on its thread; absent in the 4 runs with every switch off |
| Distant Horizons join fix (`distanthorizons_join_config_resend`) | Applied in all 3 client runs; singleplayer is left alone as designed (no resend). See the limits below |
| GeckoLib easing and Iris vertex outputs | Hashes identical to the qualified 1.0.27 port |
| JEI tooltip words (differential test on this JAR) | 200,000 random strings: the helper gives the same words in the same order as JEI 19.51's own split, and the same text as Minecraft's formatting removal |
| Target coverage | Every guarded switch obtains an APPLY decision in at least one production run |

## New in this version

- `vanilla_mob_class_warmup` (both sides, on by default), the one control Forge 1.0.35 added. The first time each kind
  of mob spawns in a session, the server thread loads and verifies that mob's classes and the classes of its goals,
  brain behaviours, sensors and navigation; when night falls, many kinds spawn for the first time within seconds. Once a
  server has started (`ServerLifecycleHooks.handleServerStarted`, after the started event), a background thread loads
  and links, without initializing, the entity classes the registered entity types create, mod classes whose type chain
  reaches Minecraft's goal, behaviour, sensor, navigation, control or path-node types, and Minecraft's own classes
  under `net/minecraft/world/entity` and `net/minecraft/world/level/pathfinder`. Mixin classes and classes with a
  client-only type in their chain are skipped; a class whose constructor names a client-only type is only loaded.
  On the base-game server it took 319 ms for 1,290 classes (129
  entity, 332 AI classes from the scan data, 829 more vanilla entity and
  path-finding classes).
- The NeoForge port reads the mod list, its scan data and the game jar through NeoForge's mod loader (the same records
  as Forge's) and the entity types from the game's own registry.

## In-game shadow comparisons

With a switch's `-Dbons_and_furious.<name>.shadow=true` property set, its helper also computes the original answer
for every call and counts disagreements. Totals over the shadow runs (all mismatches 0):

| Helper (package) | Comparisons |
| --- | ---: |
| BlockStateAirFlag (mesh_air) | 1,164,315,720 |
| AdjacentFaceSkip (dh_loader) | 400,003,798 |
| BiomeBlendMemo (distanthorizons) | 303,868,586 |
| WrapperAirFlag (dh_loader) | 178,374,737 |
| NamespaceRuleMemo (terrablender) | 176,358,330 |
| AquiferCandidates (worldgen_aquifer) | 114,733,021 |
| LodBiomeMemo (distanthorizons) | 107,328,472 |
| TagIds (tag_ids) | 60,680,776 |
| BeardifierBounds (worldgen_beardifier_bounds) | 39,026,688 |
| LazyNamespaceRules (terrablender) | 28,867,081 |
| TickingChunkMemo (chunk_tick) | 12,967,456 |
| PooledStringIndex (dh_loader) | 5,023,293 |
| SpawnGate (spawn_gate) | 1,710,394 |
| GoalFlags (vanilla_goal_flags) | 1,549,526 |
| TickerGate (ticker_gate) | 1,424,602 |
| SeaLifeWaterlogged (state_memo) | 645,871 |
| BlockScans (vanilla_entity) | 532,716 |
| CuriosTagKeys (curios_tooltip) | 453,168 |
| ArtifactsTickOrder (artifacts) | 344,166 |
| EmptyBeardifiers (beardifier) | 283,392 |
| LionfishFluidWalk (fluidwalk_c2) | 226,694 |
| GameEventRegistries (vanilla_game_events) | 186,300 |
| PartEntityCollisions (radium_fixes) | 162,314 |
| SpriteFrameTimes (embeddium_sprites) | 142,236 |
| QuadSortKeys (dh_loader) | 33,452 |
| DrawBatchCache (embeddium_draw) | 27,825 |
| StripFormatting (vanilla_text) | 24,316 |
| MergedDraws (embeddium_draw) | 22,092 |
| EntityClassCounts (crittersandcompanions_c2) | 11,361 |
| EmptyShoulderSkip (mutantmonsters_c2) | 7,018 |
| CountLabels (storagedrawers) | 6,786 |
| QuadKeySort (embeddium_sorting) | 3,880 |
| LongJumpPicks (vanilla_long_jump) | 3,173 |
| ClipFast (vanilla_raycast) | 3,050 |
| DhSqlScriptLookup (module_resources) | 2,223 |
| DuplicateGroups (almostunified) | 2,043 |
| MergeCandidates (vanilla_item_merge) | 1,066 |
| SelectorPrefilter (datapack_selectors) | 954 |
| CheckMemo (structurify_c2) | 570 |
| SunBurnOrder (mob_sunburn) | 256 |
| TurtleEggSearch (vanilla_search) | 59 |
| RepellentSearch (vanilla_search) | 11 |
| SearchReplay (embeddium_search) | 8 |
| BackgroundGrams (jei_search) | 3 |
| SortKeys (jei_search) | 3 |

Total: 2,599,359,486 comparisons, 0 mismatches.

8 helpers had no work in any run, although their mods were installed: nothing in the scripted scenes reached
their code. They are BakeLocations, CubeBakeMemo, CucumberTileDispatch, EmptyFilters, FramedMaps, RedPandaGate, SortedConnections, SpawnerPresence. Their switches are guarded and applied where installed, and rest on static and
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
method the new guard entry covers (`ServerLifecycleHooks.handleServerStarted`, NeoForge's own code) needed no variant.
The bytecode comparisons (made for 1.0.30+mc1.21.1, unchanged) are in `../evidence/variant-review/` and
`../evidence/*-bytecode-diff.txt`; the first two methods were reviewed for the 1.0.27 port and are in its source archive.

## Crash fixes checked in production

- Dragon Mounts Remastered: see the table (checked in game on 1.0.34+mc1.21.1; the fields involved are unchanged).
- Generator Accelerator, Accelerated Rendering with Iris, Embeddium without Iris, Bye Pregen and ServerCore: the fixes
  of the earlier ports stay in place, and their runs above pass again with this JAR. Their negative controls are in the
  earlier source archives.

## Integration decisions

- The published 1.0.34+mc1.21.1 is the starting point. The 1.0.35 change came from the Forge source of the frozen
  1.0.35 release. Its four new files have no counterpart in the port, so they were translated by hand: NeoForge's
  `ServerLifecycleHooks`, mod list and scan-data classes instead of Forge's, and the game's entity type registry instead
  of `ForgeRegistries`. The Forge sources were not changed.
- The control that 1.0.35 added is ported (`vanilla_mob_class_warmup`). In all: 171 controls ported,
  42 retired on 1.21.1, 42 without a 1.21.1 NeoForge build of their mod
  (PORT-COVERAGE.md gives each reason). Forge 1.0.35 changed nothing else.

## Limits and unrelated upstream messages

These are smoke tests, deterministic on/off comparisons and in-game shadow comparisons. They do not establish every
gameplay path, every option combination, long-session stability or a speed improvement.

- `vanilla_mob_class_warmup` was checked for correctness here (hook applied, classes loaded and linked, a clean log,
  identical terrain with the switches on and off). Its effect on the lag spikes of first spawns was measured on Forge
  1.20.1 only; no timing is claimed for 1.21.1.
- `distanthorizons_join_config_resend` acts only when a client joins a dedicated server. These client runs are
  singleplayer, so on 1.21.1 the hook was shown to apply and to leave singleplayer alone; the resend itself was tested
  on the Forge 1.0.34 build, and the Distant Horizons 3.3.3 code it relies on is unchanged (five guard entries check it).
- Dragon Mounts Remastered was run in game with 1.0.34+mc1.21.1, not again with this JAR (see the table).
- Citadel 2.7.1 has no 1.21.1 NeoForge consumer to render through; its switch is guarded and applies, but its model
  path did not run on a real model.
- Switches without a shadow mode (GeckoLib bone queues and quad vectors, Citadel/LionfishAPI vertices, FancyMenu,
  Pehkui, climate tree keys and spans, climate column parts, Distant Horizons byte stream, the JEI start-up switches,
  the mob class warm-up) rest on the 1.20.1 proofs, the 1.21.1 code comparisons made for the port, the terrain
  comparisons, the differential test and clean in-game behaviour.
- The Collections Of Optimizations step-aside rules are kept; no build of that mod exists for 1.21.1 NeoForge to test
  them against.
- Generator Accelerator 1.6.2 stopped generating chunks by itself in 5 of the 7 runs with it
  here (spawn preparation never finished and nothing was logged until the time limit); the earlier ports' tests showed
  the same with Generator Accelerator alone. The Generator Accelerator results above come from the runs that completed.
- The scripted client scene summons hostile mobs next to a survival player; they killed the probe player in
  1 of 3 client runs. The earlier ports' tests showed the same with the
  published 1.0.30.1+mc1.21.1 and with every switch off. The probe, its checks and the shadow comparisons ran to the end
  in every run.
- The optional test setups show upstream warnings that also appear with the switches off, as in earlier versions.

## Test environment

All runtime tests used isolated, disposable game instances and worlds created for this qualification.
