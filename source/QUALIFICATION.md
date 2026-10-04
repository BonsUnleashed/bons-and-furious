# Qualification — 2026-10-04

**PASS** for `bons_and_furious-neoforge-1.0.30+mc1.21.1.jar`.

SHA-256: `351cd7f41311348c307cf6c05c6020dcbc2d25f50d42b4b6672f1320468267d9` (1,312,881 bytes)

Tested with Minecraft 1.21.1, NeoForge 21.1.252 and Temurin Java 21.0.12.1. The same packaged JAR was used in all
15 production runs below. The development probe is a separate mod and is absent from the release JAR.
Evidence is in `../evidence/qualification.json` in the source bundle.

The JAR was built on a test PC (Intel Core Ultra 9 285K, RTX 4090); the runs took place on two test PCs (AMD Ryzen 9 5900X, RTX 3080; Intel Core Ultra 9 285K, RTX 4090). Each stage receipt in the qualification receipt names its PC. Compared with an earlier build of this port (SHA-256 `2fabf321936fd4ae…`), the JAR differs only in
`PureConfig.class` and `bons_and_furious.guards.tsv`: the configuration fix and the ServerCore step-aside below (`evidence/jar-diff-1030.json`).

It was reconciled with the frozen Forge 1.0.30 release (SHA-256 `3b32a46648a917e085f7fa8b21c749c869a409444008ddfbe790a10dd02d1a50`): every Forge
control is accounted for in PORT-COVERAGE.md, and every switch this port ships has the Forge release's default value.

| Check | Result |
| --- | --- |
| Gradle build and pinned guard verification | Pass: 161 guarded switches, 1,296 method/class-shape checks, 239 guarded mixins (243 mixin classes in all) |
| Original Forge and NeoForge name audits | Both pass; a deliberately wrong mod ID is rejected |
| Static mixin signature audit | 439 checks; the three known raw-class gaps are Embeddium's runtime-added methods, verified in its client run |
| Production fingerprints (installed 21.1.252 server and client jars) | No guard differs; 19 methods with reviewed layout variants (below) |
| Mixin crash-class sweep over 469 mod jars | No crash class: 23 overlaps, all allowed (lower-priority overwrites, stand-downs) or overwrite warnings |
| Production dedicated server, base game, switches on vs off | Identical blocks and biome hashes across 3,670,016 blocks in the Overworld, Nether and End |
| Production server, 56 target mods without C2ME and Dynamic Trees (TerraBlender, Oh The Biomes We've Gone and others), on vs off | Identical blocks and biome hashes in all three dimensions |
| The same with Dynamic Trees (57 mods) | Overworld and End identical. The Nether varies between two runs with the same settings (13,370 and 11,839 blocks, all in Dynamic Trees' fungus trees) as much as between on and off (12,445), so this fixture cannot show equality there |
| Base-game server, shadow mode, 1,200-tick scripted soak | Pass; 0 shadow mismatches |
| Server with 60 optional target mods incl. C2ME, shadow, 1,200-tick soak | Pass; 0 shadow mismatches; the C2ME stand-downs logged as designed |
| Server with 57 target mods without C2ME, shadow, 2,400-tick soak | Pass; 0 shadow mismatches |
| Production server with ServerCore 1.5.19 | Starts and runs; `vanilla_spawn_gate_visibility_memo` steps aside because ServerCore redirects the same call. A build without that step-aside does not start (below) |
| Production Iris client, 76 mods, Complementary Reimagined r5.9.3, shadow, 600 world ticks with scripted entities, drawers and block entities | World loaded and rendered (screenshot inspected); 0 shadow mismatches |
| Production Embeddium client without Iris or Sodium, 74 mods, shadow, 600 world ticks | World loaded and rendered, drawer count labels drawn (screenshot inspected); 0 shadow mismatches |
| Production Iris client with Accelerated Rendering 1.0.14 and shaders, 77 mods, shadow, 600 world ticks | World, chat and toast text rendered with shaders (screenshot inspected); 0 shadow mismatches. The published 1.0.27+mc1.21.1 crashes in this same run (below) |
| GeckoLib easing and Iris vertex outputs | 33,924 easing samples and 4,096 Iris vertices: hashes identical to the qualified 1.0.27 port |
| Target coverage | Every guarded switch obtains an APPLY decision in at least one production run |

## In-game shadow comparisons

With a switch's `-Dbons_and_furious.<name>.shadow=true` property set, its helper also computes the original answer
for every call and counts disagreements. Totals over the shadow runs (all mismatches 0):

| Helper (package) | Comparisons |
| --- | ---: |
| BlockStateAirFlag (mesh_air) | 1,394,869,695 |
| AdjacentFaceSkip (dh_loader) | 566,122,408 |
| BiomeBlendMemo (distanthorizons) | 515,416,282 |
| WrapperAirFlag (dh_loader) | 263,858,758 |
| NamespaceRuleMemo (terrablender) | 218,394,279 |
| LodBiomeMemo (distanthorizons) | 119,686,851 |
| AquiferCandidates (worldgen_aquifer) | 114,733,021 |
| TagIds (tag_ids) | 65,549,513 |
| BeardifierBounds (worldgen_beardifier_bounds) | 39,026,688 |
| LazyNamespaceRules (terrablender) | 28,867,081 |
| TickingChunkMemo (chunk_tick) | 12,966,731 |
| PooledStringIndex (dh_loader) | 7,420,852 |
| SpawnGate (spawn_gate) | 1,779,483 |
| GoalFlags (vanilla_goal_flags) | 1,577,572 |
| TickerGate (ticker_gate) | 1,470,540 |
| SeaLifeWaterlogged (state_memo) | 680,525 |
| BlockScans (vanilla_entity) | 604,896 |
| CuriosTagKeys (curios_tooltip) | 453,168 |
| ArtifactsTickOrder (artifacts) | 397,830 |
| EmptyBeardifiers (beardifier) | 283,392 |
| LionfishFluidWalk (fluidwalk_c2) | 252,177 |
| PartEntityCollisions (radium_fixes) | 206,930 |
| GameEventRegistries (vanilla_game_events) | 169,965 |
| SpriteFrameTimes (embeddium_sprites) | 141,366 |
| QuadSortKeys (dh_loader) | 64,229 |
| StripFormatting (vanilla_text) | 62,018 |
| DrawBatchCache (embeddium_draw) | 29,545 |
| MergedDraws (embeddium_draw) | 22,785 |
| CountLabels (storagedrawers) | 9,700 |
| EntityClassCounts (crittersandcompanions_c2) | 8,458 |
| EmptyShoulderSkip (mutantmonsters_c2) | 7,664 |
| ClipFast (vanilla_raycast) | 3,406 |
| QuadKeySort (embeddium_sorting) | 3,002 |
| LongJumpPicks (vanilla_long_jump) | 2,753 |
| DuplicateGroups (almostunified) | 2,043 |
| MergeCandidates (vanilla_item_merge) | 1,174 |
| SelectorPrefilter (datapack_selectors) | 1,046 |
| CheckMemo (structurify_c2) | 594 |
| SunBurnOrder (mob_sunburn) | 468 |
| TurtleEggSearch (vanilla_search) | 104 |
| RepellentSearch (vanilla_search) | 22 |
| BackgroundGrams (jei_search) | 3 |
| SortKeys (jei_search) | 3 |

Total: 3,355,149,020 comparisons, 0 mismatches.

10 helpers had no work in any run, although their mods were installed: nothing in the scripted scenes reached
their code. They are BakeLocations, CubeBakeMemo, CucumberTileDispatch, DhSqlScriptLookup, EmptyFilters, FramedMaps, RedPandaGate, SearchReplay, SortedConnections, SpawnerPresence. Their switches are guarded and applied where installed, and rest on static and
code-comparison evidence.

## Reviewed fingerprint variants

The production (Mojang) and NeoForm development versions of these methods have equivalent code with different
return, temporary-local or constant-pool layouts. Their bytecode was inspected and one extra fingerprint is accepted for
each: `Climate.Parameter.distance`, `CubicSampler.gaussianSampleVec3`, `NoiseChunk.wrapNew`, `EntityLookup.add`,
`ClassInstanceMultiMap.iterator`, `EntityGetter.getNearestEntity`, `MapItemSavedData.tickCarriedBy`,
`EuclideanGameEventListenerRegistry.visitInRangeListeners`, `LongJumpToRandomPos.pickCandidate`,
`LongJumpToPreferredBlock.getJumpCandidate`, `SingleValuePalette.idFor`, `PiglinSpecificSensor.isValidRepellent`,
`BlockPos$3.computeNext`, `Aquifer$NoiseBasedAquifer.computeSubstance` and `calculatePressure`, `Mth.clampedLerp`,
`Shapes.create`, `VertexBuffer.upload` and `ByteBufferBuilder$Result.close` (21 guard entries in all). The bytecode
comparisons are in `../evidence/variant-review/` and `../evidence/*-bytecode-diff.txt`; the first two methods were
reviewed for the 1.0.27 port and are in its source archive.

## Crash fixes checked in production

- Accelerated Rendering with Iris: the published 1.0.27+mc1.21.1 crashes while Minecraft starts in the Accelerated
  Rendering run above, with `InvalidInjectionException: @At("CONSTANT") on ModelToEntityVertexSerializer::modifyMidU
  with priority 1000 cannot inject into ... serialize(JJI)V merged by bons.furious.mixin.oculus...`. This port's
  overwrite runs at priority 999, so Accelerated Rendering's constant changes apply to it and the run passes.
- Embeddium without Iris: `vanilla_model_bone_lookup` stands down inside Embeddium's own overwrite; the Embeddium run
  starts and plays.
- Bye Pregen: `terrain_final_density_reuse` overwrites `DensityFunctions.MarkerOrMarked.mapAll` at priority 499, so
  Bye Pregen's priority 500 injector into that method is accepted and both run. That fix was reproduced and tested on
  the 1.0.27 port; here it is part of the build, with terrain identical on vs off as above.
- ServerCore: `vanilla_spawn_gate_visibility_memo` (new in 1.0.30) and ServerCore 1.5.19 both redirect
  `ServerLevel.isNaturalSpawningAllowed` in `ServerChunkCache.tickChunks`; two redirects of one call cannot both apply.
  The switch steps aside whenever ServerCore lists that mixin. A build without the step-aside stops at server start:
  Mixin skips ServerCore's redirect ("@Redirect conflict") and then fails with "Critical injection failure: Redirector
  servercore$skipUnloadedChunks" (`../evidence/production-p30-servercore-control.log`).
- Switches the bundled defaults ship `=false` now stay off for new installs and for players who update, until set to
  `true` (the same configuration fix as the Forge release).

## Integration decisions

- The 1.0.27 port is the starting point. The 1.0.28 to 1.0.30 additions came from the Forge source of the final 1.0.29
  and of 1.0.30, translated to Mojang names and adapted per target. The Forge sources were not changed.
- Of the 121 switches that 1.0.28 to 1.0.30 added, 83 are ported, 28 retired (most because the 1.21.1 target already
  does the work) and 10 have no 1.21.1 target mod. PORT-COVERAGE.md gives each reason.
- Five switches step aside while C2ME is installed (it replaces the same code); they were qualified in fixtures
  without C2ME. Two step-asides were added after a static crash-class sweep: `vanilla_beardifier_influence_bounds`
  (C2ME overwrites `Beardifier.compute`) and `vanilla_game_event_registry_lookup` (Lithium's game-event dispatch).
- The empty-beardifier switch also steps aside for Qliphoth Awakening, which adds code to `Beardifier`; the
  production logs show each step-aside with its reason.

## Limits and unrelated upstream messages

These are smoke tests, deterministic on/off comparisons and in-game shadow comparisons. They do not establish every
gameplay path, every option combination, long-session stability or a speed improvement.

- Two attempts on the Core Ultra 9 285K test PC ended in crashes of the Java runtime itself, not of the game: the JIT
  compiler (C2, `ShouldNotReachHere`, while compiling `NoiseBasedChunkGenerator.doFill`; none of the 174 methods in
  that compilation belongs to Bons and Furious, it held code from C2ME, YUNG's API, Radium and ModernFix), and a
  garbage-collector thread (access violation 13 seconds into a server start). The identical reruns passed. Evidence:
  `../evidence/c2-crash-p30-iris-shadow-attempt1.json`, `../evidence/jvm-crash-p30-tb-on-attempt1.json`.
- Citadel 2.7.1 has no 1.21.1 NeoForge consumer to render through; its switch is guarded and applies, but its model
  path did not run on a real model. LionfishAPI's ran through L_Ender's Cataclysm 3.33.
- Switches without a shadow mode (GeckoLib bone queues and quad vectors, Citadel/LionfishAPI vertices, FancyMenu,
  Pehkui, climate tree keys and spans, Distant Horizons byte stream) rest on the 1.20.1 proofs, the 1.21.1 code
  comparisons made for this port and clean in-game rendering.
- The Collections Of Optimizations step-aside rules are kept; no build of that mod exists for 1.21.1 NeoForge to test
  them against.
- The optional fixtures show upstream warnings that also appear with the switches off: Spawn's missing `roly_poly`
  loot item, Scorched's old function, client classes named by other mods' mixin configs on a dedicated server, and
  overwrite conflicts between other mods (Radium and ModernFix, CorgiLib and Oh The Trees You'll Grow).

## Test environment

All runtime tests used isolated, disposable game instances and worlds created for this qualification.
