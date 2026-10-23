# Bons and Furious for Minecraft 1.21.1 / NeoForge

Targeted optimizations and fixes for Minecraft and optional mods, with one switch per control.

Requires Java 21 and NeoForge 21.1.252 or newer. The port carries 260 controls from Bons and Furious 1.0.47. The Forge 1.20.1 release remains on [main](https://github.com/BonsUnleashed/bons-and-furious/tree/main).

[Download 1.0.47+mc1.21.1](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.47%2Bmc1.21.1) Â· [Installation and compatibility](https://github.com/BonsUnleashed/bons-and-furious/wiki/Minecraft-1.21.1-NeoForge) Â· [Source and build instructions](source/README.md) Â· [Control coverage](source/PORT-COVERAGE.md) Â· [Test results](source/QUALIFICATION.md)

This release retains the previous 211 NeoForge controls and adds 49, including the TerraBlender saved-layer missing-noise fix. Dedicated-server and client tests cover supported behavior and rendering. These are targeted tests, not comprehensive multiplayer coverage. The public source archive rebuilds the release JAR byte for byte. No speed benchmark has been run for this port; Forge performance figures apply to Minecraft 1.20.1 only.

Source is GPL-3.0-only; see [LICENSE](source/LICENSE), [NOTICE](source/NOTICE.md) and [upstream credits](source/upstream-credits.json). The source archive contains our source, build wrapper and test receipts, without Minecraft, optional target-mod JARs or shader packs.
