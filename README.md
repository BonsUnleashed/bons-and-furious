# Bons and Furious for Minecraft 1.21.1 / NeoForge

Targeted optimizations and fixes for Minecraft and optional mods, with one switch per control.

Requires Java 21 and NeoForge 21.1.252 or newer. The port carries 561 controls from Bons and Furious 1.0.50. The Forge 1.20.1 release remains on [main](https://github.com/BonsUnleashed/bons-and-furious/tree/main).

[Download 1.0.50+mc1.21.1](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.50%2Bmc1.21.1) · [Installation and compatibility](https://github.com/BonsUnleashed/bons-and-furious/wiki/Minecraft-1.21.1-NeoForge) · [Source and build instructions](source/README.md) · [Control coverage](source/PORT-COVERAGE.md) · [Test results](source/QUALIFICATION.md)

This release adds five controls to the 556 of 1.0.49.2+mc1.21.1 and carries the fixes of Forge 1.0.50 that apply on NeoForge. Dedicated-server and client tests cover supported behavior and rendering, including one server and one client with almost every supported mod installed at once. These are targeted tests, not comprehensive multiplayer coverage. The public source archive rebuilds the release JAR byte for byte. Apart from one chunk-section rebuild timing, no speed benchmark has been run for this port; Forge performance figures apply to Minecraft 1.20.1 only.

Source is GPL-3.0-only; see [LICENSE](source/LICENSE), [NOTICE](source/NOTICE.md) and [upstream credits](source/upstream-credits.json). The source archive contains our source, build wrapper and test receipts, without Minecraft, optional target-mod JARs or shader packs.
