# Qualification — 2026-10-03

**PASS** for `bons_and_furious-neoforge-1.0.27+mc1.21.1.jar`.

SHA-256: `04d25a1bbed6f8d3eb40e8c5de71195f0847d886db37b48459c4d6818147ab82`

Tested with Minecraft 1.21.1, NeoForge 21.1.252 and Temurin Java 21.0.12.1.
The same packaged JAR was used in all five final production runs. The development
probe is a separate mod and is absent from the release JAR. Evidence is in
`../evidence/qualification.json` in the source bundle.

| Check | Result |
| --- | --- |
| Gradle build and pinned guard verification | Pass: 78 guarded controls, 560 method/class-shape checks |
| Original Forge and new NeoForge name audits | Both pass; deliberately wrong mod ID is rejected |
| Production dedicated server, base game | Clean exit; all applicable guarded targets load |
| Production server with 28 optional target/dependency JARs | Pass; Radium collision-group assertions also pass |
| Production Iris client with 41 optional JARs | World loaded, 120 client ticks; Complementary Reimagined r5.9.3 active; screenshot visually inspected |
| Production Embeddium client with 39 optional JARs | World loaded, 120 client ticks; all applicable targets load; bone lookup correctly yields to Embeddium |
| Target coverage across production runs | All 78 guarded controls obtain an APPLY decision in at least one run; secondary compatibility rules can still defer individual patches |
| Production terrain enabled/disabled | Exactly matching blocks and biome hashes across 3,670,016 blocks in Overworld, Nether and End |
| GeckoLib easing enabled/disabled | 33,924 samples; identical output hashes |
| Iris entity vertex conversion enabled/disabled | 4,096 vertices, 54-byte stride; identical output hashes; captured IDs and destination boundary checked |
| Static mixin signature audit | 269 checks; three raw-class gaps resolved by Embeddium's runtime-added methods and verified in its client run |
| Packaging | Deterministic JAR layout; one bundled MixinSquared dependency; no probe classes, legacy Forge metadata or configuration BOM |

The terrain probe uses seed `708193841`, generates a two-chunk decoration margin,
then compares the interior 4×4 chunks at full vertical height in each dimension.
Block states use registry names and sorted property names. Early exploratory
fixtures sampled chunks before their neighbours finished decoration and were
discarded; the final production comparison is the authoritative result.

The GeckoLib/Iris output comparison uses separate development runs with the
switches enabled and disabled. The production shader run produces the same
hashes. These are correctness checks, not performance measurements.

The production and NeoForm development versions of `Climate.Parameter.distance`
and `CubicSampler.gaussianSampleVec3` have equivalent code with different return
or temporary-local layouts. Their bytecode was inspected, and exactly two extra
fingerprints are accepted. All other target checks retain one accepted hash.

## Integration decisions

- The published 1.0.27 source was copied into a separate project. Original Forge
  sources and the managed play instances were not edited by this task.
- Minecraft and loader APIs were ported to Mojang names, Java 21, NeoForge
  capabilities, events, metadata and resource-pack interfaces.
- Optional integrations were adapted to the pinned 1.21.1 builds, including
  GeckoLib, Curios, EMF/ETF, Iris, Embeddium, Ars Nouveau, Fowl Play, Spawn,
  Distant Horizons and C2ME. Exact target hashes are in `targets.lock.json`.
- Embeddium's indexed bone lookup takes precedence. The replacement would
  otherwise inject into Embeddium's overwrite. Native Lithium owns its own
  block-entity and POI implementations; Radium uses the separately tested path.
- Scorched's two function fixes use the singular `function` resource directory
  and data-pack format 48. Original resources are hash-checked and changed in
  memory; they are not redistributed.

## Limits and unrelated upstream messages

These are smoke tests and selected equivalence checks. They do not establish
every gameplay path, every option combination, long-session stability or a speed
improvement. Shader testing used one shader pack. COO metadata support is retained,
but an actual 1.21.1 NeoForge COO build was not tested. New target versions may
skip a patch until their changed methods are reviewed.

The optional fixtures include upstream warnings/errors also observed with Bons
switches disabled: Scorched's unrelated old potion command, Spawn's missing
`roly_poly` loot-table item, and some content-model/animation/light definitions.
ModernFix presents advisory loading warnings about its suggested performance
mods; the probe continues only when NeoForge offers its nonfatal Continue button.
Complementary also reports uniforms for newer Minecraft biomes absent in 1.21.1;
the shader still compiles and renders in the inspected screenshot.

Radium 0.13.1 chooses Fabric-style method names in a Mojang-mapped development
environment, causing its own collision-group assertion on player login. The
development client comparison uses native Lithium. Production tests use Radium
and pass the same assertion path; no Radium JAR was altered.

## Workspace provenance

Read the root `AGENTS.md`, `CLAUDE.md`, shared memory protocol and complete topic
index, relevant Bons release/COO/coordination notes, matching ledger sections,
recent changelog pages, selected release identity, pins, source and release
receipts. Shared-memory bootstrap passed. History was inventoried and relevant
local evidence retrieved; this does not claim every retained conversation was
read. Public target metadata and publisher hashes were recorded locally.

This task did not publish a release, install anything into the managed Forge
client/server, or modify their world saves. All runtime tests used isolated,
disposable fixtures created under this port project.
