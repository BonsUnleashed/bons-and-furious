![Bons and Furious cover, AI-generated promotional concept art](https://github.com/BonsUnleashed/bons-and-furious/releases/download/v1.0.15/bons-and-furious-cover.png)

# Bons and Furious

**Your CPU lives its life one tick at a time.**

[**Forge 1.20.1: 1.0.49.2**](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.49.2) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bons-and-furious) · [Modrinth](https://modrinth.com/mod/bons-and-furious) · [Wiki](https://github.com/BonsUnleashed/bons-and-furious/wiki) · [Issues](https://github.com/BonsUnleashed/bons-and-furious/issues)

## Faster worlds. Smoother busy scenes.

Bons and Furious reduces the repeated work behind terrain generation, loading, rendering and server ticks. It also fixes specific freezes, crashes and memory leaks in the mods it supports. On Forge 1.20.1 it has **885 controls covering Minecraft, Forge and 247 other mods**, each with its own switch. Add it alongside your existing performance mods: one JAR, nothing else required.

**In our tested Forge pack: about 21× world-generation throughput, 6½ minutes less waiting to enter a new world, and 25% higher average FPS with 64 animated mobs.**

Those results were measured with **Bons and Furious 1.0.49.1 on Minecraft 1.20.1** at its default settings on 9 and 10 November 2026. Every test ran on the same PC, a Ryzen 9 3900X with an RTX 3080. They are not measurements of the current 1.0.49.2 release or the NeoForge build.

![Whole-pack results measured with Bons and Furious 1.0.49.1 on a Ryzen 9 3900X: 21 times the world-generation throughput (14.1 against 0.66 chunks per second), 59 percent less waiting to enter a new world, 25 percent higher average FPS with 64 animated mobs and 30 percent higher average FPS in a landscape view](https://github.com/BonsUnleashed/bons-and-furious/releases/download/v1.0.15/bons-and-furious-whole-pack-results-v5.png)

## What you get

- **Less waiting for terrain.** Reuse terrain calculations and ground-height estimates instead of repeating them for neighbouring chunks. In the flight test, an average of 489 of 529 nearby chunks were loaded with Bons, compared with 331 without it.
- **Smoother crowded scenes.** Reduce repeated animation, model and rendering work across Minecraft, Embeddium, Oculus, Entity Model Features, Entity Texture Features and other supported mods. The matched 64-mob scene gained 25% average FPS and 27% in 1% lows.
- **Less work during loading and saves.** Speed up recipe processing, resource loading, model rebuilds and chunk serialization, and move supported saved-data and player-file writes off the server thread.
- **Less work in the mods you actually run.** Mobs stop searching for targets while their level has none (Alex's Mobs, Primitive Mobs, Wither Reincarnated and more). Magic mods such as Blood Magic, Goety and Iron's Spells skip repeated lookups, machines from Create, Titanium and Mystical Agriculture do less work per tick, and Xaero's World Map and FTB Chunks keep and upload less.
- **Fewer spikes, freezes and crashes.** Prepare mob classes before their first spawn and repair reproduced threading and world-generation bugs, with 94 fixes in all. Unloaded ship chunks stop ticking, saved rules that reference missing terrain noise no longer crash TerraBlender, and firing the Meka-Gun no longer stops a dedicated server. The first-night improvement was measured separately with 1.0.35; details are below.
- **Less memory held after leaving a world.** Release old worlds and players retained by specific paths in Distant Horizons, JEI, Flywheel, Smart Particles, Alex's Mobs, Iron's Spells, Citadel and other supported mods. Songs from The Immersive Music Mod are streamed instead of each being kept whole in sound memory. These fix reproduced leaks; they do not remove every source of memory growth.
- **Extra savings you choose.** 192 opt-in switches ship off: trade-offs that save work by doing something less often, and gameplay fixes such as boats that survive a fall onto land. Turn on what suits your pack; each one's wiki section says what it changes.

## What is inside

| Part of your pack | Controls | Examples |
| --- | ---: | --- |
| Minecraft itself | 115 | Terrain, chunk loading, saving, networking, frame pacing |
| Forge | 12 | Events, registries, render layers, server status |
| Rendering, shaders and ambience | 108 | Embeddium, Oculus, Entity Model Features, Xaero's maps |
| Shared libraries and server performance mods | 110 | GeckoLib, Curios API, Radium, ModernFix, TerraBlender |
| Ships, structures and distant terrain | 59 | Valkyrien Skies, Distant Horizons, Structurify |
| Content and gameplay | 481 | Alex's Mobs, Ice and Fire, Ars Nouveau, Create, Mekanism |

Every control has its own line in `config/bons_and_furious.properties`. 693 are on by default and the other 192 are the opt-in switches above.

## Measured on an already optimized pack

Both sides kept **ModernFix, FerriteCore, Radium and C2ME**. The client also kept **Embeddium, ImmediatelyFast and EntityCulling**, plus Oculus, Distant Horizons, Complementary shaders and Fresh Animations. These gains come from adding Bons and Furious to that stack.

**9 and 10 November 2026 · Forge 1.20.1 · Bons and Furious 1.0.49.1 at its default settings · four measured runs per build.** Every comparison ran on the same PC with matched settings, with and without Bons.

| Workload | Without Bons | With Bons 1.0.49.1 | Result |
| --- | ---: | ---: | --- |
| Generate 512 chunks, dedicated server | 771 s | 36 s | **21× throughput** |
| Launch the game and enter a new world | 662 s | 274 s | **59% less waiting** |
| 64 animated mobs, average FPS | 37.3 | 46.7 | **25% higher** |
| Same mob scene, 1% low FPS | 24.1 | 30.6 | **27% higher** |
| Looking over the landscape, average FPS | 102.4 | 133.6 | **30% higher** |
| Same view, 1% low FPS | 44.3 | 71.0 | **60% higher** |

Pre-generation speed, 512 chunks on the dedicated server with Chunk Pregenerator (median of four runs):

| Build | Chunks per second | Time for 512 chunks |
| --- | ---: | ---: |
| Bons and Furious 1.0.49.1 | **14.1** | 36.4 s |
| Bons and Furious 1.0.30 | 7.14 | 71.7 s |
| Without Bons | 0.664 | 770.9 s |

Test PC: AMD Ryzen 9 3900X (12 cores, 24 threads), 32 GB DDR4-2400, Samsung 850 PRO SATA SSD, NVIDIA GeForce RTX 3080 10 GB, Windows 10. Server: Java 21, 14 GB heap, 517 mods. Client: Java 25, 14 GB heap, 568 mods, 1080p with Complementary shaders. Forge 47.4.28 on both. C2ME's `reduceLockRadius` was enabled. The FPS tests used the same saved world, with Distant Horizons generation off and JEI's start-up finished before joining.

Generation also used **88% less CPU time** and approximately **94% less memory allocation per chunk**. That is less temporary garbage, not 94% less RAM: live server heap after startup was about 120 MiB (5%) higher with Bons.

Results depend on the pack, settings and hardware. CPU savings may give little extra FPS when the GPU is the limit. In the same run, 1.0.49.1 generated chunks twice as fast as 1.0.30. These figures do not compare with the 5 October test, which ran its server on a different PC with an older pack.

[Full results, test method and earlier benchmarks](https://github.com/BonsUnleashed/bons-and-furious/wiki/Whole-modpack-benchmark).

## A closer look at the improvements

These Forge 1.20.1 examples explain where the savings come from. **They measure individual tasks, not an extra FPS or TPS gain to add to the whole-pack results.**

### World generation and terrain

| Where | What changed | Measured |
| --- | --- | --- |
| **Minecraft** chunk generation | Neighbouring chunk work areas share their ground-height estimates instead of re-scanning the same columns | **95% of surface scans skipped** (349,843 → 16,494 in a 144-chunk run) and **about half the wall time** for that run (mean 162 → 75 s, shared machine) |
| **Minecraft** terrain preparation | The second density-graph pass reuses what the first pass built instead of rebuilding it | **91% less CPU** per NoiseChunk (10.35 → 0.89 ms) and **53% less** per structure-placement height query (17.7 → 8.3 ms) |
| **Minecraft** structure pieces | Structure pieces share their block positions instead of each keeping its own | **1,500 pieces in memory: 506 → 429 MB**, and a full garbage collection 98.5 → 70.9 ms. About 1.3 GB less if every piece of the tested pack were loaded (1.0.49). |
| **Minecraft + TerraBlender** biome searches | Reuse distances already calculated during the same climate search | **13% less time per search** in the recorded pack workload with 1.0.46. |
| **Quark + Zeta** stone clusters | Test the cluster shape before reading blocks outside it | **26.9% less time** per chunk touched by a cluster in the 1.0.46 tests. |
| **Distant Horizons** | The rough-surface generator keeps the parts of the terrain density that depend only on x and z instead of recomputing them at every probe height | **85% less time** per LOD column (3,351 → 494 µs) |
| **TerraBlender** saved surface rules | Skip an obsolete saved rule that needs a terrain noise removed by a mod change; keep current rules | In the 1.0.47 missing-noise test, **16 of 16 chunks failed without the fix; all 16 generated with it**. A crash fix, not a speed measurement. |

### Rendering and animation

| Where | What changed | Measured |
| --- | --- | --- |
| **Embeddium + Fusion** | Hidden connected-texture faces are skipped before their quads are built | **25% less chunk-meshing time** (5.2 → 3.9 s for 733 sections), **41% less allocation**, byte-identical vertices and indices |
| **Embeddium** | A section search whose inputs are all unchanged replays the previous one | **58–68% less time** per search with a still camera; the search was 14.3% of the render thread there |
| **Oculus** | Empty shader render-order graphs are reused instead of rebuilt | **90% less time** per reset (166 → 16 ns), 808 → 0 bytes |
| **Forge** + Oculus | Each block remembers its chunk render layers while the shader pack's layer map is unchanged | **59% less time** per lookup (122 → 50 ns), 12-14% of chunk meshing |
| **Entity Texture Features** | Each sprite's texture id is worked out once instead of on every draw of a chest, sign, bed or banner | **95% less time** per draw (134 → 7 ns) |
| **Entity Model Features + Fresh Animations** | Variable indexes and model-part lookups are reused during animation compilation | Entity-renderer rebuild **15.1–16.6 → 8.8 s**, with 5.1 million variable answers and 692,000 part lookups checked in game |
| **GeckoLib** | Mixed animation easing without boxed doubles | **50% less time** per evaluation (67 → 34 ns) |
| **ImmediatelyFast** | Horse-layer ordering without substrings | **46% less time** (28.9 → 15.5 ns), 64 → 0 bytes |
| **Frame pacing** (client) | The FPS-limiter wait moves before the display update | **84% less frame-interval variation** at p95 (7.63 → 1.25 ms) at the same 120 FPS cap |
| **Xaero's World Map** | New map tiles share one empty height table until they get a height | **15,798 → 104 bytes** and 1,507 → 38 ns per new tile (1.0.49). |

### Loading, saving and networking

| Where | What changed | Measured |
| --- | --- | --- |
| **Farmer's Delight** | Tool-action ingredients share their registry scan during a recipe load | Client recipe-packet decoding **8.8 → 0.4 s**; recipe rebuild **5.5–6.4 → 1.4 s** on the client and **3.6 → 1.2 s** on the server, same ingredients |
| **Minecraft** saving | Saved data and player files are compressed and written by a background writer | Server thread **4.9 → 0.9 s** for 500 data files at once, byte-identical files |
| **Minecraft** networking | Packet bursts are flushed once instead of once per packet | 300 socket flushes → 1; network-thread CPU **8.7 → 2.6 µs per packet**, identical bytes |
| **Forge** | The mod and channel part of the server-list status is reused while it is unchanged | **4.5 → 0.15 ms** per status update with 440 mods; it was 16.6% of the server thread during a pregeneration |
| **JEI** | Each ingredient keeps its display stack with the same expiry semantics | **84% less time** per indexing lookup (830 → 130 ns in the cache harness); the in-game index retained the same categories, recipes and stack content |
| **Resource and data packs** | Folder listings use an index instead of repeatedly walking the ZIP | **305 → 11.9 ms** for 3,472 listings in the 1.0.46 tests. |
| **Forge** registries | Registry holder lists grow in powers of two | **421 → 12 ms** for one pass over the tested pack's 41 registries (1.0.46). |
| **Entity Model Features, GeckoLib, AzureLib and Text Animator** | No exception with a full stack trace just to learn that a token is not a number | An Entity Model Features load pass **239 → 148 ms** in the tested pack (1.0.46). |

### Mobs, machines and server ticks

| Where | What changed | Measured |
| --- | --- | --- |
| **First-night mob spawning** | Prepares mob and AI classes on a background thread before their first spawn | About **a quarter less time in ticks over 50 ms** in the first-night tests on two CPUs with 1.0.35. Spawn rates stay the same. |
| **Forge** events | Tick and render-stage listeners that would return at once are not called | **23% less time** per living entity tick with 139 listeners (933.6 → 722.0 ns), 12% less per client tick with 136 (1.0.49). |
| **Minecraft** block entities | Removed block entities leave the ticker list in one pass | **4,908 → 334 µs** per walk of 20,000 tickers with 25% removed, 1,197 → 313 µs with 5% removed (1.0.49). |
| **Minecraft** inventories | Inventory changes test only the advancement conditions that can match | Opening a 36-slot container **33.6 → 5.0 ms** with the tested pack's 21,600 conditions, 1.7 → 0.5 ms with a player's typical 2,300 (1.0.36). |
| **Create** | Contraption collision boxes are built in one pass | A 400-block carriage **369 → 1.3 ms** and 108 → 0.25 MB, identical boxes |
| **Valkyrien Skies** | Ship chunk bookkeeping, after seven rounds of ship work | **94% less time** (329 → 21 µs, full-pack fixture); physics terrain conversion 65–69% less |
| **Radium** with C2ME | Radium's fast chunk access runs again while C2ME's replacement for it is switched off | **43% less time** per loaded-chunk lookup (81 → 46 ns), `getBlockState` 133 → 73 ns |
| **Architectury API** | Event dispatch without re-resolving method handles | **19× faster** (649 → 34 ns per listener call) |
| **AmbientSounds** | Bounded terrain scan | **88% lower p95** (5.45 → 0.65 ms per analysis) |
| **Mystical Agriculture** | An idle Soul Extractor answers "no recipe" at once instead of scanning every recipe | **70× faster** per lookup with 20 soul recipes (419.8 → 5.9 ns), 187× with 60 (1.0.49). |

### Memory and leaks

| Where | What changed | Measured |
| --- | --- | --- |
| **Distant Horizons, JEI, Flywheel and Smart Particles** | Release old world references held by the repaired caches and menus | **No old worlds retained by the repaired holders** in the 1.0.46 checks after six dimension changes and three reconnects. Other holders remain. |
| **Valkyrien Skies** chunk cleanup | Unload client ship block entities and world chunks when they are no longer needed | Fixes the reproduced buildup of **845 tickers for a 169-block-entity ship** after four revisits, plus world chunks continuing to tick after the server unloaded them (1.0.46). |
| **The Immersive Music Mod** | Songs are streamed instead of each being decoded whole and kept in sound memory | A song starts after **1.6 ms of decoding instead of 180.5 ms**, and sound memory no longer grows with every song heard (1.0.49). |

The mob warm-up uses about 7 MB more class memory and adds roughly half a second to a singleplayer join. Other controls reduce chunk-save allocation, inventory advancement checks, idle mob and camera-shake scans, and repeated work in mods such as Cataclysm and Mekanism. Examples labelled with a version are separate checks, not a new whole-pack benchmark. Distant Horizons and Valkyrien Skies still have other paths that retain old worlds. [Details and remaining limits](https://github.com/BonsUnleashed/bons-and-furious/wiki/Version-history).

[Measurements and caveats](https://github.com/BonsUnleashed/bons-and-furious/wiki/Measurements-and-caveats) · [Every control and its evidence](https://github.com/BonsUnleashed/bons-and-furious/wiki/All-controls)

## Choose your build

| Minecraft | Loader | Controls | Requirements |
| --- | --- | ---: | --- |
| 1.20.1 | Forge | 885 | Forge 47.3.22 or newer |
| 1.21.1 | NeoForge | 556 | Java 21; NeoForge 21.1.252 or newer |

Each build uses one JAR for client and server, with no required target mods. Coverage differs between builds; the figures above are Forge 1.20.1 measurements.

The Forge build contains **597 optimizations, 94 fixes and 194 deliberate behaviour changes**. They include frame pacing, experimental Radium options and, since 1.0.49, 189 opt-in switches that ship off (trade-offs and gameplay fixes); each one is documented individually. All 885 controls are listed in `config/bons_and_furious.properties`. To disable one, set its key to `false` and restart.

## Install the matching build

For Minecraft 1.21.1, use `bons_and_furious-neoforge-1.0.49+mc1.21.1.jar`, Java 21 and NeoForge 21.1.252 or newer. MixinSquared is bundled; NeoForge supplies MixinExtras. [NeoForge setup and compatibility](https://github.com/BonsUnleashed/bons-and-furious/wiki/Minecraft-1.21.1-NeoForge).

For Minecraft 1.20.1 / Forge:

1. Download `bons_and_furious-1.0.49.2.jar` from the [1.0.49.2 release](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.49.2) (SHA-256 in `SHA256SUMS.txt`) and put it in `mods/` on the client and on the server. Nothing else is required; every target mod is detected at load. The two small mixin libraries it uses, MixinExtras and MixinSquared, are bundled inside it.
2. Start once. The mod writes its config file with its defaults (every switch on except the 192 opt-in switches: three older ones and 189 trade-offs and gameplay fixes added in 1.0.49) and logs how many controls are enabled.
3. To turn one off, set its key to `false` and restart. Client-only patches (renderer, shaders, ambience) never load on a dedicated server.

JVM overrides, log messages and troubleshooting: [Installation and configuration](https://github.com/BonsUnleashed/bons-and-furious/wiki/Installation-and-configuration).

## Compatibility

Keep your optimization stack: Embeddium, ImmediatelyFast, ModernFix, FerriteCore, Radium and C2ME. Bons and Furious changes paths that still did unnecessary work in the tested pack, including a few inside Embeddium, ImmediatelyFast and Oculus themselves. With C2ME installed it also gives Radium and ModernFix three optimizations back that they switch off for every C2ME build, as long as the C2ME module that would clash with each one is off in `c2me.toml`.

On newer Forge, JEI and ModernFix builds that already include eight of these changes, the corresponding Bons controls step aside automatically. With Collections Of Optimizations, shared controls also check which implementation is active so the same optimization is not applied twice or left disabled in both mods. Since 1.0.49, Collections Of Optimizations 5.8 adds nothing Bons and Furious does not already do, apart from the [listed exceptions](https://github.com/BonsUnleashed/bons-and-furious/wiki/Redundant-mods#collections-of-optimizations). [Compatibility details](https://github.com/BonsUnleashed/bons-and-furious/wiki/Known-conflicts-and-workarounds).

It also holds up in very large packs. A dedicated server with 640 popular Forge mods started, ran and generated the same terrain with Bons and Furious 1.0.49 as without it.

## Forge 1.20.1 covered mods (all optional)

**Rendering, shaders and ambience:** Embeddium, Oculus, ImmediatelyFast, Entity Texture Features, Entity Model Features, EntityCulling, Fusion, Colorwheel, Ryoamic Lights, Presence Footsteps, AmbientSounds, CIT Reforged, FancyMenu, Particular, Subtle Effects, Smart Particles, Punchy, Blessfulled, FTB Chunks, Photon, Pick Up Notifier, Shoulder Surfing Reloaded, The Immersive Music Mod, Tooltip Overhaul, Touch'n Motion, Xaero's Map - Waystones Compatibility, Xaero's Minimap, Xaero's World Map.

**Shared libraries and server performance mods:** GeckoLib, Architectury API, Curios API, Structure Gel API, Radium, ModernFix, ChunkSending, TerraBlender, Citadel, Lionfish API, Placebo, Kiwi, Cucumber, CoFH Core, L2 Library, Pehkui, Almost Unified, ElysiumAPI, Zeta, Flywheel, AzureLib, Knight Lib, LDLib, Advanced Wall Climber API, Item Filters, Text Animator, AllTheLeaks, Balm, Celestial Core, Cerbon's API, EndingLibrary, FTB XMod Compat, GTBC's SpellLib, Immersive Optimization, Konkrete, KubeJS, Moonlight Library, NeoBackports, Patchouli, Projectile Damage Attribute, Pufferfish's Attributes, Ritchie's Projectile Library, Titanium, XaeroLib.

**Ships, structures and distant terrain:** Valkyrien Skies, Trackwork, Distant Horizons, Structurify, Sakes Structures, Oh The Biomes We've Gone, Dynamic Trees, Biolith, Block Swap, Integrated API, Integrated Villages, Lithostitched, Lootr.

**Content and gameplay:** Alex's Caves, Ice and Fire, Ars Nouveau, JEI, Farmer's Delight, Relics, Timeless and Classics Zero (TaCZ), Terramity, Ad Astra, Fowl Play, Butterflies, Goblins Tyranny, Under the Moon, Nether Depths Upgrade, Spawn, Cryptic Foes, Hostile Villages, Scuba Gear, Occult, Scorched and Better Combat, Create, Pipez, Storage Drawers, Immersive Engineering, Slice & Dice, Cooking for Blockheads, Alex's Mobs, Mowzie's Mobs, Mutant Monsters, Critters and Companions, Bosses of Mass Destruction, FD Bosses, Legendary Monsters, Ribbits, Artifacts, Simply Swords, SlashBlade: Resharped, Dungeons Delight, Jaden's Nether Expansion, Regions Unexplored, L_Ender's Cataclysm, Mekanism, Rats, Aquaculture, XercaPaint, Botania, Reliquary, Immersive Aircraft, Born in Chaos, Luminous Beasts, Rustic Engineer, Unique Dungeons, More Artifacts, Nature's Aura, L2 Hostility, Species, Celestisynth, Brutality, Clanging Howl, TreeChop, Cable Facades, Adam's Ars Plus, Advanced Bow Enchantments, Amendments, Animus, Armageddon, Ars 'n' Spells, Ars Additions, Ars Elemental, Ars Énergistique, Better Spellcasting, Biomancy, Blood Magic, Born in Chaos, Brutal Bosses, Caster Curios Bonus, Celestial Enchantments, Chimes, Cognition, Companions!, Composite Material, Corpse, Cosmetic Armor Reworked, Create Crafts & Additions, Create: Copycats+, Create: Solar Powered, Creatures and Beasts: Unofficial Port, Critters n' Crawlers, Dark Doppelganger, Deeper and Darker, Dodo's Mobs, Domestication Innovation, Duplicationless, Easy Villagers, Echelon, EEEAB's Mobs, Enigmatic Addons, Enigmatic Delicacy, Enigmatic Legacy, Extra Hostile Neural Networks, Fallen Traits, Faunify, Fluidium, From the Shadows Reborn, Goety, Goety Awaken, Goety Cataclysm, Goety Delight, Goety Hostility, GTBC's Geomancy Plus, Hexerei, Hostile Neural Networks, Immersive Armors, Immersive Weathering, Industrial Foregoing, Iron's Spells 'n Spellbooks, Just Dire Things, L2 Archery, L2 Backpack, L2 Complements, Legendary Spellbooks, Lethality, Macabre: Call of False Prophets, Mahou Tsukai, Marium's Soulslike Weaponry, Mekanism: Weapons, MmmMmmMmmMmm, Mob Grinding Utils, Modular Routers, More Relics, Mystical Agriculture, Myths & Legends, Odd Accessories, Opposing Force, Peyro's Scythe, Powah, Primitive Mobs, Refined Mod, RevelationFix, Saint's Dragons, Sanguine Neural Networks, Simply Swords Overhaul, Skarrier Mobs, Sky Arena, Snuffles, SomakeSpells, Sophisticated Backpacks, Summonity, Supplementaries, T.O Magic 'n Extras, TACT, Terra Curio, The Undergarden, Tome of Blood: Rebirth, Tome of Wonders, Tons Of Enchants, Too Many Bows, Too Many Glyphs, Torchmaster, Transmog, Unique Accessories, Wither Reincarnated.

Coverage means the tested build and the specific code paths of each mod, not every feature. Tested builds per mod: [Compatibility and target versions](https://github.com/BonsUnleashed/bons-and-furious/wiki/Compatibility-and-target-versions).

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

The 1.0.49.2 release JAR was tested in client and dedicated-server copies of the reference pack before release. The whole-pack benchmark above was measured with 1.0.49.1 on 9 and 10 November 2026.

### Upstream pull requests

Pull requests sent to the projects this mod patches: 21 merged, 76 open, 20 closed without merge (status checked 8 November 2026).

| Project | Pull requests | Status |
| --- | --- | --- |
| AmbientSounds | [#348](https://github.com/CreativeMD/AmbientSounds/pull/348), [#349](https://github.com/CreativeMD/AmbientSounds/pull/349) | Merged |
| Butterflies | [#493](https://github.com/doc-bok/Butterflies/pull/493) | Merged |
| Dungeon's Delight | [#126](https://github.com/Yirmiri/Dungeons-Delight/pull/126) | Merged |
| Entity Model Features | [#591](https://github.com/Traben-0/Entity_Model_Features/pull/591), [#592](https://github.com/Traben-0/Entity_Model_Features/pull/592), [#611](https://github.com/Traben-0/Entity_Model_Features/pull/611) | Merged |
| Entity Texture Features | [#508](https://github.com/Traben-0/Entity_Texture_Features/pull/508) | Merged |
| Forge | [#10893](https://github.com/MinecraftForge/MinecraftForge/pull/10893), [#10894](https://github.com/MinecraftForge/MinecraftForge/pull/10894), [#10899](https://github.com/MinecraftForge/MinecraftForge/pull/10899) | Merged |
| Just Enough Items | [#4525](https://github.com/mezz/JustEnoughItems/pull/4525) | Merged. Released in JEI 15.62.0.218. |
| Just Enough Items | [#4612](https://github.com/mezz/JustEnoughItems/pull/4612) | Merged |
| ModernFix | [#696](https://github.com/embeddedt/ModernFix/pull/696) | Merged |
| OcclusionCulling (EntityCulling) | [#5](https://github.com/LogisticsCraft/OcclusionCulling/pull/5) | Merged |
| Radium Re-Reforged | [#9](https://github.com/bigenergy/radium-reforged-patched/pull/9), [#15](https://github.com/bigenergy/radium-reforged-patched/pull/15), [#16](https://github.com/bigenergy/radium-reforged-patched/pull/16), [#17](https://github.com/bigenergy/radium-reforged-patched/pull/17), [#18](https://github.com/bigenergy/radium-reforged-patched/pull/18) | Merged |
| TerraBlender | [#241](https://github.com/Glitchfiend/TerraBlender/pull/241) | Merged |
| Ad Astra | [#825](https://github.com/terrarium-earth/Ad-Astra/pull/825) | Open |
| Alex's Caves | [#1759](https://github.com/AlexModGuy/AlexsCaves/pull/1759), [#1760](https://github.com/AlexModGuy/AlexsCaves/pull/1760), [#1762](https://github.com/AlexModGuy/AlexsCaves/pull/1762) | Open |
| Alex's Mobs | [#2385](https://github.com/AlexModGuy/AlexsMobs/pull/2385), [#2386](https://github.com/AlexModGuy/AlexsMobs/pull/2386) | Open |
| Architectury API | [#747](https://github.com/architectury/architectury-api/pull/747) | Open |
| Artifacts | [#518](https://github.com/ochotonida/artifacts/pull/518) | Open |
| Baked Substring Index | [#1](https://github.com/mezz/baked-substring-index/pull/1) | Open |
| Better Combat | [#623](https://github.com/ZsoltMolnarrr/BetterCombat/pull/623) | Open |
| Bosses of Mass Destruction Forge | [#31](https://github.com/CERBON-MODS/Bosses-of-Mass-Destruction-FORGE/pull/31) | Open |
| CIT Reforged | [#16](https://github.com/tomwmth/cit-reforged/pull/16) | Open |
| Citadel | [#232](https://github.com/AlexModGuy/Citadel/pull/232) | Open |
| Colorwheel | [#84](https://github.com/djefrey/Colorwheel/pull/84) | Open |
| Cucumber Library | [#62](https://github.com/BlakeBr0/Cucumber/pull/62) | Open |
| Curios API | [#639](https://github.com/TheIllusiveC4/Curios/pull/639), [#644](https://github.com/TheIllusiveC4/Curios/pull/644), [#645](https://github.com/TheIllusiveC4/Curios/pull/645) | Open |
| Dynamic Trees | [#1230](https://github.com/DynamicTreesTeam/DynamicTrees/pull/1230), [#1231](https://github.com/DynamicTreesTeam/DynamicTrees/pull/1231) | Open |
| Embeddium | [#575](https://github.com/FiniteReality/embeddium/pull/575), [#576](https://github.com/FiniteReality/embeddium/pull/576), [#577](https://github.com/FiniteReality/embeddium/pull/577), [#578](https://github.com/FiniteReality/embeddium/pull/578), [#579](https://github.com/FiniteReality/embeddium/pull/579), [#580](https://github.com/FiniteReality/embeddium/pull/580), [#581](https://github.com/FiniteReality/embeddium/pull/581) | Open |
| Farmer's Delight | [#1397](https://github.com/vectorwing/FarmersDelight/pull/1397), [#1398](https://github.com/vectorwing/FarmersDelight/pull/1398) | Open |
| FDBosses | [#42](https://github.com/FINDERFEED/FDBosses/pull/42) | Open |
| Fowl Play | [#242](https://github.com/aqariio/Fowl-Play/pull/242), [#243](https://github.com/aqariio/Fowl-Play/pull/243) | Open |
| Fusion | [#316](https://github.com/SuperMartijn642/Fusion/pull/316), [#317](https://github.com/SuperMartijn642/Fusion/pull/317), [#318](https://github.com/SuperMartijn642/Fusion/pull/318) | Open |
| Hostile Villages | [#37](https://github.com/someaddons/HostileVillages/pull/37), [#38](https://github.com/someaddons/HostileVillages/pull/38) | Open |
| Ice and Fire | [#5641](https://github.com/AlexModGuy/Ice_and_Fire/pull/5641), [#5642](https://github.com/AlexModGuy/Ice_and_Fire/pull/5642), [#5643](https://github.com/AlexModGuy/Ice_and_Fire/pull/5643), [#5644](https://github.com/AlexModGuy/Ice_and_Fire/pull/5644), [#5645](https://github.com/AlexModGuy/Ice_and_Fire/pull/5645) | Open |
| Immersive Engineering | [#6448](https://github.com/BluSunrize/ImmersiveEngineering/pull/6448) | Open |
| L2 Library | [#34](https://github.com/Minecraft-LightLand/L2Library/pull/34), [#35](https://github.com/Minecraft-LightLand/L2Library/pull/35) | Open |
| Legendary Monsters | [#13](https://github.com/Miauczel/Legendary-Monsters-1.20.1/pull/13) | Open |
| Lionfish API | [#5](https://github.com/lender544/Lionfish-API/pull/5), [#6](https://github.com/lender544/Lionfish-API/pull/6) | Open |
| Mowzie's Mobs | [#65](https://github.com/BobMowzie/MowziesMobs-Public/pull/65) | Open |
| Mutant Monsters | [#139](https://github.com/Fuzss/mutant-monsters/pull/139) | Open |
| Oculus | [#869](https://github.com/Asek3/Oculus/pull/869), [#870](https://github.com/Asek3/Oculus/pull/870), [#871](https://github.com/Asek3/Oculus/pull/871), [#872](https://github.com/Asek3/Oculus/pull/872) | Open |
| Oh The Biomes We've Gone | [#421](https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/pull/421) | Open |
| Particular | [#59](https://github.com/Leclowndu93150/Particular/pull/59) | Open |
| Pehkui | [#636](https://github.com/Virtuoel/Pehkui/pull/636) | Open |
| Presence Footsteps (Forge) | [#67](https://github.com/PaintNinja/Presence-Footsteps-Forge/pull/67) | Open |
| Regions Unexplored | [#234](https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/pull/234) | Open |
| Relics | [#357](https://github.com/Octo-Studios/relics/pull/357), [#358](https://github.com/Octo-Studios/relics/pull/358) | Open |
| Ribbits | [#85](https://github.com/yungnickyoung/Ribbits/pull/85) | Open |
| Ryoamic Lights | [#54](https://github.com/ThinkingStudios/RyoamicLights/pull/54), [#55](https://github.com/ThinkingStudios/RyoamicLights/pull/55) | Open |
| Smart Particles | [#10](https://github.com/chedidandrew/Smart_Particles/pull/10) | Open |
| Species | [#91](https://github.com/Peculiar-Room/Species/pull/91) | Open |
| Storage Drawers | [#1307](https://github.com/jaquadro/StorageDrawers/pull/1307) | Open |
| Timeless and Classics Zero (TaCZ) | [#745](https://github.com/MCModderAnchor/TACZ/pull/745) | Open |
| Trackwork | [#70](https://github.com/Endalion/trackwork/pull/70) | Open |
| TreeChop | [#363](https://github.com/hammertater/treechop/pull/363) | Open |
| Valkyrien Skies | [#1981](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1981), [#1982](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1982), [#1983](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1983), [#1984](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1984), [#1985](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1985), [#1997](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1997) | Open |
| Almost Unified | [#144](https://github.com/AlmostReliable/almostunified/pull/144) | Closed without merge |
| Ars Nouveau | [#2258](https://github.com/baileyholl/Ars-Nouveau/pull/2258) | Closed without merge |
| ChunkSending | [#13](https://github.com/someaddons/chunksending/pull/13) | Closed without merge |
| Cooking for Blockheads | [#812](https://github.com/TwelveIterations/CookingForBlockheads/pull/812) | Closed without merge |
| Cryptic Foes | [#7](https://github.com/min2222/Cryptic-Foes/pull/7) | Closed without merge |
| Forge | [#10895](https://github.com/MinecraftForge/MinecraftForge/pull/10895) | Closed without merge. Withdrawn in favour of #10899 on the 26.3 branch, with the design the maintainer asked for. |
| Forge | [#10896](https://github.com/MinecraftForge/MinecraftForge/pull/10896) | Closed without merge. Withdrawn: too little left to gain after #10893. |
| ImmediatelyFast | [#586](https://github.com/RaphiMC/ImmediatelyFast/pull/586), [#588](https://github.com/RaphiMC/ImmediatelyFast/pull/588), [#589](https://github.com/RaphiMC/ImmediatelyFast/pull/589) | Closed without merge |
| Jaden's Nether Expansion | [#353](https://github.com/ThatJadenXgamer/Jadens-Nether-Expansion/pull/353) | Closed without merge. Withdrawn: the 1.20.1 branch is no longer maintained and the 1.21.1 rework already covers it. |
| Just Enough Items | [#4527](https://github.com/mezz/JustEnoughItems/pull/4527) | Closed without merge. Withdrawn: it did not fit what that cache is for. |
| ModernFix | [#697](https://github.com/embeddedt/ModernFix/pull/697), [#698](https://github.com/embeddedt/ModernFix/pull/698), [#699](https://github.com/embeddedt/ModernFix/pull/699) | Closed without merge |
| Nether Depths Upgrade | [#67](https://github.com/Scouter456/Nether_Depths_Upgrade/pull/67) | Closed without merge. The 1.20 branch is no longer maintained. |
| Placebo | [#125](https://github.com/Shadows-of-Fire/Placebo/pull/125) | Closed without merge. The 1.20 branch is no longer maintained; newer branches removed this event. |
| Radium Re-Reforged | [#8](https://github.com/bigenergy/radium-reforged-patched/pull/8), [#11](https://github.com/bigenergy/radium-reforged-patched/pull/11) | Closed without merge |
| Structurify | [#93](https://github.com/Faboslav/structurify/pull/93) | Closed without merge |

## Development disclosure

The implementation was substantially written by Bons outside agent sessions. Generative AI assisted portions of the code, testing, packaging and documentation. Original mod authors retain credit for their work.

The project logo is AI-generated promotional concept art. It does not depict content added by this mod.
