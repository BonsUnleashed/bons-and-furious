# Bons and Furious for Minecraft 1.21.1 / NeoForge

Targeted optimizations and fixes for Minecraft and optional mods, with one switch per control.

Requires Java 21 and NeoForge 21.1.252 or newer. The port carries 205 controls from Bons and Furious 1.0.39. The Forge 1.20.1 release remains on [main](https://github.com/BonsUnleashed/bons-and-furious/tree/main).

[Download 1.0.39+mc1.21.1](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.39%2Bmc1.21.1) · [Installation and compatibility](https://github.com/BonsUnleashed/bons-and-furious/wiki/Minecraft-1.21.1-NeoForge) · [Source and build instructions](source/README.md) · [Control coverage](source/PORT-COVERAGE.md) · [Test results](source/QUALIFICATION.md)

This build changes one control of the previous port, whose dedicated-server, Iris with shaders, Embeddium, Accelerated Rendering, ServerCore, Generator Accelerator and Sable checks still apply. Added for it: dedicated servers with Bye Pregen 1.1.3.1, where every chunk was saved and the terrain matched, and the Embeddium client check. This port has no published speed benchmark; the Forge measurements apply to 1.20.1 only.

Source is GPL-3.0-only; see [LICENSE](source/LICENSE), [NOTICE](source/NOTICE.md) and [upstream credits](source/upstream-credits.json). The source archive contains our source, build wrapper and test receipts, without Minecraft, optional target-mod JARs or shader packs.
