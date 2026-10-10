![Bons and Furious cover, AI-generated promotional concept art](https://github.com/BonsUnleashed/bons-and-furious/releases/download/v1.0.15/bons-and-furious-cover.png)

# Bons and Furious

**Your CPU lives its life one tick at a time.**

[**Forge 1.20.1: 1.0.39**](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.39) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bons-and-furious) · [Modrinth](https://modrinth.com/mod/bons-and-furious) · [Wiki](https://github.com/BonsUnleashed/bons-and-furious/wiki) · [Issues](https://github.com/BonsUnleashed/bons-and-furious/issues)

Bons and Furious reduces repeated work in Minecraft and optional mods. Each optimization or fix has its own switch in one config file. Choose the build for your Minecraft version and loader.

| Minecraft | Loader | Controls | Requirements |
| --- | --- | ---: | --- |
| 1.20.1 | Forge | 306 | Forge 47.3.22 or newer |
| 1.21.1 | NeoForge | 205 | Java 21; NeoForge 21.1.252 or newer |

Both builds use one JAR for client and server, with no required target mods.

## Minecraft 1.20.1 / Forge: measured on an already optimized modpack

**5.6× faster world generation. 4.8× faster spawn preparation. Previously measured: +10% average FPS with 64 animated mobs and +29% 1% lows in a GPU-bound scene.**

**These gains are ON TOP of the major performance mods already running in the test pack.** Both sides of the comparison keep **ModernFix, FerriteCore, Radium and C2ME**; the client also keeps **Embeddium, ImmediatelyFast and EntityCulling**, along with **Oculus, Distant Horizons, Complementary shaders and Fresh Animations**. The comparison adds or removes Bons and Furious from that existing setup. The figures below separate the latest 1.0.26 benchmark from the earlier clean FPS test.

### Latest whole-pack results — 1.0.26, 2 October 2026

Four matched runs per arm, ABBA-ABBA order, same PC and seed; the reference pack had 471 server / 510 client mod JARs at the snapshot. C2ME was enabled on both sides, with `reduceLockRadius=false` throughout this test.

| Workload | With Bons and Furious 1.0.26 | Same stack without Bons | Result |
| --- | ---: | ---: | --- |
| Dedicated server, 512 new chunks | 102 s | 567 s | **5.6× throughput** |
| First 4 chunks of a new world | 23.5 s | 238 s | **90% less time** |
| New singleplayer world, spawn preparation | 58 s | 277 s | **4.8× faster** |
| Game launch to standing in a new world | 242 s | 482 s | **50% less time** |
| CPU time per generated chunk | 0.56 s | 2.91 s | **81% less CPU** |
| Memory allocated per generated chunk | 308 MB | 2,460 MB | **87% less allocation** |
| Distant Horizons LOD records, same 4-minute session | 8,259 | 1,960 | **4.2× as many records built** |

Client start-up before loading a world was 128 vs 136 s (6% faster). Server start-up was unchanged; live heap after server start-up was **70 MB higher** (2.30 vs 2.23 GB). Allocation saved while generating is separate from memory retained. These are whole-modpack results for this PC, pack and seed, not promises for every setup.

### Measured FPS gains — earlier 1.0.21 benchmark, 30 September 2026

The same saved scenes were drawn with the client optimization stack above enabled in both arms, shaders and resource packs on, Distant Horizons generation off, four runs each. These are **measured 1.0.21 results**, retained as historical evidence; they are not a new 1.0.26 FPS measurement.

| Same-scene rendering workload | With Bons 1.0.21 | Same stack without Bons | Result |
| --- | ---: | ---: | --- |
| 64 animated mobs, average FPS | 72.6 | 66.1 | **+10%** |
| 64 animated mobs, 1% low FPS | 40 | 35 | **+16%** (from unrounded data) |
| GPU-bound overlook, 1% low FPS | 89 | 69 | **+29%** |
| GPU-bound overlook, average FPS | 172 | 171 | No significant change |

The 1.0.26 saved-world baseline froze its integrated server in all four runs, while the Bons runs kept 20 TPS. That is documented as a stability result, not used to inflate the FPS comparison. New-world sessions also draw more terrain with Bons, so their frame rates are not a like-for-like comparison.

Every run, the retained earlier results, the baseline mod versions and the method: [Whole-modpack benchmark](https://github.com/BonsUnleashed/bons-and-furious/wiki/Whole-modpack-benchmark).

## What the Forge 1.20.1 build does

- **264 optimizations** reduce repeated work: fewer allocations, no repeated lookups, no state rebuilt only to come out identical. Terrain preparation, ground-height estimates, climate lookups, Distant Horizons' rough-surface generation, chunk render layers, ship chunk bookkeeping, shader graph resets, animation easing and event dispatch are the largest. Eight controls restore or provide compatible versions of performance paths disabled in the tested setup, including Radium, ModernFix, ImmediatelyFast and Distant Horizons integrations. Each checks the relevant mod builds and configuration.
- **37 fixes** repair reproduced server freezes, worker-thread crashes, generation exceptions and defects in the target mods themselves: large mobs whose solid body parts could be walked through with Radium, caches that two threads could corrupt, data that piled up on every world join.
- **5 deliberate changes** (frame pacing, the Occult bed scan, Fowl Play flight targets, Scorched sandcrab processing, two experimental Radium options) trade a documented behaviour difference for a saving.

All 306 are listed in `config/bons_and_furious.properties` with their target mod, tested build, side and measurement. Set any key to `false` and restart. Every control is explained in the [wiki](https://github.com/BonsUnleashed/bons-and-furious/wiki).

## Forge 1.20.1 results, per patch

| Where | What changed | Measured |
| --- | --- | --- |
| **Minecraft** chunk generation (new in 1.0.18) | Neighbouring chunk work areas share their ground-height estimates instead of re-scanning the same columns | **95% of surface scans skipped** (349,843 → 16,494 in a 144-chunk run) and **about half the wall time** for that run (mean 162 → 75 s, shared machine) |
| **Minecraft** terrain preparation (new in 1.0.16) | The second density-graph pass reuses what the first pass built instead of rebuilding it | **91% less CPU** per NoiseChunk (10.35 → 0.89 ms) and **53% less** per structure-placement height query (17.7 → 8.3 ms) |
| **Embeddium + Fusion** (new in 1.0.26) | Hidden connected-texture faces are skipped before their quads are built | **25% less chunk-meshing time** (5.2 → 3.9 s for 733 sections), **41% less allocation**, byte-identical vertices and indices |
| **Farmer's Delight** (new in 1.0.26) | Tool-action ingredients share their registry scan during a recipe load | Client recipe-packet decoding **8.8 → 0.4 s**; recipe rebuild **5.5–6.4 → 1.4 s** on the client and **3.6 → 1.2 s** on the server, same ingredients |
| **Create** (new in 1.0.30) | Contraption collision boxes are built in one pass | A 400-block carriage **369 → 1.3 ms** and 108 → 0.25 MB, identical boxes |
| **Minecraft** saving (new in 1.0.30) | Saved data and player files are compressed and written by a background writer | Server thread **4.9 → 0.9 s** for 500 data files at once, byte-identical files |
| **Minecraft** networking (new in 1.0.30) | Packet bursts are flushed once instead of once per packet | 300 socket flushes → 1; network-thread CPU **8.7 → 2.6 µs per packet**, identical bytes |
| **Embeddium** (new in 1.0.30) | A section search whose inputs are all unchanged replays the previous one | **58–68% less time** per search with a still camera; the search was 14.3% of the render thread there |
| **Forge** (new in 1.0.30) | The mod and channel part of the server-list status is reused while it is unchanged | **4.5 → 0.15 ms** per status update with 440 mods; it was 16.6% of the server thread during a pregeneration |
| **Entity Model Features + Fresh Animations** (new in 1.0.26) | Variable indexes and model-part lookups are reused during animation compilation | Entity-renderer rebuild **15.1–16.6 → 8.8 s**, with 5.1 million variable answers and 692,000 part lookups checked in game |
| **Distant Horizons** (new in 1.0.23) | The rough-surface generator keeps the parts of the terrain density that depend only on x and z instead of recomputing them at every probe height | **85% less time** per LOD column (3,351 → 494 µs) |
| **Valkyrien Skies** | Ship chunk bookkeeping, after seven rounds of ship work | **94% less time** (329 → 21 µs, full-pack fixture); physics terrain conversion 65–69% less |
| **Oculus** | Empty shader render-order graphs are reused instead of rebuilt | **90% less time** per reset (166 → 16 ns), 808 → 0 bytes |
| **JEI** (new in 1.0.26) | Each ingredient keeps its display stack with the same expiry semantics | **84% less time** per indexing lookup (830 → 130 ns in the cache harness); the in-game index retained the same categories, recipes and stack content |
| **GeckoLib** | Mixed animation easing without boxed doubles | **50% less time** per evaluation (67 → 34 ns) |
| **Architectury API** | Event dispatch without re-resolving method handles | **19× faster** (649 → 34 ns per listener call) |
| **Forge** + Oculus (new in 1.0.23) | Each block remembers its chunk render layers while the shader pack's layer map is unchanged | **59% less time** per lookup (122 → 50 ns), 12-14% of chunk meshing |
| **Radium** with C2ME (new in 1.0.23) | Radium's fast chunk access runs again while C2ME's replacement for it is switched off | **43% less time** per loaded-chunk lookup (81 → 46 ns), `getBlockState` 133 → 73 ns |
| **Entity Texture Features** (new in 1.0.21) | Each sprite's texture id is worked out once instead of on every draw of a chest, sign, bed or banner | **95% less time** per draw (134 → 7 ns) |
| **AmbientSounds** | Bounded terrain scan | **88% lower p95** (5.45 → 0.65 ms per analysis) |
| **Frame pacing** (client) | The FPS-limiter wait moves before the display update | **84% less frame-interval variation** at p95 (7.63 → 1.25 ms) at the same 120 FPS cap |
| **ImmediatelyFast** | Horse-layer ordering without substrings | **46% less time** (28.9 → 15.5 ns), 64 → 0 bytes |

Smaller allocation and lookup savings in Ars Nouveau, Curios API, Alex's Caves, Ice and Fire, TaCZ and others are on the wiki.

> **How to read the per-patch numbers.** Each figure measures the named method, phase or reproduction in a fixture, on the build it was measured on. The figures are not additive; the whole-modpack comparison above is the aggregate measurement. Method, fixture settings and the result for every control: [Measurements and caveats](https://github.com/BonsUnleashed/bons-and-furious/wiki/Measurements-and-caveats).

## Forge 1.20.1 covered mods (all optional)

**Rendering, shaders and ambience:** Embeddium, Oculus, ImmediatelyFast, Entity Texture Features, Entity Model Features, EntityCulling, Fusion, Colorwheel, Ryoamic Lights, Presence Footsteps, AmbientSounds, CIT Reforged, FancyMenu, Particular.

**Shared libraries and server performance mods:** GeckoLib, Architectury API, Curios API, Structure Gel API, Radium, ModernFix, ChunkSending, TerraBlender, Citadel, Lionfish API, Placebo, Kiwi, Cucumber, CoFH Core, L2 Library, Pehkui, Almost Unified, ElysiumAPI.

**Ships, structures and distant terrain:** Valkyrien Skies, Trackwork, Distant Horizons, Structurify, Sakes Structures, Oh The Biomes We've Gone, Dynamic Trees.

**Content and gameplay:** Alex's Caves, Ice and Fire, Ars Nouveau, JEI, Farmer's Delight, Relics, Timeless and Classics Zero (TaCZ), Terramity, Ad Astra, Fowl Play, Butterflies, Goblins Tyranny, Under the Moon, Nether Depths Upgrade, Spawn, Cryptic Foes, Hostile Villages, Scuba Gear, Occult, Scorched and Better Combat, Create, Pipez, Storage Drawers, Immersive Engineering, Slice & Dice, Cooking for Blockheads, Alex's Mobs, Mowzie's Mobs, Mutant Monsters, Critters and Companions, Bosses of Mass Destruction, FD Bosses, Legendary Monsters, Ribbits, Artifacts, Simply Swords, SlashBlade: Resharped, Dungeons Delight, Jaden's Nether Expansion, Regions Unexplored, L_Ender's Cataclysm, Mekanism, Rats, Aquaculture, XercaPaint.

Coverage means the tested build and the specific code paths of each mod, not every feature. Tested builds per mod: [Compatibility and target versions](https://github.com/BonsUnleashed/bons-and-furious/wiki/Compatibility-and-target-versions).

## Install the matching build

For Minecraft 1.21.1, use `bons_and_furious-neoforge-1.0.38+mc1.21.1.jar`, Java 21 and NeoForge 21.1.252 or newer. MixinSquared is bundled; NeoForge supplies MixinExtras. [NeoForge setup and compatibility](https://github.com/BonsUnleashed/bons-and-furious/wiki/Minecraft-1.21.1-NeoForge).

For Minecraft 1.20.1 / Forge:

1. Download `bons_and_furious-1.0.39.jar` from the [1.0.39 release](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.39) (SHA-256 in `SHA256SUMS.txt`) and put it in `mods/` on the client and on the server. Nothing else is required; every target mod is detected at load. The two small mixin libraries it uses, MixinExtras and MixinSquared, are bundled inside it.
2. Start once. The mod writes its config file with its defaults (every switch on except `vanilla_background_level_dat`) and logs how many controls are enabled.
3. To turn one off, set its key to `false` and restart. Client-only patches (renderer, shaders, ambience) never load on a dedicated server.

JVM overrides, log messages and troubleshooting: [Installation and configuration](https://github.com/BonsUnleashed/bons-and-furious/wiki/Installation-and-configuration).

## Compatibility

Keep your optimization stack: Embeddium, ImmediatelyFast, ModernFix, FerriteCore, Radium and C2ME. Bons and Furious changes paths that still did unnecessary work in the tested pack, including a few inside Embeddium, ImmediatelyFast and Oculus themselves. With C2ME installed it also gives Radium and ModernFix three optimizations back that they switch off for every C2ME build, as long as the C2ME module that would clash with each one is off in `c2me.toml`.

## Build from source

Requires Python 3.11+, JDK 17 or newer, a Forge 1.20.1 SRG development classpath,
and the local dependency versions listed in upstream-credits.json. The tested
compiler is Temurin 21.0.12.1 with `--release 17`. Upstream JARs are not supplied.

```text
python build.py --minecraft-dir /path/to/minecraft \
  --forge-libraries /path/to/forge/libraries --java-home /path/to/jdk
```

The Minecraft directory supplies `libraries/` and `mods/`. The Forge libraries
directory supplies Forge 47.4.16 and its libraries. A Forge-generated
`*-srg.jar` must exist beneath `libraries/net/minecraft/client/`. The script
compiles the mixins and helpers, relocates our own helper packages, checks every
guard fingerprint in `patches/*.json` against your local target jars (it stops
if one differs from the tested build), and packages the jar with MixinExtras
0.5.0 and MixinSquared 0.3.6-beta.1 as Jar-in-Jar. It downloads those two from
their Maven repositories and checks their sha1, or takes them from
`--deps-dir`. It writes only `build/` and `dist/` inside this checkout. It never
changes the supplied installations or any saves.

Mixins use SRG names with `remap = false`, the names the game runs with on
Forge 1.20.1. One mixin config per target mod lives in `resources/`, and each
switch's mixins and guarded methods are listed in `patches/<mod>.json`.

## Licence and upstream

Licensed GPL-3.0-only ([LICENSE](LICENSE)). Upstream attribution is in [NOTICE.md](NOTICE.md); the tested dependency builds are listed in [upstream-credits.json](upstream-credits.json).

The 1.0.39 release JAR was tested in client and dedicated-server copies of the reference pack before release. The whole-pack benchmark above was measured with 1.0.26; its snapshot had 510 client and 471 server mod JARs.

### Upstream pull requests

Pull requests sent to the projects this mod patches: 10 merged, 86 open, 15 closed without merge (status checked 9 October 2026).

| Project | Pull requests | Status |
| --- | --- | --- |
| Ad Astra | [#825](https://github.com/terrarium-earth/Ad-Astra/pull/825) | Open |
| Alex's Caves | [#1759](https://github.com/AlexModGuy/AlexsCaves/pull/1759), [#1760](https://github.com/AlexModGuy/AlexsCaves/pull/1760), [#1762](https://github.com/AlexModGuy/AlexsCaves/pull/1762) | Open |
| Alex's Mobs | [#2385](https://github.com/AlexModGuy/AlexsMobs/pull/2385), [#2386](https://github.com/AlexModGuy/AlexsMobs/pull/2386) | Open |
| Almost Unified | [#144](https://github.com/AlmostReliable/almostunified/pull/144) | Closed without merge |
| AmbientSounds | [#348](https://github.com/CreativeMD/AmbientSounds/pull/348), [#349](https://github.com/CreativeMD/AmbientSounds/pull/349) | Merged |
| Architectury API | [#747](https://github.com/architectury/architectury-api/pull/747) | Open |
| Ars Nouveau | [#2258](https://github.com/baileyholl/Ars-Nouveau/pull/2258) | Open |
| Artifacts | [#518](https://github.com/ochotonida/artifacts/pull/518) | Open |
| Baked Substring Index | [#1](https://github.com/mezz/baked-substring-index/pull/1) | Open |
| Better Combat | [#623](https://github.com/ZsoltMolnarrr/BetterCombat/pull/623) | Open |
| Bosses of Mass Destruction Forge | [#31](https://github.com/CERBON-MODS/Bosses-of-Mass-Destruction-FORGE/pull/31) | Open |
| Butterflies | [#493](https://github.com/doc-bok/Butterflies/pull/493) | Open |
| ChunkSending | [#13](https://github.com/someaddons/chunksending/pull/13) | Closed without merge |
| CIT Reforged | [#16](https://github.com/tomwmth/cit-reforged/pull/16) | Open |
| Citadel | [#232](https://github.com/AlexModGuy/Citadel/pull/232) | Open |
| Colorwheel | [#84](https://github.com/djefrey/Colorwheel/pull/84) | Open |
| Cooking for Blockheads | [#812](https://github.com/TwelveIterations/CookingForBlockheads/pull/812) | Open |
| Cryptic Foes | [#7](https://github.com/min2222/Cryptic-Foes/pull/7) | Closed without merge |
| Cucumber Library | [#62](https://github.com/BlakeBr0/Cucumber/pull/62) | Open |
| Curios API | [#639](https://github.com/TheIllusiveC4/Curios/pull/639), [#644](https://github.com/TheIllusiveC4/Curios/pull/644), [#645](https://github.com/TheIllusiveC4/Curios/pull/645) | Open |
| Dungeon's Delight | [#126](https://github.com/Yirmiri/Dungeons-Delight/pull/126) | Open |
| Dynamic Trees | [#1230](https://github.com/DynamicTreesTeam/DynamicTrees/pull/1230), [#1231](https://github.com/DynamicTreesTeam/DynamicTrees/pull/1231) | Open |
| Embeddium | [#575](https://github.com/FiniteReality/embeddium/pull/575), [#576](https://github.com/FiniteReality/embeddium/pull/576), [#577](https://github.com/FiniteReality/embeddium/pull/577), [#578](https://github.com/FiniteReality/embeddium/pull/578), [#579](https://github.com/FiniteReality/embeddium/pull/579), [#580](https://github.com/FiniteReality/embeddium/pull/580), [#581](https://github.com/FiniteReality/embeddium/pull/581) | Open |
| Entity Model Features | [#591](https://github.com/Traben-0/Entity_Model_Features/pull/591), [#592](https://github.com/Traben-0/Entity_Model_Features/pull/592) | Merged |
| Entity Texture Features | [#508](https://github.com/Traben-0/Entity_Texture_Features/pull/508) | Merged |
| Farmer's Delight | [#1397](https://github.com/vectorwing/FarmersDelight/pull/1397), [#1398](https://github.com/vectorwing/FarmersDelight/pull/1398) | Open |
| FDBosses | [#42](https://github.com/FINDERFEED/FDBosses/pull/42) | Open |
| Forge | [#10893](https://github.com/MinecraftForge/MinecraftForge/pull/10893), [#10894](https://github.com/MinecraftForge/MinecraftForge/pull/10894) | Merged |
| Forge | [#10895](https://github.com/MinecraftForge/MinecraftForge/pull/10895) | Closed without merge. Withdrawn in favour of #10899 on the 26.3 branch, with the design the maintainer asked for. |
| Forge | [#10896](https://github.com/MinecraftForge/MinecraftForge/pull/10896) | Closed without merge. Withdrawn: too little left to gain after #10893. |
| Forge | [#10899](https://github.com/MinecraftForge/MinecraftForge/pull/10899) | Open |
| Fowl Play | [#242](https://github.com/aqariio/Fowl-Play/pull/242), [#243](https://github.com/aqariio/Fowl-Play/pull/243) | Open |
| Fusion | [#316](https://github.com/SuperMartijn642/Fusion/pull/316), [#317](https://github.com/SuperMartijn642/Fusion/pull/317), [#318](https://github.com/SuperMartijn642/Fusion/pull/318) | Open |
| Hostile Villages | [#37](https://github.com/someaddons/HostileVillages/pull/37), [#38](https://github.com/someaddons/HostileVillages/pull/38) | Open |
| Ice and Fire | [#5641](https://github.com/AlexModGuy/Ice_and_Fire/pull/5641), [#5642](https://github.com/AlexModGuy/Ice_and_Fire/pull/5642), [#5643](https://github.com/AlexModGuy/Ice_and_Fire/pull/5643), [#5644](https://github.com/AlexModGuy/Ice_and_Fire/pull/5644), [#5645](https://github.com/AlexModGuy/Ice_and_Fire/pull/5645) | Open |
| ImmediatelyFast | [#586](https://github.com/RaphiMC/ImmediatelyFast/pull/586), [#588](https://github.com/RaphiMC/ImmediatelyFast/pull/588), [#589](https://github.com/RaphiMC/ImmediatelyFast/pull/589) | Closed without merge |
| Immersive Engineering | [#6448](https://github.com/BluSunrize/ImmersiveEngineering/pull/6448) | Open |
| Jaden's Nether Expansion | [#353](https://github.com/ThatJadenXgamer/Jadens-Nether-Expansion/pull/353) | Closed without merge. Withdrawn: the 1.20.1 branch is no longer maintained and the 1.21.1 rework already covers it. |
| Just Enough Items | [#4525](https://github.com/mezz/JustEnoughItems/pull/4525) | Merged. Released in JEI 15.62.0.218. |
| Just Enough Items | [#4527](https://github.com/mezz/JustEnoughItems/pull/4527) | Closed without merge. Withdrawn: it did not fit what that cache is for. |
| L2 Library | [#34](https://github.com/Minecraft-LightLand/L2Library/pull/34), [#35](https://github.com/Minecraft-LightLand/L2Library/pull/35) | Open |
| Legendary Monsters | [#13](https://github.com/Miauczel/Legendary-Monsters-1.20.1/pull/13) | Open |
| Lionfish API | [#5](https://github.com/lender544/Lionfish-API/pull/5), [#6](https://github.com/lender544/Lionfish-API/pull/6) | Open |
| ModernFix | [#696](https://github.com/embeddedt/ModernFix/pull/696) | Merged |
| ModernFix | [#697](https://github.com/embeddedt/ModernFix/pull/697), [#698](https://github.com/embeddedt/ModernFix/pull/698) | Closed without merge |
| ModernFix | [#699](https://github.com/embeddedt/ModernFix/pull/699) | Open |
| Mowzie's Mobs | [#65](https://github.com/BobMowzie/MowziesMobs-Public/pull/65) | Open |
| Mutant Monsters | [#139](https://github.com/Fuzss/mutant-monsters/pull/139) | Open |
| Nether Depths Upgrade | [#67](https://github.com/Scouter456/Nether_Depths_Upgrade/pull/67) | Closed without merge. The 1.20 branch is no longer maintained. |
| OcclusionCulling (EntityCulling) | [#5](https://github.com/LogisticsCraft/OcclusionCulling/pull/5) | Open |
| Oculus | [#869](https://github.com/Asek3/Oculus/pull/869), [#870](https://github.com/Asek3/Oculus/pull/870), [#871](https://github.com/Asek3/Oculus/pull/871), [#872](https://github.com/Asek3/Oculus/pull/872) | Open |
| Oh The Biomes We've Gone | [#421](https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/pull/421) | Open |
| Particular | [#59](https://github.com/Leclowndu93150/Particular/pull/59) | Open |
| Pehkui | [#636](https://github.com/Virtuoel/Pehkui/pull/636) | Open |
| Placebo | [#125](https://github.com/Shadows-of-Fire/Placebo/pull/125) | Closed without merge. The 1.20 branch is no longer maintained; newer branches removed this event. |
| Presence Footsteps (Forge) | [#67](https://github.com/PaintNinja/Presence-Footsteps-Forge/pull/67) | Open |
| Radium Re-Reforged | [#8](https://github.com/bigenergy/radium-reforged-patched/pull/8), [#9](https://github.com/bigenergy/radium-reforged-patched/pull/9), [#11](https://github.com/bigenergy/radium-reforged-patched/pull/11), [#15](https://github.com/bigenergy/radium-reforged-patched/pull/15), [#16](https://github.com/bigenergy/radium-reforged-patched/pull/16), [#17](https://github.com/bigenergy/radium-reforged-patched/pull/17), [#18](https://github.com/bigenergy/radium-reforged-patched/pull/18) | Open |
| Regions Unexplored | [#234](https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/pull/234) | Open |
| Relics | [#357](https://github.com/Octo-Studios/relics/pull/357), [#358](https://github.com/Octo-Studios/relics/pull/358) | Open |
| Ribbits | [#85](https://github.com/yungnickyoung/Ribbits/pull/85) | Open |
| Ryoamic Lights | [#54](https://github.com/ThinkingStudios/RyoamicLights/pull/54), [#55](https://github.com/ThinkingStudios/RyoamicLights/pull/55) | Open |
| Storage Drawers | [#1307](https://github.com/jaquadro/StorageDrawers/pull/1307) | Open |
| Structurify | [#93](https://github.com/Faboslav/structurify/pull/93) | Closed without merge |
| TerraBlender | [#241](https://github.com/Glitchfiend/TerraBlender/pull/241) | Merged |
| Timeless and Classics Zero (TaCZ) | [#745](https://github.com/MCModderAnchor/TACZ/pull/745) | Open |
| Trackwork | [#70](https://github.com/Endalion/trackwork/pull/70) | Open |
| Valkyrien Skies | [#1981](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1981), [#1982](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1982), [#1983](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1983), [#1984](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1984), [#1985](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1985) | Open |

## Development disclosure

The implementation was substantially written by Bons outside agent sessions. Generative AI assisted portions of the code, testing, packaging and documentation. Original mod authors retain credit for their work.

The project logo is AI-generated promotional concept art. It does not depict content added by this mod.
