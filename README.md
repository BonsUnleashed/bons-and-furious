# Bons and Furious for Minecraft 1.21.1 / NeoForge

Targeted optimizations and fixes for Minecraft and optional mods, with one switch per control.

Requires Java 21 and NeoForge 21.1.252 or newer. The port carries 205 controls from Bons and Furious 1.0.41. The Forge 1.20.1 release remains on [main](https://github.com/BonsUnleashed/bons-and-furious/tree/main).

[Download 1.0.41+mc1.21.1](https://github.com/BonsUnleashed/bons-and-furious/releases/tag/v1.0.41%2Bmc1.21.1) · [Installation and compatibility](https://github.com/BonsUnleashed/bons-and-furious/wiki/Minecraft-1.21.1-NeoForge) · [Source and build instructions](source/README.md) · [Control coverage](source/PORT-COVERAGE.md) · [Test results](source/QUALIFICATION.md)

This build adds two changes of Forge 1.0.40 and 1.0.41 to the previous port, whose dedicated-server, Iris with shaders, Embeddium, Accelerated Rendering, ServerCore, Generator Accelerator, Sable and Bye Pregen checks still apply. Added for it: dedicated servers with and without Radium 0.13.1, the Embeddium and Iris client checks again, and small clients that check the frame-rate limiter's timing and what runs while it waits. This port has no published speed benchmark; the Forge measurements apply to 1.20.1 only.

Source is GPL-3.0-only; see [LICENSE](source/LICENSE), [NOTICE](source/NOTICE.md) and [upstream credits](source/upstream-credits.json). The source archive contains our source, build wrapper and test receipts, without Minecraft, optional target-mod JARs or shader packs.
