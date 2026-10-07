# Qualification (2026-10-07)

**PASS** for `bons_and_furious-neoforge-1.0.34+mc1.21.1.jar`.

SHA-256: `28ce16731f1ef4192b5d25c5e6e6cb02b2d1d838b6b30d94d2fd423e6ec81895` (1,358,846 bytes)

Tested with Minecraft 1.21.1, NeoForge 21.1.252 and Temurin Java 21.0.12.1. The same packaged JAR was used in all
15 production runs below. The development probe is a separate mod and is absent from the release JAR.
Evidence is in `../evidence/qualification.json` in the source bundle.

The JAR was built and every run took place on one test PC (Intel Core Ultra 9 285K, RTX 4090); each stage receipt in the qualification
receipt names it. This version brings the port to Bons and Furious 1.0.34. Compared with the published
1.0.33+mc1.21.1 (SHA-256 `ff5e8bf07015e91b…`), the JAR adds 6 entries, changes
61 and removes none (`../evidence/jar-diff-1034.json`); the changes are listed under "New in this
version" below.

It was reconciled with the frozen Forge 1.0.34 release (SHA-256 `39caa1e5ad4753bde60807318617c2905c3334c1008e63ea948791155f3c4b66`): every one of
its 254 controls is accounted for in PORT-COVERAGE.md, and every
switch this port ships has the Forge release's default value.

| Check | Result |
| --- | --- |
| Gradle build and pinned guard verification | Pass: 166 guarded switches, 1,325 method/class-shape checks, 250 guarded mixins (254 mixin classes in all), 33 foreign mixins cancelled where a switch replaces them, 32 foreign patches stepped aside for |
| Original Forge and NeoForge name audits | Both pass; a deliberately wrong mod ID is rejected |
| Static mixin signature audit | 452 checks over 254 mixin classes; the 3 known raw-class gaps are Embeddium's runtime-added methods, verified in its client run |
| Production fingerprints (installed 21.1.252 server and client jars) | No guard differs; 21 guard entries with reviewed layout variants (below) |
| Mixin crash-class sweep over 470 mod jars | No crash class: 23 overlaps, all allowed (lower-priority overwrites, stand-downs) or overwrite warnings. The only change from 1.0.33+mc1.21.1: the 6 `embeddium_weighted_pick_table` overlaps are stand-downs now |
| Overwrite access audit over 471 mod jars (the Biolith crash class) | 87 overwrites; none is narrower than another mod's access transformer |
| Fields added to record classes | 9 instance fields on 4 record types, all transient: none is visible to Gson (4,160 records known in the tested mods and game jars) |
| Dragon Mounts Remastered 1.9.2 with a world data pack that sets a dragon breed (client) | The published 1.0.33+mc1.21.1 stays at 0 % with the reported Gson error; this JAR loads the world and passes (Gson check on the exported classes: FAIL before, PASS now) |
| Production dedicated server, base game, switches on vs off | Identical blocks and biome hashes across 3,670,016 blocks in the Overworld, Nether and End |
| Production server, 56 target mods without C2ME and Dynamic Trees (TerraBlender, Oh The Biomes We've Gone and others), on vs off | Identical blocks and biome hashes in all three dimensions |
| Base-game server, shadow mode, 1,200-tick scripted soak | Pass; 0 shadow mismatches |
| Server with 60 optional target mods incl. C2ME, shadow, 1,200-tick soak | Pass; 0 shadow mismatches; the C2ME step-asides logged as designed, including the three NoiseChunk switches |
| Server with 57 target mods without C2ME, shadow, 2,400-tick soak | Pass; 0 shadow mismatches |
| Production server with ServerCore 1.5.19 | Starts and runs; `vanilla_spawn_gate_visibility_memo` steps aside because ServerCore redirects the same call |
| Production server with Generator Accelerator 1.6.2 | Starts and runs; `vanilla_beardifier_influence_bounds` and `vanilla_climate_sample_xz_parts` step aside because Generator Accelerator overwrites both methods; `terrain_final_density_reuse` stands down at its hook on the same NoiseChunk call; terrain identical with the switches on and off. Generator Accelerator stopped by itself in 1 of 3 runs (below) |
| Production server with Bye Pregen 1.1.3.0 | Starts and runs; the terrain switches keep working next to it; terrain identical with the switches on and off |
| Production Iris client, 76 mods, Complementary Reimagined r5.9.3, shadow, 600 world ticks with scripted entities, drawers and block entities | World loaded and rendered with shaders; 0 shadow mismatches |
| Production Embeddium client without Iris or Sodium, 74 mods, shadow, 600 world ticks | World loaded and rendered; 0 shadow mismatches |
| Production Iris client with Accelerated Rendering 1.0.14 and shaders, 77 mods, shadow, 600 world ticks | World and text rendered with shaders; 0 shadow mismatches |
| Distant Horizons join fix (`distanthorizons_join_config_resend`) | Applied in all 3 client runs; singleplayer is left alone as designed (no resend). See the limits below |
| GeckoLib easing and Iris vertex outputs | Hashes identical to the qualified 1.0.27 port |
| JEI tooltip words (differential test on this JAR) | 200,000 random strings: the helper gives the same words in the same order as JEI 19.51's own split, and the same text as Minecraft's formatting removal |
| Target coverage | Every guarded switch obtains an APPLY decision in at least one production run |

## New in this version

- `distanthorizons_join_config_resend` (client, on by default), the one control Forge 1.0.34 added. When a client joins
  a server, Distant Horizons 3.3.3 sends its session config before it creates the threads that handle the server's
  messages; a reply that arrives in between is dropped (its log shows a bare "warn"), and the client then never asks
  the server for distant terrain. Right after those threads exist, a client world that has not received the server's
  config sends it again. Distant Horizons 3.3.3 has the same order of calls as the 3.3.2 build this was found on.
- The 9 fields our switches add to record classes (`ConnectingTextureType$QuadPredicatesKey`, `TagKey`, `Climate$Sampler`, `NamespacedSurfaceRuleSource$NamespacedRule`) are
  transient. Gson's record adapter looks for an accessor for every other field and failed on ours; Dragon Mounts
  Remastered encodes its dragon breeds with Gson while a client joins, so a world with a data-pack breed never
  finished loading (see the table).
- `distanthorizons_world_change_biome_reset` no longer loads Distant Horizons classes while a world starts: three hooks
  on the caches' static initializers record that they exist.
- A switch whose guard also covers a second mod now stays off with a debug line ("a mod it also needs is not
  installed") when that mod is absent, instead of a fingerprint warning.
- `embeddium_weighted_pick_table`'s hooks are optional, so a mod that replaces those methods makes the switch stand
  down instead of failing.
- Hostile Villages' pending chunk queue is emptied when the server stops (NeoForge's level tick event, which Hostile
  Villages 1.21-5.7 listens to).
- Checks that look at other mods' classes catch every error, so a class that cannot load on a dedicated server cannot
  stop it. JEI tooltip lines are read the way JEI itself reads them (as formatted text).

## In-game shadow comparisons

With a switch's `-Dbons_and_furious.<name>.shadow=true` property set, its helper also computes the original answer
for every call and counts disagreements. Totals over the shadow runs (all mismatches 0):

| Helper (package) | Comparisons |
| --- | ---: |
| BlockStateAirFlag (mesh_air) | 1,830,630,757 |
| AdjacentFaceSkip (dh_loader) | 778,633,918 |
| BiomeBlendMemo (distanthorizons) | 732,997,759 |
| WrapperAirFlag (dh_loader) | 374,387,794 |
| NamespaceRuleMemo (terrablender) | 293,073,813 |
| LodBiomeMemo (distanthorizons) | 154,868,725 |
| AquiferCandidates (worldgen_aquifer) | 114,733,021 |
| TagIds (tag_ids) | 75,760,744 |
| BeardifierBounds (worldgen_beardifier_bounds) | 39,026,688 |
| LazyNamespaceRules (terrablender) | 28,867,081 |
| TickingChunkMemo (chunk_tick) | 12,967,050 |
| PooledStringIndex (dh_loader) | 10,499,465 |
| SpawnGate (spawn_gate) | 1,642,617 |
| GoalFlags (vanilla_goal_flags) | 1,495,992 |
| TickerGate (ticker_gate) | 1,453,155 |
| SeaLifeWaterlogged (state_memo) | 654,551 |
| BlockScans (vanilla_entity) | 550,801 |
| CuriosTagKeys (curios_tooltip) | 453,168 |
| ArtifactsTickOrder (artifacts) | 346,456 |
| EmptyBeardifiers (beardifier) | 283,392 |
| LionfishFluidWalk (fluidwalk_c2) | 228,189 |
| PartEntityCollisions (radium_fixes) | 170,745 |
| SpriteFrameTimes (embeddium_sprites) | 141,108 |
| GameEventRegistries (vanilla_game_events) | 141,048 |
| QuadSortKeys (dh_loader) | 100,938 |
| DrawBatchCache (embeddium_draw) | 30,406 |
| StripFormatting (vanilla_text) | 24,772 |
| MergedDraws (embeddium_draw) | 23,602 |
| CountLabels (storagedrawers) | 8,271 |
| EntityClassCounts (crittersandcompanions_c2) | 6,422 |
| QuadKeySort (embeddium_sorting) | 6,340 |
| EmptyShoulderSkip (mutantmonsters_c2) | 6,340 |
| LongJumpPicks (vanilla_long_jump) | 3,304 |
| ClipFast (vanilla_raycast) | 3,303 |
| DhSqlScriptLookup (module_resources) | 2,223 |
| DuplicateGroups (almostunified) | 2,043 |
| MergeCandidates (vanilla_item_merge) | 1,056 |
| SelectorPrefilter (datapack_selectors) | 1,016 |
| CheckMemo (structurify_c2) | 648 |
| SunBurnOrder (mob_sunburn) | 292 |
| TurtleEggSearch (vanilla_search) | 70 |
| RepellentSearch (vanilla_search) | 20 |
| BackgroundGrams (jei_search) | 3 |
| SortKeys (jei_search) | 3 |

Total: 4,454,229,109 comparisons, 0 mismatches.

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
methods the new guard entries cover needed no variant. The bytecode comparisons (made for 1.0.30+mc1.21.1, unchanged)
are in `../evidence/variant-review/` and `../evidence/*-bytecode-diff.txt`; the first two methods were reviewed for the
1.0.27 port and are in its source archive.

## Crash fixes checked in production

- Dragon Mounts Remastered: see the table. The same scene with the published 1.0.33+mc1.21.1 reproduces the report.
- Generator Accelerator, Accelerated Rendering with Iris, Embeddium without Iris, Bye Pregen and ServerCore: the fixes
  of the earlier ports stay in place, and their runs above pass again with this JAR. Their negative controls are in the
  earlier source archives.

## Integration decisions

- The published 1.0.33+mc1.21.1 is the starting point. The 1.0.34 changes came from the Forge source of the frozen
  1.0.34 release, translated to Mojang names and merged file by file. The Forge sources were not changed.
- The control that 1.0.34 added is ported (`distanthorizons_join_config_resend`). In all: 170 controls ported,
  42 retired on 1.21.1, 42 without a 1.21.1 NeoForge build of their mod
  (PORT-COVERAGE.md gives each reason).
- Changes Forge 1.0.34 made to code that this port does not have (its retired controls and the mods without a 1.21.1
  build) are not carried. Two 1.0.34 additions are not needed here: the Radium expiring-ticket null guard protects code
  that our Radium switch turns on only on Forge (on 1.21.1 that switch turns on Radium's spawning option only), and the
  Pipez requirement check belongs to a gas-pipe hook this port never had.

## Limits and unrelated upstream messages

These are smoke tests, deterministic on/off comparisons and in-game shadow comparisons. They do not establish every
gameplay path, every option combination, long-session stability or a speed improvement.

- `distanthorizons_join_config_resend` acts only when a client joins a dedicated server. These client runs are
  singleplayer, so on 1.21.1 the hook was shown to apply and to leave singleplayer alone; the resend itself was tested
  on the Forge 1.0.34 build, and the Distant Horizons 3.3.3 code it relies on is unchanged (five guard entries check it).
- Citadel 2.7.1 has no 1.21.1 NeoForge consumer to render through; its switch is guarded and applies, but its model
  path did not run on a real model.
- Switches without a shadow mode (GeckoLib bone queues and quad vectors, Citadel/LionfishAPI vertices, FancyMenu,
  Pehkui, climate tree keys and spans, climate column parts, Distant Horizons byte stream, the JEI start-up switches)
  rest on the 1.20.1 proofs, the 1.21.1 code comparisons made for the port, the terrain comparisons, the differential
  test and clean in-game behaviour.
- The Collections Of Optimizations step-aside rules are kept; no build of that mod exists for 1.21.1 NeoForge to test
  them against.
- Generator Accelerator 1.6.2 stopped generating chunks by itself in 1 of the 3 runs with it
  here (spawn preparation never finished and nothing was logged until the time limit); the earlier port's tests showed
  the same with Generator Accelerator alone. The Generator Accelerator results above come from the runs that completed.
- The scripted client scene summons hostile mobs next to a survival player; they killed the probe player in
  3 of 3 client runs. The earlier port's tests showed the same with the
  published 1.0.30.1+mc1.21.1 and with every switch off. The probe, its checks and the shadow comparisons ran to the end
  in every run.
- The optional test setups show upstream warnings that also appear with the switches off, as in earlier versions.

## Test environment

All runtime tests used isolated, disposable game instances and worlds created for this qualification.
