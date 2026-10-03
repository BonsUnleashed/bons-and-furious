# Bons and Furious — Minecraft 1.21.1 / NeoForge

This is the Minecraft 1.21.1 port of the published Bons and Furious 1.0.27 source.
It uses mod ID `bons_and_furious`, version `1.0.27+mc1.21.1`, and requires Java 21
and NeoForge 21.1.252 or newer. Minecraft is restricted to exactly 1.21.1.
The tested loader is 21.1.252; later loaders are not automatically qualified.

The original release SHA-256 is
`196e7bc1996495537c5e1f4cf8aa34cc6662cdc46600992b950de40ffc9ad0d6`.
This port has its own artifact name and does not replace the Forge 1.20.1 release.

## Install

Put `bons_and_furious-neoforge-1.0.27+mc1.21.1.jar` in the `mods` folder of a
Minecraft 1.21.1 NeoForge instance. Optional target mods are not required; their
patches only apply when the relevant targets and method fingerprints match.
MixinSquared is bundled; NeoForge supplies MixinExtras.

The first launch creates `config/bons_and_furious.properties`. Set a switch to
`false` and restart to disable it. Existing JVM disable flags remain supported.
Client rendering patches only run on clients. The same JAR works on dedicated
servers, where client-only target mods should not be installed.

## Coverage and compatibility

The port ships **82 controls**, including 78 groups guarded by 560 method or
class-shape checks, three legacy mixin controls and the Scorched function fix.
Of the original 127 controls, 13 are retired because the target was fixed or
changed upstream, and 32 depend on unavailable 1.21.1 NeoForge target mods.
[PORT-COVERAGE.md](PORT-COVERAGE.md) accounts for every original control.

The Oculus-prefixed switches now target Iris. The tested Iris pairing is Iris
1.8.12 with Sodium 0.6.13. Embeddium 1.0.15 is tested separately; do not combine
Embeddium with Sodium/Iris. Embeddium owns model bone lookup when installed.
Native Lithium owns block-entity state and POI indexing when installed. The
Radium compatibility paths apply to Radium 0.13.1, not native Lithium.

C2ME compatibility is locked to the reviewed `0.4.0-alpha.0.122+1.21.1` build.
Distant Horizons direct reads stay disabled when C2ME's replacement chunk IO is
active. Restoring ModernFix's stronghold cache still respects its user options.
The Radium experimental control now enables spawning while leaving entity block
caching off; expiring chunk tickets are already enabled in this Radium release.

The 1.0.27 foreign-patch coordination mechanism reads NeoForge's `[[mixins]]`
metadata, including side lists and `requiredMods`. Its COO rules are retained
for matching declared mixins. No actual 1.21.1 NeoForge COO combination is
qualified by this port. Missing, changed or unknown targets stay unpatched.

Exact dependency versions, authors, licenses and hashes are in
[upstream-credits.json](upstream-credits.json). They describe development targets,
including targets inspected and then retired, not required installation lists.

## Validation

See [QUALIFICATION.md](QUALIFICATION.md) and the accompanying qualification
receipt for the exact tested artifact and runtime results. The checks cover
production server loading, optional mod integrations, in-world client rendering,
and deterministic enabled/disabled comparisons. They are not a comprehensive
modpack or long-session test. No 1.20.1 performance claim is reused for this port.

## Build

Use Python 3.11+ and a Java 21 JDK:

```text
python fetch_targets.py
gradlew.bat build
```

On Unix, use `./gradlew build`. The wrapper pins Gradle 9.2.1 and the build pins
ModDevGradle 2.0.148 and NeoForge 21.1.252. The first build needs network access.
`fetch_targets.py` verifies publisher SHA-1 values from `targets.lock.json` and
extracts nested compile targets. It writes into sibling `targets` and
`targets-nested` directories. It does not install those mods into a game.

`build` checks all fingerprints against the pinned targets and refuses a stale
guard table. Do not refresh fingerprints merely to silence a mismatch: review
the target's behavior first. Two methods have explicitly reviewed alternate
fingerprints for the equivalent NeoForm development and Mojang production
instruction layouts. No wildcard hashes are accepted.

The output is in `build/libs/`. `src/probe` is a separate qualification mod;
`gradlew.bat probeJar` builds it separately. It is excluded from the release JAR.
The archive order, timestamps and compression are deterministic.

License: GPL-3.0-only; see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Optional
target JARs, Minecraft, shader packs, game worlds and local account data are not
included in the source archive.
