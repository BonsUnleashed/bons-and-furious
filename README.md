![Bons and Furious cover, AI-generated promotional concept art](https://github.com/BonsUnleashed/bons-and-furious/releases/download/v1.0.15/bons-and-furious-cover.png)

# Bons and Furious

**Your CPU lives its life one tick at a time.**

[**Downloads**](https://github.com/BonsUnleashed/bons-and-furious/releases) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bons-and-furious) · [Modrinth](https://modrinth.com/mod/bons-and-furious) · [Wiki](https://github.com/BonsUnleashed/bons-and-furious/wiki) · [Issues](https://github.com/BonsUnleashed/bons-and-furious/issues)

Bons and Furious makes demanding modpacks run better: faster world generation, less waiting for worlds and resources to load, smoother rendering, and fixes for modded-world freezes and crashes.

It cuts repeated work in Minecraft and dozens of optional mods. **Keep your existing performance mods.** Bons and Furious builds on them, with one JAR for client and server and a separate switch for every change.

## More performance from the pack you love

- **Explore sooner.** Terrain preparation and ground-height calculations reuse work across chunks, helping new terrain and Distant Horizons landscapes build faster.
- **Keep detailed worlds and animated mobs.** Rendering and animation optimizations reduce work in shader, model and connected-texture paths, including Oculus, Entity Model Features, GeckoLib and Embeddium with Fusion.
- **Spend less time loading.** Recipe scans, ingredient indexing and animation compilation avoid repeated lookups, with targeted improvements for Farmer's Delight, JEI and Fresh Animations.
- **Keep complex worlds moving.** Fixes address reproduced server freezes, worker-thread crashes and generation exceptions where content mods meet Distant Horizons or C2ME. Valkyrien Skies also gets faster ship chunk bookkeeping and terrain conversion.
- **Tune every change.** Each optimization and fix has its own switch. Target mods are optional, and client rendering patches stay off dedicated servers.

Coverage depends on the Minecraft build and installed mod versions. [Browse the controls and compatibility](https://github.com/BonsUnleashed/bons-and-furious/wiki).

## Measured on top of an optimized modpack

**5.6× faster world generation. 4.8× faster spawn preparation. Half the time from launching the game to standing in a new world.**

These whole-pack tests kept **ModernFix, FerriteCore, Radium and C2ME** on both sides. Client tests also kept **Embeddium, ImmediatelyFast, EntityCulling, Oculus, Distant Horizons, Complementary shaders and Fresh Animations**. The comparison adds or removes Bons and Furious from that stack.

| What you gain | With Bons and Furious | Without Bons | Improvement |
| --- | ---: | ---: | --- |
| Generate 512 new server chunks | 102 s | 567 s | **5.6× faster** |
| Prepare a new singleplayer spawn | 58 s | 277 s | **4.8× faster** |
| Launch the game and enter a new world | 242 s | 482 s | **50% less waiting** |
| CPU time per generated chunk | 0.56 s | 2.91 s | **81% less CPU** |
| Memory allocated per generated chunk | 308 MB | 2,460 MB | **87% less allocation** |
| Average FPS with 64 animated mobs | 72.6 | 66.1 | **10% higher** |
| 1% low FPS in a shader-heavy overlook | 89 | 69 | **29% higher** |

Measured on **Minecraft 1.20.1 / Forge**, with four matched runs per arm on the same PC and seed. Generation and loading figures use Bons and Furious 1.0.26; FPS figures use the separate 1.0.21 same-scene test. The mob scene was CPU-bound; average FPS in the GPU-bound overlook stayed unchanged. Allocation means temporary memory created, not RAM retained. Results depend on the pack and hardware and do not measure the 1.21.1 build. [Full benchmarks and methods](https://github.com/BonsUnleashed/bons-and-furious/wiki/Whole-modpack-benchmark).

## A few of the biggest improvements

| System | Targeted improvement measured on Forge 1.20.1 |
| --- | --- |
| **Distant Horizons** | **85% less time** per rough-surface LOD column |
| **Valkyrien Skies** | **94% less time** on ship chunk bookkeeping; **65–69% less** on physics terrain conversion |
| **Embeddium + Fusion** | **25% less chunk-meshing time**, with identical vertices and indices |
| **Farmer's Delight** | Client recipe-packet decoding fell from **8.8 to 0.4 seconds**, with the same ingredients |
| **Entity Model Features + Fresh Animations** | Entity-renderer rebuild fell from **15–17 to 8.8 seconds** |
| **Frame pacing** | **84% less frame-interval variation** at p95, at the same 120 FPS cap |

These measure individual tasks, not total FPS gains, and cannot be added together. [Per-patch measurements and caveats](https://github.com/BonsUnleashed/bons-and-furious/wiki/Measurements-and-caveats).

## Install and make it yours

| Minecraft / loader | Requirements and setup |
| --- | --- |
| **1.20.1 / Forge** | Forge 47.3.22 or newer. [Setup and controls](https://github.com/BonsUnleashed/bons-and-furious/wiki/Installation-and-configuration) |
| **1.21.1 / NeoForge** | Java 21; NeoForge 21.1.252 or newer. [Setup and controls](https://github.com/BonsUnleashed/bons-and-furious/wiki/Minecraft-1.21.1-NeoForge) |

1. Download the JAR matching your Minecraft version and loader from [Downloads](https://github.com/BonsUnleashed/bons-and-furious/releases) and put it in `mods/` on the client, server, or both. No target mods are required.
2. Start the game or server to create `config/bons_and_furious.properties`.
3. To disable a change, set its key to `false` and restart.

Most controls remove redundant work; four make documented behaviour changes: frame pacing, Occult bed scans, Fowl Play flight targets and Scorched sandcrab processing. All can be disabled individually. [Controls, compatibility and troubleshooting](https://github.com/BonsUnleashed/bons-and-furious/wiki).

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

### Upstream pull requests

Pull requests sent to the projects this mod patches: 5 merged, 32 open, 3 closed without merge (status checked 3 October 2026).

| Project | Pull requests | Status |
| --- | --- | --- |
| Ad Astra | [#825](https://github.com/terrarium-earth/Ad-Astra/pull/825) | Open |
| Alex's Caves | [#1759](https://github.com/AlexModGuy/AlexsCaves/pull/1759), [#1760](https://github.com/AlexModGuy/AlexsCaves/pull/1760) | Open |
| AmbientSounds | [#348](https://github.com/CreativeMD/AmbientSounds/pull/348), [#349](https://github.com/CreativeMD/AmbientSounds/pull/349) | Merged |
| Architectury API | [#747](https://github.com/architectury/architectury-api/pull/747) | Open |
| Ars Nouveau | [#2258](https://github.com/baileyholl/Ars-Nouveau/pull/2258) | Open |
| Better Combat | [#623](https://github.com/ZsoltMolnarrr/BetterCombat/pull/623) | Open |
| Butterflies | [#493](https://github.com/doc-bok/Butterflies/pull/493) | Open |
| ChunkSending | [#13](https://github.com/someaddons/chunksending/pull/13) | Open |
| Colorwheel | [#84](https://github.com/djefrey/Colorwheel/pull/84) | Open |
| Cryptic Foes | [#7](https://github.com/min2222/Cryptic-Foes/pull/7) | Closed without merge |
| Curios API | [#639](https://github.com/TheIllusiveC4/Curios/pull/639) | Open |
| Embeddium | [#575](https://github.com/FiniteReality/embeddium/pull/575) | Open |
| Forge | [#10893](https://github.com/MinecraftForge/MinecraftForge/pull/10893), [#10894](https://github.com/MinecraftForge/MinecraftForge/pull/10894) | Merged |
| Fowl Play | [#242](https://github.com/aqariio/Fowl-Play/pull/242), [#243](https://github.com/aqariio/Fowl-Play/pull/243) | Open |
| Hostile Villages | [#37](https://github.com/someaddons/HostileVillages/pull/37) | Open |
| Ice and Fire | [#5641](https://github.com/AlexModGuy/Ice_and_Fire/pull/5641), [#5642](https://github.com/AlexModGuy/Ice_and_Fire/pull/5642), [#5643](https://github.com/AlexModGuy/Ice_and_Fire/pull/5643) | Open |
| ImmediatelyFast | [#586](https://github.com/RaphiMC/ImmediatelyFast/pull/586) | Open |
| ModernFix | [#696](https://github.com/embeddedt/ModernFix/pull/696) | Merged |
| Nether Depths Upgrade | [#67](https://github.com/Scouter456/Nether_Depths_Upgrade/pull/67) | Closed without merge. The 1.20 branch is no longer maintained. |
| OcclusionCulling (EntityCulling) | [#5](https://github.com/LogisticsCraft/OcclusionCulling/pull/5) | Open |
| Oculus | [#869](https://github.com/Asek3/Oculus/pull/869), [#870](https://github.com/Asek3/Oculus/pull/870) | Open |
| Presence Footsteps (Forge) | [#67](https://github.com/PaintNinja/Presence-Footsteps-Forge/pull/67) | Open |
| Radium Re-Reforged | [#8](https://github.com/bigenergy/radium-reforged-patched/pull/8), [#9](https://github.com/bigenergy/radium-reforged-patched/pull/9) | Open |
| Ryoamic Lights | [#54](https://github.com/ThinkingStudios/RyoamicLights/pull/54) | Open |
| Structurify | [#93](https://github.com/Faboslav/structurify/pull/93) | Closed without merge |
| Timeless and Classics Zero (TaCZ) | [#745](https://github.com/MCModderAnchor/TACZ/pull/745) | Open |
| Trackwork | [#70](https://github.com/Endalion/trackwork/pull/70) | Open |
| Valkyrien Skies | [#1981](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1981), [#1982](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1982), [#1983](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1983), [#1984](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1984), [#1985](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1985) | Open |

## Development disclosure

The implementation was substantially written by Bons outside agent sessions. Generative AI assisted portions of the code, testing, packaging and documentation. Original mod authors retain credit for their work.

The project logo is AI-generated promotional concept art. It does not depict content added by this mod.
