# Bons and Furious — notices

Copyright 2026 BonsUnleashed and contributors. Our code and modifications are
provided under GNU GPL version 3. The full text is in LICENSE.

This project patches other authors' installed Minecraft mods. Their names and
interfaces identify compatibility targets, and do not imply endorsement. Their
original code, assets and licenses remain their authors' property. Consult
upstream-credits.json for the exact dependency builds used during development.
License documents are retained under src/main/resources/META-INF/licenses with their
upstream project names. Those documents describe their respective upstream
works; they do not relicense other targets or their assets.

From 1.0.20 every patch is a mixin; the sources are in src/main/java/bons/furious/mixin/,
the helpers they call in src/main/java/. For targets under MIT, Apache-2.0, LGPL or GPL
licences some mixins carry a modified copy of a target method (an @Overwrite),
distributed with its source, build instructions and applicable notices. From
1.0.21 one mixin also carries a modified copy of Entity Texture Features'
Material.buffer handler (LGPL-3.0) in place of ETF's own, which it cancels.
From 1.0.23 the Colorwheel (LGPL-3.0) and OcclusionCulling (MIT, bundled in
EntityCulling) switches carry modified copies of the methods they replace.

For targets with restricted or unclear redistribution permissions (All Rights
Reserved, No-Derivatives or custom licences) and for Minecraft itself, the
mixins contain only our own code, attached with targeted injections; the
unchanged instructions stay in the installed class. The exceptions are a few
short methods that Forge's Mixin 0.8.5 can only change by replacing them whole
(interface methods, which accept no injection, and a method whose new logic is
our own algorithm): those replacements are written from the method's behaviour
and are marked with @Overwrite and a reason in the source. Each guarded switch is bound to
the tested target builds by method fingerprints (patches/*.json), and a guarded switch
whose fingerprints do not match is not applied at all. The two Scorched functions are read from the installed target resources and
changed in memory. Their original resources are not included in this release.

MixinSquared 0.3.6-beta.1 (Bawnorton, MIT) is bundled as a NeoForge Jar-in-Jar.
MixinExtras is provided by NeoForge. The retained licence texts are under
src/main/resources/META-INF/licenses.

Minecraft 1.21.1, NeoForge 21.1.252 or newer and Java 21 are required external dependencies. No Minecraft or upstream
mod JAR is bundled. No upstream textures, sounds or promotional artwork are
included. Optional target mods must be obtained from their authors.

AI tools assisted development, testing and documentation; the creator reports
substantial implementation work outside those sessions.

Targets added in 1.0.26: JEI (mezz, MIT), Farmer's Delight (vectorwing, MIT),
Fusion (SuperMartijn642, All Rights Reserved), and Relics (SSKirillSS / Octo
Studios, All Rights Reserved). Their repository URLs and exact tested JAR
digests are in upstream-credits.json. No target JAR or asset is redistributed.
Fusion's record-hash replacement is our own memoization around the JDK's
ObjectMethods bootstrap; its source explains the independent implementation.
Relics is changed by an early-return injection. The other Fusion methods keep
their installed bodies and receive targeted hooks. The separate BonsFusion
project is not bundled in this release.

Minecraft 1.21.1 port: the original 1.0.27 implementation is adapted to NeoForge
and Mojang-named APIs. The current target builds and authors are recorded in
upstream-credits.json; upstream-credits-1.20.1.json preserves historical provenance.
Oculus switches target Iris; unavailable mods and patches fixed upstream are
listed in PORT-COVERAGE.md. Historical source comments and measurements refer
to the original 1.20.1 work, not new 1.21.1 performance results.

Targets added in 1.0.38+mc1.21.1, which ports the switches Forge 1.0.36 added
together with the MixinSquared cancellation check of Forge 1.0.38: Aquaculture 2
(Shadowclaimer, Girafi, All Rights Reserved) and L_Ender's Cataclysm (L_Ender;
source code CC-BY-NC-ND-4.0, assets all rights reserved). Their exact tested JAR
digests are in upstream-credits.json. No target JAR or asset is redistributed; these
targets get only our own code, as described above. Entity Model Features'
getEntityVariable (LGPL-3.0) is carried as a modified copy, distributed with its
source.

Ideas credited in 1.0.38+mc1.21.1. Several of the switches this release ports follow
an idea another project described; we worked from those descriptions only
(changelogs, documentation, option names, issue and pull request text), never from
their code: Collections Of Optimizations, C2ME, Paper, Gale, Leaves, Bellows,
Lithium (documentation), TickMender, InventoryAdvancementAccelerator, Sodium (pull
request #2679) and AzureLib. The Forge release's bons_and_furious.properties names
the idea behind each switch on its "Idea:" line; "ours" marks our own designs.
