# Bons and Furious — notices

Copyright 2026 BonsUnleashed and contributors. Our code and modifications are
provided under GNU GPL version 3. The full text is in LICENSE.

This project patches other authors' installed Minecraft mods. Their names and
interfaces identify compatibility targets, and do not imply endorsement. Their
original code, assets and licenses remain their authors' property. Consult
upstream-credits.json for the exact dependency builds used during development.
License documents are retained under resources/META-INF/licenses with their
upstream project names. Those documents describe their respective upstream
works; they do not relicense other targets or their assets.

From 1.0.20 every patch is a mixin; the sources are in src/bons/furious/mixin/,
the helpers they call in src/. For targets under MIT, Apache-2.0, LGPL or GPL
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
and are marked with @Overwrite and a reason in the source. Every switch is bound to
the tested target builds by method fingerprints (patches/*.json), and a switch
whose fingerprints do not match is not applied at all. Trackwork model geometry
and the two Scorched functions are read from the installed target resources and
changed in memory. Their original resources are not included in this release.

Two MIT-licensed mixin libraries are bundled as Jar-in-Jar: MixinExtras 0.5.0
(LlamaLad7) and MixinSquared 0.3.6-beta.1 (Bawnorton). Their licence texts are
in resources/META-INF/licenses.

Minecraft and Forge are required external dependencies. No Minecraft or upstream
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

Targets added in 1.0.30: Alex's Mobs (Alexthe668, Carro1001, Paint_Ninja, GNU
LESSER GENERAL PUBLIC LICENSE); Bosses of Mass Destruction (CerbonXD, GNU
LESSER GENERAL PUBLIC LICENSE); CIT Reforged (tomwmth, MIT);
CookingForBlockheads (BlayTheNinth, All rights reserved); Create (simibubi,
MIT); Critters and Companions (Joosh, EterDelta, MIT); Dungeon's Delight
(Yirmiri & Betwixer, AZURUNE License); Dynamic Trees (Ferreusveritas, MIT);
Qliphoth Awakening (FINDERFEED, All Rights Reserved); Immersive Engineering
(BluSunrize and Damien A.W. Hazard, Blu's License of Common Sense); Kiwi
Library (Snownee, MIT); L2 Library (lcy0x1 and LightLand team, LGPL v2.1);
LegendaryMonsters (Miauczel, All Rights Reserved); Mutant Monsters (shcott21,
Chumbanotz, Fuzs, tdstress, AGPL-3.0-or-later); Jaden's Nether Expansion
(ThatJadenXgamer, CC-BY-NC-SA-4.0); Particular (MIT); Pipez (Max Henkel, All
rights reserved); Regions Unexplored (UHQ_GAMES, All rights reserved); Ribbits
(Joosh, YUNGNICKYOUNG, HellionGames, Refresh Studios, LGPLv3); Simply Swords
(Sweenus, Timefall Development License); Slash Blade:Resharped (Furia,
NyMmd-MIT:nyatla, ObjModelImporter:forge, Resharped Code: MMF-Group, MIT
License, Art Resources: All Rights Reserved.); Create Slice & Dice
(possible_triangle,
https://github.com/pssbletrngle/sliceanddice/blob/1.20.x/LICENSE.txt). Their
exact tested JAR digests are in upstream-credits.json; targets our test pack
does not install were tested against the listed JARs. No target JAR or asset is
redistributed. Targets under restricted or custom licences get only our own
code, as described above.
