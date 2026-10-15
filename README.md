# Bons and Furious for Minecraft 1.21.1 / NeoForge

Targeted optimizations and fixes for Minecraft and optional mods, with one switch per control.

Requires Java 21 and NeoForge 21.1.252 or newer. The port carries 211 controls from Bons and Furious 1.0.42. The Forge 1.20.1 release remains on [main](https://github.com/BonsUnleashed/bons-and-furious/tree/main).

[Download 1.0.42+mc1.21.1](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.42%2Bmc1.21.1) · [Installation and compatibility](https://github.com/BonsUnleashed/bons-and-furious/wiki/Minecraft-1.21.1-NeoForge) · [Source and build instructions](source/README.md) · [Control coverage](source/PORT-COVERAGE.md) · [Test results](source/QUALIFICATION.md)

This release adds six controls from Forge 1.0.42. Dedicated-server and client checks cover spawn preparation, Ben's Sharks behavior, and JEI recipe order after a disconnect and rejoin. Embeddium and Iris with shaders passed rendering comparisons. These are targeted tests, not comprehensive multiplayer coverage. The public source archive rebuilds the release JAR byte for byte. No speed benchmark has been run for this port; Forge performance figures apply to Minecraft 1.20.1 only.

Source is GPL-3.0-only; see [LICENSE](source/LICENSE), [NOTICE](source/NOTICE.md) and [upstream credits](source/upstream-credits.json). The source archive contains our source, build wrapper and test receipts, without Minecraft, optional target-mod JARs or shader packs.
