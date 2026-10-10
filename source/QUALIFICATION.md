# Hotfix 1.0.39+mc1.21.1 (2026-10-10)

**PASS** for `bons_and_furious-neoforge-1.0.39+mc1.21.1.jar`, SHA-256
`0fdf2fb35e7791e1216fbb26605b12e0be0ccf1f9858f2950044189fbb1958ea`.

One change, the same as Forge 1.0.39: `vanilla_chunk_palette_direct_nbt` leaves chunk sections that another mod keeps
in its own block storage to the regular writer. Bye Pregen keeps chunks that are still being generated in its own
storage and writes them itself; 1.0.38+mc1.21.1 asked that storage for a step it refuses, so those chunks were not
saved whenever they reached the regular writer. Evidence is in `../evidence/hotfix-1.0.39/`.

| Check | Result |
| --- | --- |
| Build | The unchanged 1.0.38+mc1.21.1 source rebuilds to the published JAR byte for byte; the hotfix JAR differs from it only in 4 entries: the switch's class and its nested record, and the version text |
| Reproduced | 1.0.38+mc1.21.1 with Bye Pregen 1.1.3.1 and a mod that listens for chunk saves: 3,214 failed chunk saves; only 113 of 1,741 chunks reached the disk |
| Not affected by default | 1.0.38+mc1.21.1 with Bye Pregen alone: its own chunk saver stays on, no failed save |
| Workaround | The same mods with `vanilla_chunk_palette_direct_nbt=false`: no failed save, all 1,741 chunks saved |
| Fixed | Hotfix with Bye Pregen and the chunk-save listener: no failed save, all 1,741 chunks saved, one log line for Bye Pregen's storage; the terrain of every dimension equals 1.0.38+mc1.21.1 |
| Without Bye Pregen | Hotfix and 1.0.38+mc1.21.1 give identical terrain and identical decisions for every probed switch |
| Static checks | Guards (Gradle build), name audit, crash-class check over 488 jars and access check over 489 jars: the same result as 1.0.38+mc1.21.1, no crashing kind |
| Client | The Embeddium client without Iris (76 mods) loaded a world and rendered it; 919,326,310 shadow comparisons, 0 mismatches |

These are dedicated-server runs and one client run; a singleplayer world runs the same server code. The rest of this
file is the qualification of 1.0.38+mc1.21.1, which this hotfix otherwise equals.

# Qualification (2026-10-10)

**PASS** for `bons_and_furious-neoforge-1.0.38+mc1.21.1.jar`.

SHA-256: `e5b600a0008c1abadb81e99182873072d8fd0afddca403e4e44e8adcb3edaec9` (1,630,806 bytes)

Tested with Minecraft 1.21.1, NeoForge 21.1.252 and Temurin Java 21.0.12.1. The same packaged JAR was used in all
16 production runs below and in the 3 Sable runs. The development probe is a separate mod
and is absent from the release JAR. Evidence is in `../evidence/qualification.json` in the source bundle.

The JAR was built and every run took place on one test PC (AMD Ryzen 9 3900X, RTX 3080); each stage receipt in the qualification
receipt names it. This version brings the port to Bons and Furious 1.0.38: the switches Forge 1.0.36 added, Forge
1.0.38's MixinSquared cancellation check, and the fix for Sable (below). Compared with the published 1.0.35+mc1.21.1
(SHA-256 `9c846abbf874ee01…`), the JAR adds 144 entries (the new switches' mixins, helpers and
mixin configurations), changes 39 (the guard classes, the groups whose Forge 1.0.36 changes were merged,
`META-INF/neoforge.mods.toml`, the default properties, the guard table, the notices and the credits) and removes none;
every other entry is byte-identical (`../evidence/jar-diff-1036.json`).

It was reconciled with the frozen Forge 1.0.36 release (SHA-256 `96ec1b62d1c109cf6e24326ca4f1f50d4ee77d4ed715751bcad204ee17adf88c`) and with Forge
1.0.38 (SHA-256 `4e78bd0684f99cdbda8cf060e52e00f1196eba07057610f44e677748d5c0cba6`), which has the same switches and defaults plus the cancellation
check this port carries: every one of their 306 controls is accounted
for in PORT-COVERAGE.md, and every switch this port ships has the Forge release's default value.

| Check | Result |
| --- | --- |
| Gradle build and pinned guard verification | Pass: 201 guarded switches, 1,726 method/class-shape checks, 311 guarded mixins (315 mixin classes in all), 65 foreign mixins cancelled where a switch replaces them, 68 foreign patches stepped aside for |
| Original Forge and NeoForge name audits | Both pass; a deliberately wrong mod ID is rejected |
| Static mixin signature audit | 553 checks over 315 mixin classes; the 3 known raw-class gaps are Embeddium's runtime-added methods, verified in its client run |
| Production fingerprints (installed 21.1.252 server and client jars) | No guard differs; 41 guard entries with reviewed layout variants (below) |
| Mixin crash-class sweep over 488 mod jars, Sable 2.0.6 and Create Aeronautics 1.3.2 included | No crash class: 25 overlaps, all allowed (lower-priority overwrites, stand-downs) or overwrite warnings. Differences from 1.0.35+mc1.21.1: 2 rows, all naming this version's new or changed switches and all allowed; the published 1.0.35+mc1.21.1 shows the Sable crash class, this JAR does not |
| Overwrite access audit over 489 mod jars (the Biolith crash class) | 88 overwrites; none is narrower than another mod's access transformer |
| MixinSquared canceller census over 488 mod jars | 1 foreign canceller (`GAMixinCanceller`); none names a mixin that a switch here changes |
| Fields added to record classes | 9 instance fields on 4 record types, all transient: none is visible to Gson (4,496 records known in the tested mods and game jars); the same fields as 1.0.35+mc1.21.1 |
| Sable 2.0.6 + Storage Drawers 13.11.4, dedicated server | Starts and runs; one line says `storagedrawers_count_sync_holders` leaves Sable's `PlayerList.broadcast` alone (the published 1.0.35+mc1.21.1 stops here) |
| Create Aeronautics 1.3.2 stack (Sable, Create 6.0.10) + Storage Drawers, dedicated server with the probe | Starts and runs; the same single line; probe passed |
| The reported case: Create Aeronautics + Storage Drawers in singleplayer | World loads and runs; drawer count updates and block events go through Sable's broadcast; every scripted command succeeded |
| Production dedicated server, base game, switches on vs off | Identical blocks and biome hashes across 3,670,016 blocks in the Overworld, Nether and End |
| Production server, 56 target mods without C2ME and Dynamic Trees (TerraBlender, Oh The Biomes We've Gone and others), on vs off | Identical blocks and biome hashes in all three dimensions |
| Base-game server, shadow mode, 1,200-tick scripted soak | Pass; 0 shadow mismatches |
| Server with 60 optional target mods incl. C2ME, shadow, 1,200-tick soak | Pass; 0 shadow mismatches; the C2ME step-asides logged as designed |
| Server with 57 target mods without C2ME, shadow, 2,400-tick soak | Pass; 0 shadow mismatches |
| Production server with ServerCore 1.5.19 | Starts and runs; `vanilla_spawn_gate_visibility_memo` steps aside because ServerCore redirects the same call |
| Production server with Generator Accelerator 1.6.2 | Starts and runs; the switches whose methods Generator Accelerator overwrites step aside; `terrain_final_density_reuse` stands down at its hook on the same NoiseChunk call; terrain identical with the switches on and off. Generator Accelerator stopped by itself in 2 of 4 runs (below) |
| Production server with Bye Pregen 1.1.3.0 | Starts and runs; the terrain switches keep working next to it, and `vanilla_climate_last_result_weak` steps aside because Bye Pregen keeps its own search state in the same field; terrain identical with the switches on and off |
| Production Iris client, 76 mods, Complementary Reimagined r5.9.3, shadow, 600 world ticks with scripted entities (glowing on and off, Cataclysm bosses), drawers and block entities | World loaded and rendered with shaders; 0 shadow mismatches |
| Production Embeddium client without Iris or Sodium, 74 mods, shadow, 600 world ticks | World loaded and rendered; 0 shadow mismatches |
| Production Iris client with Accelerated Rendering 1.0.14 and shaders, 77 mods, shadow, 600 world ticks | World and text rendered with shaders; 0 shadow mismatches |
| Production Iris client with Jaden's Nether Expansion 2.4.1 and Aquaculture 2.7.21, 80 mods, shadow, their custom-rendered items in the hotbar | World loaded and rendered; 0 shadow mismatches |
| Mob class warm-up (`vanilla_mob_class_warmup`) | Applied in all 12 runs with the switches on: 1,290 to 3,020 classes loaded and linked per run in 306 to 1,305 ms on a background thread; no WARN or ERROR line on its thread; absent in the 4 runs with every switch off |
| Distant Horizons join fix (`distanthorizons_join_config_resend`) | Applied in all 4 client runs; singleplayer is left alone as designed (no resend) |
| GeckoLib easing and Iris vertex outputs | Hashes identical to the qualified 1.0.27 port |
| JEI tooltip words (differential test on this JAR) | 200,000 random strings: the helper gives the same words in the same order as JEI 19.51's own split, and the same text as Minecraft's formatting removal |
| Target coverage | Every guarded switch obtains an APPLY decision in at least one production run, the 34 new ones included |

## New in this version

- Forge 1.0.36 added 51 switches: 34 are ported here, on by default as in Forge;
  13 were retired because their 1.21.1 target changed or was fixed upstream, and
  4 target mods have no NeoForge 1.21.1 build. PORT-COVERAGE.md gives every reason. The ported
  ones cover L_Ender's Cataclysm 3.33 (attack-area and beam scans, leg solvers, coral swimming checks, the Monstrous
  Helm scan, boss block breaking, idle animations, pose-hand events, shield layer poses and two client leaks), Lionfish
  API 3.1 models (part lists, animator transforms), item renderers built once per item for Aquaculture and Jaden's
  Nether Expansion, Distant Horizons 3.3.3 (LOD output streams, tint neighbour lookups), Entity Model Features 3.3.9
  (variable defaults, anger map), and Minecraft itself: the entity outline pass while nothing is outlined, chunk saving
  (block state codecs, biome codec, palettes), inventory advancement triggers, entity selectors by type and by tag,
  terrain noise columns, cells and corners, the climate search, basalt columns, the nearest-structure search and the
  structure template cache.
- Sable 2.0.6 (the physics library of Create Aeronautics): with Storage Drawers installed, every port since
  1.0.30+mc1.21.1 stopped while a world started, because Mixin refused our `PlayerList.broadcast` hook inside Sable's
  replacement of that method. The hook now runs at a higher priority and steps aside, with one log line, when another
  mod replaces the method; Sable's method stays as it ships.
- From Forge 1.0.38: a switch that changes another mod's mixin steps aside, with one log line, when a third mod cancels
  that mixin through MixinSquared, instead of stopping the game at start.
- A mod whose own patch cannot be checked is now left as it ships: the switch that would replace that patch steps
  aside and nothing of that mod is cancelled.
- Forge 1.0.36's changes to existing switches are merged (entity selector type index, terrain, beardifier bounds).

## In-game shadow comparisons

With a switch's `-Dbons_and_furious.<name>.shadow=true` property set, its helper also computes the original answer
for every call and counts disagreements. Totals over the shadow runs (all mismatches 0):

| Helper (package) | Comparisons |
| --- | ---: |
| BlockStateAirFlag (mesh_air) | 1,577,776,913 |
| AdjacentFaceSkip (dh_loader) | 596,452,146 |
| TintColumns (distanthorizons_tint) | 537,419,408 |
| BiomeBlendMemo (distanthorizons) | 460,147,163 |
| WrapperAirFlag (dh_loader) | 266,354,376 |
| NamespaceRuleMemo (terrablender) | 229,738,611 |
| LodBiomeMemo (distanthorizons) | 149,905,617 |
| AquiferCandidates (worldgen_aquifer) | 114,733,021 |
| TagIds (tag_ids) | 76,323,100 |
| BeardifierBounds (worldgen_beardifier_bounds) | 39,026,688 |
| LazyNamespaceRules (terrablender) | 28,867,081 |
| WeakLastResult (climate_last_result) | 20,983,582 |
| TickingChunkMemo (chunk_tick) | 12,967,138 |
| PooledStringIndex (dh_loader) | 7,721,912 |
| SpawnGate (spawn_gate) | 1,755,105 |
| TickerGate (ticker_gate) | 1,552,874 |
| GoalFlags (vanilla_goal_flags) | 1,478,464 |
| PaletteDirectNbt (vanilla_chunk_codecs) | 1,355,693 |
| SingleTypeTest (datapack_selectors) | 891,627 |
| TagIndex (datapack_selectors) | 869,753 |
| SeaLifeWaterlogged (state_memo) | 690,009 |
| CuriosTagKeys (curios_tooltip) | 613,296 |
| StructureTemplateHits (vanilla_structure_templates) | 572,752 |
| BlockScans (vanilla_entity) | 561,083 |
| ArtifactsTickOrder (artifacts) | 381,572 |
| EmptyBeardifiers (beardifier) | 283,392 |
| GameEventRegistries (vanilla_game_events) | 231,930 |
| LionfishFluidWalk (fluidwalk_c2) | 225,758 |
| PartEntityCollisions (radium_fixes) | 185,235 |
| SpriteFrameTimes (embeddium_sprites) | 142,682 |
| ColumnCellFill (vanilla_cellfill) | 110,515 |
| ListenerIndex (vanilla_inventory_index) | 84,618 |
| StateCodecMemo (vanilla_chunk_codecs) | 70,990 |
| QuadSortKeys (dh_loader) | 55,558 |
| StripFormatting (vanilla_text) | 29,092 |
| DrawBatchCache (embeddium_draw) | 26,584 |
| LionfishModelParts (lionfish_models) | 25,005 |
| CataclysmPoseHand (cataclysm_client) | 22,140 |
| CitadelPoseHand (cataclysm_client) | 22,140 |
| MergedDraws (embeddium_draw) | 21,874 |
| UnlockedOutputStream (distanthorizons_streams) | 17,256 |
| CountLabels (storagedrawers) | 11,979 |
| EntityClassCounts (crittersandcompanions_c2) | 11,908 |
| EmptyShoulderSkip (mutantmonsters_c2) | 8,326 |
| ShieldLayerPose (cataclysm_client) | 7,775 |
| CornerShare (vanilla_corner_table) | 4,850 |
| QuadKeySort (embeddium_sorting) | 4,489 |
| BasaltGuards (basalt_guards) | 3,871 |
| OutlineSkip (vanilla_outline_skip) | 3,813 |
| LongJumpPicks (vanilla_long_jump) | 3,391 |
| ClipFast (vanilla_raycast) | 3,234 |
| DhSqlScriptLookup (module_resources) | 2,964 |
| DuplicateGroups (almostunified) | 2,464 |
| ItemRendererReuse (item_renderers) | 2,204 |
| MergeCandidates (vanilla_item_merge) | 1,207 |
| SelectorPrefilter (datapack_selectors) | 1,132 |
| CheckMemo (structurify_c2) | 636 |
| SunBurnOrder (mob_sunburn) | 303 |
| BiomeCodecMemo (vanilla_chunk_codecs) | 176 |
| TurtleEggSearch (vanilla_search) | 63 |
| ColumnSummary (terrain) | 60 |
| RepellentSearch (vanilla_search) | 17 |
| BackgroundGrams (jei_search) | 4 |
| SortKeys (jei_search) | 4 |

Total: 4,130,768,623 comparisons, 0 mismatches.

10 helpers had no work in any run, although their mods were installed: nothing in the scripted scenes reached
their code. They are BakeLocations, CubeBakeMemo, CucumberTileDispatch, EmptyFilters, FramedMaps, RedPandaGate, SearchReplay, SortedConnections, SpawnerPresence, StructureRing. Their switches are guarded and applied where installed, and rest
on static and code-comparison evidence.

## Reviewed fingerprint variants

The production (Mojang) and NeoForm development versions of these methods have equivalent code with different
return, branch, temporary-local or constant-pool layouts. Their bytecode was inspected and one extra fingerprint is
accepted for each (41 guard entries in all): `Aquifer$NoiseBasedAquifer.calculatePressure`, `Aquifer$NoiseBasedAquifer.computeSubstance`, `BasaltColumnsFeature.canPlaceAt`, `BlockEntityWithoutLevelRenderer.renderByItem`, `BlockPos$3.computeNext`, `ByteBufferBuilder$Result.close`, `ClassInstanceMultiMap.iterator`, `Climate$Parameter.distance`, `CubicSampler.gaussianSampleVec3`, `EntityGetter.getNearestEntity`, `EntityLookup.add`, `EntitySelectorOptions.lambda$bootStrap$46`, `EuclideanGameEventListenerRegistry.visitInRangeListeners`, `InventoryChangeTrigger$TriggerInstance$Slots.matches`, `InventoryChangeTrigger$TriggerInstance.matches`, `LongJumpToPreferredBlock.getJumpCandidate`, `LongJumpToRandomPos.pickCandidate`, `MapItemSavedData.tickCarriedBy`, `Mth.clampedLerp`, `NbtOps$NbtRecordBuilder.build`, `NoiseChunk$CacheAllInCell.compute`, `NoiseChunk$CacheOnce.fillArray`, `NoiseChunk$FlatCache.compute`, `NoiseChunk$NoiseInterpolator.compute`, `NoiseChunk$NoiseInterpolator.fillArray`, `NoiseChunk.wrapNew`, `PiglinSpecificSensor.isValidRepellent`, `Shapes.create`, `SimpleCriterionTrigger.trigger`, `SingleValuePalette.idFor`, `StateHolder.lambda$codec$2`, `StructureTemplateManager.save`, `VertexBuffer.upload`. The comparisons are in
`../evidence/variant-review/`, `../evidence/*-bytecode-diff.txt` and, for the methods new in this version,
`../evidence/round6-prod-variants-bytecode-diff.txt` and `../evidence/round6-worldgen-prod-fingerprints.json`.

## Crash fixes checked in production

- Sable with Storage Drawers: see the table (server, server with the whole Create Aeronautics stack, and the reported
  singleplayer case); the crash-class sweep shows the published 1.0.35+mc1.21.1's refusal and none for this JAR.
- Dragon Mounts Remastered: checked in game on 1.0.34+mc1.21.1; the record fields involved are unchanged.
- Generator Accelerator, Accelerated Rendering with Iris, Embeddium without Iris, Bye Pregen and ServerCore: the fixes
  of the earlier ports stay in place, and their runs above pass again with this JAR.

## Integration decisions

- The published 1.0.35+mc1.21.1 is the starting point. The Forge 1.0.35 to 1.0.36 changes were merged in Mojang names;
  the files with no counterpart in the port were ported by comparing the 1.20.1 and 1.21.1 target code. The Forge
  sources were not changed.
- In all: 205 controls ported, 55 retired on 1.21.1,
  46 without a 1.21.1 NeoForge build of their mod (PORT-COVERAGE.md gives each reason).
- `vanilla_structure_ring_search` steps aside next to Structurify 2.0.42, whose own mixin wraps the same search loop:
  it applied in 5 runs without Structurify and stepped aside in 7 runs with it.
- `citadel_pose_hand_events` skips Citadel's event only while nothing listens to it: the two listeners the Forge
  version recognises (Alex's Caves, Alex's Mobs) have no NeoForge 1.21.1 build.
- `vanilla_climate_last_result_weak` steps aside next to Bye Pregen 1.1.3.0. A first build of this version replaced the
  climate search field that Bye Pregen also uses, and its Bye Pregen run stopped at the first chunk; this JAR yields to
  Bye Pregen's mixin, and all runs above were made again with it.

## Limits and unrelated upstream messages

These are smoke tests, deterministic on/off comparisons and in-game shadow comparisons. They do not establish every
gameplay path, every option combination, long-session stability or a speed improvement.

- The switches new in this version were ported by comparing the 1.20.1 and 1.21.1 target code. Their in-game checks
  here are that every guarded switch applies with its target installed, that shadow modes report no mismatch where the
  scenes reach them, and that the terrain stays identical. Their 1.20.1 measurements are not repeated on 1.21.1.
- `distanthorizons_join_config_resend` acts only when a client joins a dedicated server. These client runs are
  singleplayer, so on 1.21.1 the hook was shown to apply and to leave singleplayer alone.
- Dragon Mounts Remastered was run in game with 1.0.34+mc1.21.1, not again with this JAR.
- Switches without a shadow mode rest on the 1.20.1 proofs, the 1.21.1 code comparisons made for the port, the terrain
  comparisons, the differential test and clean in-game behaviour.
- The Collections Of Optimizations step-aside rules are kept; no build of that mod exists for 1.21.1 NeoForge to test
  them against.
- Generator Accelerator 1.6.2 stopped generating chunks by itself in 2 of the 4 runs with it
  here (spawn preparation never finished and nothing was logged until the time limit); the earlier ports' tests showed
  the same with Generator Accelerator alone. The Generator Accelerator results above come from the runs that completed.
- The scripted client scene summons hostile mobs next to a survival player; they killed the probe player in
  3 of 4 client runs. The probe, its checks and the shadow comparisons
  ran to the end in every run.
- The optional test setups show upstream warnings that also appear with the switches off, as in earlier versions.

## Test environment

All runtime tests used isolated, disposable game instances and worlds created for this qualification.
