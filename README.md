# Bons and Furious

**Your CPU lives its life one tick at a time.**

Targeted optimizations and compatibility fixes for Minecraft 1.20.1 Forge:
vanilla, 33 optional mod targets, and 74 individual controls. The 1.0.15 release
includes the former Bons to Be Afloat / Bons Valkyrien Fixes companion.

Valkyrien Skies ship bookkeeping and terrain conversion lead the release,
alongside vanilla terrain preparation, Embeddium, Oculus, GeckoLib,
AmbientSounds, and generation fixes involving Distant Horizons and content mods.
Changes address specific tested paths alongside the existing optimization stack.

Historical VS companion fixtures measured chunk bookkeeping at 329 → 21 µs and
real-section terrain conversion at 44.7 → 15.7 µs. Vanilla's 64-query preparation
fixture allocated 59.6% fewer bytes. These are separate workloads and builds;
their results cannot be added together or treated as whole-game FPS/TPS gains.
A matched whole-modpack with/without benchmark is still pending.

## Install

Use Minecraft 1.20.1 and Forge 47.3.22 or later within 47.x (tested: 47.4.16).
The JAR works on client and server; optional targets need not all be installed.
Replace the older Bons and Furious JAR and remove the separate Bons Valkyrien
Fixes companion. Configure changes in
`config/bons_pure_optimizations.properties`, then restart. Unsupported target
bytecode is skipped and logged. Some compatibility repairs deliberately change
loading, generation or timing behavior; read the comments beside each control.

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

## Contributions and license

Upstream submissions are being prepared. The current review contains 42 external
drafts across 32 mods; this is not a claim that those PRs have been submitted.

GNU GPL v3 for our code; see LICENSE and NOTICE.md for upstream attribution and
scope. AI tools assisted development, tests and documentation. Substantial
implementation work was written by the creator outside AI-agent sessions.
