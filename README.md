![Bons and Furious cover, AI-generated promotional concept art](https://github.com/dialectikproductions/bons-and-furious/releases/download/v1.0.15/bons-and-furious-cover.png)

# Bons and Furious

**Your CPU lives its life one tick at a time.**

[**Download 1.0.15**](https://github.com/dialectikproductions/bons-and-furious/releases/tag/v1.0.15) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bons-and-furious) · [Modrinth](https://modrinth.com/mod/bons-and-furious) · [Wiki](https://github.com/dialectikproductions/bons-and-furious/wiki) · [Issues](https://github.com/dialectikproductions/bons-and-furious/issues)

Bons and Furious patches 74 specific, measured hot spots in vanilla Minecraft 1.20.1 and in 33 popular Forge mods, from Valkyrien Skies, Distant Horizons and Alex's Caves to Embeddium, Oculus and GeckoLib. Each patch is one switch in one config file. It applies only to the exact mod build it was tested against and leaves anything else untouched, with one line in the log.

**Minecraft 1.20.1 · Forge 47.3.22 or newer · no required dependencies · one JAR for client and server**

## What it does

- **60 optimizations** give the same results with less work: fewer allocations, no repeated lookups, no state rebuilt only to come out identical. Terrain preparation, ship chunk bookkeeping, shader graph resets, animation easing and event dispatch are the largest.
- **10 fixes** repair reproduced server freezes, worker-thread crashes and generation exceptions, mostly where Distant Horizons or C2ME worker threads meet a content mod's world generation.
- **4 deliberate changes** (frame pacing, the Occult bed scan, Fowl Play flight targets, Scorched sandcrab processing) trade a documented behaviour difference for a large saving.

All 74 are listed in `config/bons_pure_optimizations.properties` with their target mod, tested build, side and measurement. Set any key to `false` and restart. Every control is explained in the [wiki](https://github.com/dialectikproductions/bons-and-furious/wiki).

## Measured results

| Where | What changed | Measured |
| --- | --- | --- |
| **Minecraft** terrain preparation | Shared density-graph nodes are transformed once per chunk instead of once per reference | **59.6% less memory allocated** (1.47 GB → 0.60 GB over 64 height queries) and 56% less thread CPU |
| **Valkyrien Skies** | Ship chunk bookkeeping, after seven rounds of ship work | **94% less time** (329 → 21 µs, full-pack fixture); physics terrain conversion 65–69% less |
| **Distant Horizons** + content mods | Six generation-context fixes | A reproduced Scuba Gear stall recovers from **6.1 to 20 TPS** |
| **Oculus** | Empty shader render-order graphs are reused instead of rebuilt | **90% less time** per reset (166 → 16 ns), 808 → 0 bytes |
| **Embeddium** | Chunk-mesh upload classification without stream pipelines | **59% less time** (151 → 62 ns per region with 8 outputs) |
| **GeckoLib** | Mixed animation easing without boxed doubles | **50% less time** per evaluation (67 → 34 ns) |
| **Architectury API** | Event dispatch without re-resolving method handles | **19× faster** (649 → 34 ns per listener call) |
| **AmbientSounds** | Bounded terrain scan | **88% lower p95** (5.45 → 0.65 ms per analysis) |
| **Frame pacing** (client) | The FPS-limiter wait moves before the display update | **84% less frame-interval variation** at p95 (7.63 → 1.25 ms) at the same 120 FPS cap |
| **ImmediatelyFast** | Horse-layer ordering without substrings | **46% less time** (28.9 → 15.5 ns), 64 → 0 bytes |

Smaller allocation and lookup savings in Ars Nouveau, Curios API, Alex's Caves, Ice and Fire, TaCZ and others are on the wiki.

> **How to read these numbers.** Each figure measures the named method, phase or reproduction in a fixture, on the build it was measured on. The figures are not additive and do not add up to an FPS or TPS gain. A matched whole-modpack comparison is still pending. Method, fixture settings and the result for every control: [Measurements and caveats](https://github.com/dialectikproductions/bons-and-furious/wiki/Measurements-and-caveats).

## Covered mods (all optional)

**Rendering, shaders and ambience:** Embeddium, Oculus, ImmediatelyFast, Ryoamic Lights, Presence Footsteps, AmbientSounds.

**Shared libraries:** GeckoLib, Architectury API, Curios API, Structure Gel API.

**Ships, structures and distant terrain:** Valkyrien Skies, Trackwork, Distant Horizons, Structurify, Sakes Structures.

**Content and gameplay:** Alex's Caves, Ice and Fire, Ars Nouveau, Timeless and Classics Zero (TaCZ), Terramity, Ad Astra, Fowl Play, Butterflies, Goblins Tyranny, Under the Moon, Nether Depths Upgrade, Spawn, Cryptic Foes, Hostile Villages, Scuba Gear, Occult, Scorched, and our own Living Engineering addon.

Coverage means the tested build and the specific code paths of each mod, not every feature. Tested builds per mod: [Compatibility and target versions](https://github.com/dialectikproductions/bons-and-furious/wiki/Compatibility-and-target-versions).

## Install

1. Download `bons_pure_optimizations-1.0.15.jar` from the [1.0.15 release](https://github.com/dialectikproductions/bons-and-furious/releases/tag/v1.0.15) (SHA-256 in `SHA256SUMS.txt`) and put it in `mods/` on the client and on the server. Nothing else is required; every target mod is detected at load.
2. Start once. The mod writes its config file with every switch on and logs how many controls are enabled.
3. To turn one off, set its key to `false` and restart. Client-only patches (renderer, shaders, ambience) never load on a dedicated server.

**Upgrading from 1.0.14 or earlier:** replace the JAR and remove *Bons to Be Afloat* (Bons Valkyrien Fixes) and *Bons Worldgen Compatibility* if they are still installed. Their work is included in 1.0.15, and two copies would patch the same classes twice.

JVM overrides, log messages and troubleshooting: [Installation and configuration](https://github.com/dialectikproductions/bons-and-furious/wiki/Installation-and-configuration).

## Compatibility

Keep your optimization stack: Embeddium, ImmediatelyFast, ModernFix, FerriteCore, Radium and C2ME. Bons and Furious changes paths that still did unnecessary work in the tested pack, including a few inside Embeddium, ImmediatelyFast and Oculus themselves. Every patch is bound to the tested build of its target; another build is left untouched with one `WARN` line. Because no target is required, the mod loads in any 1.20.1 Forge pack.

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
creates a local compile-only view of the fields our VS adapter adds, compiles
the helpers, relocates our own packages, and packages the editable resources.
It writes only `build/` and `dist/` inside this checkout. It never changes the
supplied installations or any saves.

## Licence and upstream

Licensed GPL-3.0-only ([LICENSE](LICENSE)). Upstream attribution is in [NOTICE.md](NOTICE.md); the tested dependency builds are listed in [upstream-credits.json](upstream-credits.json).

The published 1.0.15 JAR passed a 470-mod dedicated-server regression, ship save-data checks and reload checks for five Trackwork models, and an independent build from this source matched all 47 compiled classes. These are correctness checks, separate from the timings above.

### Upstream pull requests

Of the 42 external drafts across 32 mods, these have been submitted so far (status checked 29 September 2026):

| Project | Pull request | Status |
| --- | --- | --- |
| Ad Astra | [terrarium-earth/Ad-Astra #825](https://github.com/terrarium-earth/Ad-Astra/pull/825) | Open |
| Alex's Caves | [AlexModGuy/AlexsCaves #1759](https://github.com/AlexModGuy/AlexsCaves/pull/1759) | Open |
| Alex's Caves | [AlexModGuy/AlexsCaves #1760](https://github.com/AlexModGuy/AlexsCaves/pull/1760) | Open |
| AmbientSounds | [CreativeMD/AmbientSounds #348](https://github.com/CreativeMD/AmbientSounds/pull/348) | Open |
| Architectury API | [architectury/architectury-api #747](https://github.com/architectury/architectury-api/pull/747) | Open |
| Ars Nouveau | [baileyholl/Ars-Nouveau #2258](https://github.com/baileyholl/Ars-Nouveau/pull/2258) | Open |
| Butterflies | [doc-bok/Butterflies #493](https://github.com/doc-bok/Butterflies/pull/493) | Open |
| Cryptic Foes | [min2222/Cryptic-Foes #7](https://github.com/min2222/Cryptic-Foes/pull/7) | Open |
| Curios API | [TheIllusiveC4/Curios #639](https://github.com/TheIllusiveC4/Curios/pull/639) | Open |
| Embeddium | [FiniteReality/embeddium #575](https://github.com/FiniteReality/embeddium/pull/575) | Open |
| Fowl Play | [aqariio/Fowl-Play #242](https://github.com/aqariio/Fowl-Play/pull/242) | Open |
| Fowl Play | [aqariio/Fowl-Play #243](https://github.com/aqariio/Fowl-Play/pull/243) | Open |
| Hostile Villages | [someaddons/HostileVillages #37](https://github.com/someaddons/HostileVillages/pull/37) | Open |
| Ice and Fire | [AlexModGuy/Ice_and_Fire #5641](https://github.com/AlexModGuy/Ice_and_Fire/pull/5641) | Open |
| Ice and Fire | [AlexModGuy/Ice_and_Fire #5642](https://github.com/AlexModGuy/Ice_and_Fire/pull/5642) | Open |
| ImmediatelyFast | [RaphiMC/ImmediatelyFast #586](https://github.com/RaphiMC/ImmediatelyFast/pull/586) | Open |
| Nether Depths Upgrade | [Scouter456/Nether_Depths_Upgrade #67](https://github.com/Scouter456/Nether_Depths_Upgrade/pull/67) | Closed without merge. The maintainer no longer maintains the 1.20 branch. |
| Oculus | [Asek3/Oculus #869](https://github.com/Asek3/Oculus/pull/869) | Open |
| Presence-Footsteps-Forge | [PaintNinja/Presence-Footsteps-Forge #67](https://github.com/PaintNinja/Presence-Footsteps-Forge/pull/67) | Open |
| Ryoamic Lights | [ThinkingStudios/RyoamicLights #54](https://github.com/ThinkingStudios/RyoamicLights/pull/54) | Open |
| Structurify | [Faboslav/structurify #93](https://github.com/Faboslav/structurify/pull/93) | Open |
| Timeless and Classics Zero (TaCZ) | [MCModderAnchor/TACZ #745](https://github.com/MCModderAnchor/TACZ/pull/745) | Open |
| Trackwork | [Endalion/trackwork #70](https://github.com/Endalion/trackwork/pull/70) | Open |
| Valkyrien Skies | [ValkyrienSkies/Valkyrien-Skies-2 #1981](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1981) | Open |
| Valkyrien Skies | [ValkyrienSkies/Valkyrien-Skies-2 #1982](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1982) | Open |
| Valkyrien Skies | [ValkyrienSkies/Valkyrien-Skies-2 #1983](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1983) | Open |
| Valkyrien Skies | [ValkyrienSkies/Valkyrien-Skies-2 #1984](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1984) | Open |
| Valkyrien Skies | [ValkyrienSkies/Valkyrien-Skies-2 #1985](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1985) | Open |

The remaining drafts are being ported and built against the upstream source before submission.

## Development disclosure

The implementation was substantially written by Bons outside agent sessions. Generative AI assisted portions of the code, testing, packaging and documentation. Original mod authors retain credit for their work.

The project logo is AI-generated promotional concept art. It does not depict content added by this mod.
