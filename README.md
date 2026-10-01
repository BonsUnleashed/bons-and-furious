![Bons and Furious cover, AI-generated promotional concept art](https://github.com/BonsUnleashed/bons-and-furious/releases/download/v1.0.15/bons-and-furious-cover.png)

# Bons and Furious

**Your CPU lives its life one tick at a time.**

[**Download 1.0.23**](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.23) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bons-and-furious) · [Modrinth](https://modrinth.com/mod/bons-and-furious) · [Wiki](https://github.com/BonsUnleashed/bons-and-furious/wiki) · [Issues](https://github.com/BonsUnleashed/bons-and-furious/issues)

Bons and Furious patches 107 specific, measured hot spots in vanilla Minecraft 1.20.1, in Forge itself and in 40 popular Forge mods, from Valkyrien Skies, Distant Horizons and Alex's Caves to Embeddium, Oculus and GeckoLib. Each patch is one switch in one config file. It applies only to the exact mod build it was tested against and leaves anything else untouched, with one line in the log.

**Minecraft 1.20.1 · Forge 47.3.22 or newer · no required dependencies · one JAR for client and server**

## Measured in a whole modpack

On 30 September 2026 the pack this mod is developed in (470 mods: Valkyrien Skies, Alex's Caves, Distant Horizons, Complementary shaders, Fresh Animations and the rest) was run with Bons and Furious 1.0.21 and with the mod removed, 4 matched runs each on the same PC and world seed.

| | With Bons and Furious | Without |
| --- | --- | --- |
| Dedicated server, 512 new chunks generated | 2.4 min | 20 min |
| New singleplayer world, spawn area ready | 56 s | 268 s |
| Processor time and memory per new chunk | 0.8 s, 0.4 GB | 4.1 s, 4.1 GB |
| Same scene, 64 animated mobs in view | 73 FPS, 1% low 40 | 66 FPS, 1% low 35 |
| Same scene, graphics card at its limit | 172 FPS, 1% low 89 | 171 FPS, 1% low 69 |
| Singleplayer server tick, nothing generating | 10 ms | 14 ms |
| Start-up time, memory after start-up | no difference | |

In every row with a difference, the slowest run with the mod beat the fastest run without it. The world-generation gain comes from the three vanilla terrain switches. The frame-rate rows are one saved world drawn by both versions; while new terrain generates the two are not comparable, because the mod keeps about a third more terrain loaded around you. Other packs will differ with the mods they use and the terrain they generate.

Every run and the method: [Whole-modpack benchmark](https://github.com/BonsUnleashed/bons-and-furious/wiki/Whole-modpack-benchmark)

## What it does

- **91 optimizations** give the same results with less work: fewer allocations, no repeated lookups, no state rebuilt only to come out identical. Terrain preparation, ground-height estimates, climate lookups, Distant Horizons' rough-surface generation, chunk render layers, ship chunk bookkeeping, shader graph resets, animation easing and event dispatch are the largest. Five of them give Radium, ModernFix and ImmediatelyFast optimizations back that those mods switch off themselves when C2ME or a shader resource pack is installed.
- **12 fixes** repair reproduced server freezes, worker-thread crashes, generation exceptions and one chunk-tracking gap between Radium and SecurityCraft, mostly where Distant Horizons or C2ME worker threads meet a content mod's world generation.
- **4 deliberate changes** (frame pacing, the Occult bed scan, Fowl Play flight targets, Scorched sandcrab processing) trade a documented behaviour difference for a large saving.

All 107 are listed in `config/bons_and_furious.properties` with their target mod, tested build, side and measurement. Set any key to `false` and restart. Every control is explained in the [wiki](https://github.com/BonsUnleashed/bons-and-furious/wiki).

## Measured results, per patch

| Where | What changed | Measured |
| --- | --- | --- |
| **Minecraft** chunk generation (new in 1.0.18) | Neighbouring chunk work areas share their ground-height estimates instead of re-scanning the same columns | **95% of surface scans skipped** (349,843 → 16,494 in a 144-chunk run) and **about half the wall time** for that run (mean 162 → 75 s, shared machine) |
| **Minecraft** terrain preparation (new in 1.0.16) | The second density-graph pass reuses what the first pass built instead of rebuilding it | **91% less CPU** per NoiseChunk (10.35 → 0.89 ms) and **53% less** per structure-placement height query (17.7 → 8.3 ms) |
| **Minecraft** terrain preparation | Shared density-graph nodes are transformed once per chunk instead of once per reference | **59.6% less memory allocated** (1.47 GB → 0.60 GB over 64 height queries) and 56% less thread CPU |
| **Valkyrien Skies** | Ship chunk bookkeeping, after seven rounds of ship work | **94% less time** (329 → 21 µs, full-pack fixture); physics terrain conversion 65–69% less |
| **Distant Horizons** + content mods | Seven generation-context fixes | A reproduced Scuba Gear stall recovers from **6.1 to 20 TPS** |
| **Oculus** | Empty shader render-order graphs are reused instead of rebuilt | **90% less time** per reset (166 → 16 ns), 808 → 0 bytes |
| **Embeddium** | Chunk-mesh upload classification without stream pipelines | **59% less time** (151 → 62 ns per region with 8 outputs) |
| **GeckoLib** | Mixed animation easing without boxed doubles | **50% less time** per evaluation (67 → 34 ns) |
| **Architectury API** | Event dispatch without re-resolving method handles | **19× faster** (649 → 34 ns per listener call) |
| **AmbientSounds** | Bounded terrain scan | **88% lower p95** (5.45 → 0.65 ms per analysis) |
| **Frame pacing** (client) | The FPS-limiter wait moves before the display update | **84% less frame-interval variation** at p95 (7.63 → 1.25 ms) at the same 120 FPS cap |
| **Entity Texture Features** (new in 1.0.21) | Each sprite's texture id is worked out once instead of on every draw of a chest, sign, bed or banner | **95% less time** per draw (134 → 7 ns) |
| **Minecraft** keyframe animations (new in 1.0.21) | Bone lookup walks the model tree directly instead of building stream pipelines | **92% less time** per frame (120 → 10 µs for a 47-part model) |
| **Distant Horizons** (new in 1.0.23) | The rough-surface generator keeps the parts of the terrain density that depend only on x and z instead of recomputing them at every probe height | **85% less time** per LOD column (3,351 → 494 µs) |
| **Forge** + Oculus (new in 1.0.23) | Each block remembers its chunk render layers while the shader pack's layer map is unchanged | **59% less time** per lookup (122 → 50 ns), 12-14% of chunk meshing |
| **Radium** with C2ME (new in 1.0.23) | Radium's fast chunk access runs again while C2ME's replacement for it is switched off | **43% less time** per loaded-chunk lookup (81 → 46 ns), `getBlockState` 133 → 73 ns |
| **ImmediatelyFast** | Horse-layer ordering without substrings | **46% less time** (28.9 → 15.5 ns), 64 → 0 bytes |

Smaller allocation and lookup savings in Ars Nouveau, Curios API, Alex's Caves, Ice and Fire, TaCZ and others are on the wiki.

> **How to read the per-patch numbers.** Each figure measures the named method, phase or reproduction in a fixture, on the build it was measured on. The figures are not additive; the whole-modpack comparison above is the aggregate measurement. Method, fixture settings and the result for every control: [Measurements and caveats](https://github.com/BonsUnleashed/bons-and-furious/wiki/Measurements-and-caveats).

## Covered mods (all optional)

**Rendering, shaders and ambience:** Embeddium, Oculus, ImmediatelyFast, Entity Texture Features, Entity Model Features, EntityCulling, Colorwheel, Ryoamic Lights, Presence Footsteps, AmbientSounds.

**Shared libraries and server performance mods:** GeckoLib, Architectury API, Curios API, Structure Gel API, Radium, ModernFix, ChunkSending.

**Ships, structures and distant terrain:** Valkyrien Skies, Trackwork, Distant Horizons, Structurify, Sakes Structures.

**Content and gameplay:** Alex's Caves, Ice and Fire, Ars Nouveau, Timeless and Classics Zero (TaCZ), Terramity, Ad Astra, Fowl Play, Butterflies, Goblins Tyranny, Under the Moon, Nether Depths Upgrade, Spawn, Cryptic Foes, Hostile Villages, Scuba Gear, Occult, Scorched and Better Combat.

Coverage means the tested build and the specific code paths of each mod, not every feature. Tested builds per mod: [Compatibility and target versions](https://github.com/BonsUnleashed/bons-and-furious/wiki/Compatibility-and-target-versions).

## Install

1. Download `bons_and_furious-1.0.23.jar` from the [1.0.23 release](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.23) (SHA-256 in `SHA256SUMS.txt`) and put it in `mods/` on the client and on the server. Nothing else is required; every target mod is detected at load. The two small mixin libraries it uses, MixinExtras and MixinSquared, are bundled inside it.
2. Start once. The mod writes its config file with every switch on and logs how many controls are enabled.
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

The 1.0.23 release JAR is built from this tree and was tested in a 470-mod pack, server and client, before release.

### Upstream pull requests

Pull requests sent to the projects this mod patches: 27 open, 3 closed without merge (status checked 1 October 2026).

| Project | Pull requests | Status |
| --- | --- | --- |
| Ad Astra | [#825](https://github.com/terrarium-earth/Ad-Astra/pull/825) | Open |
| Alex's Caves | [#1759](https://github.com/AlexModGuy/AlexsCaves/pull/1759), [#1760](https://github.com/AlexModGuy/AlexsCaves/pull/1760) | Open |
| AmbientSounds | [#348](https://github.com/CreativeMD/AmbientSounds/pull/348) | Open |
| Architectury API | [#747](https://github.com/architectury/architectury-api/pull/747) | Open |
| Ars Nouveau | [#2258](https://github.com/baileyholl/Ars-Nouveau/pull/2258) | Open |
| Better Combat | [#623](https://github.com/ZsoltMolnarrr/BetterCombat/pull/623) | Open |
| Butterflies | [#493](https://github.com/doc-bok/Butterflies/pull/493) | Open |
| Cryptic Foes | [#7](https://github.com/min2222/Cryptic-Foes/pull/7) | Closed without merge |
| Curios API | [#639](https://github.com/TheIllusiveC4/Curios/pull/639) | Open |
| Embeddium | [#575](https://github.com/FiniteReality/embeddium/pull/575) | Open |
| Fowl Play | [#242](https://github.com/aqariio/Fowl-Play/pull/242), [#243](https://github.com/aqariio/Fowl-Play/pull/243) | Open |
| Hostile Villages | [#37](https://github.com/someaddons/HostileVillages/pull/37) | Open |
| Ice and Fire | [#5641](https://github.com/AlexModGuy/Ice_and_Fire/pull/5641), [#5642](https://github.com/AlexModGuy/Ice_and_Fire/pull/5642), [#5643](https://github.com/AlexModGuy/Ice_and_Fire/pull/5643) | Open |
| ImmediatelyFast | [#586](https://github.com/RaphiMC/ImmediatelyFast/pull/586) | Open |
| Nether Depths Upgrade | [#67](https://github.com/Scouter456/Nether_Depths_Upgrade/pull/67) | Closed without merge. The 1.20 branch is no longer maintained. |
| Oculus | [#869](https://github.com/Asek3/Oculus/pull/869) | Open |
| Presence Footsteps (Forge) | [#67](https://github.com/PaintNinja/Presence-Footsteps-Forge/pull/67) | Open |
| Ryoamic Lights | [#54](https://github.com/ThinkingStudios/RyoamicLights/pull/54) | Open |
| Structurify | [#93](https://github.com/Faboslav/structurify/pull/93) | Closed without merge |
| Timeless and Classics Zero (TaCZ) | [#745](https://github.com/MCModderAnchor/TACZ/pull/745) | Open |
| Trackwork | [#70](https://github.com/Endalion/trackwork/pull/70) | Open |
| Valkyrien Skies | [#1981](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1981), [#1982](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1982), [#1983](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1983), [#1984](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1984), [#1985](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1985) | Open |

## Development disclosure

The implementation was substantially written by Bons outside agent sessions. Generative AI assisted portions of the code, testing, packaging and documentation. Original mod authors retain credit for their work.

The project logo is AI-generated promotional concept art. It does not depict content added by this mod.
