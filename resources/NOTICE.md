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

Targets added in 1.0.36: Aquaculture 2 (Shadowclaimer, Girafi, All Rights
Reserved); ElysiumAPI (ThatJadenXgamer, GNU-LESSER-GENERAL-PUBLIC-LICENSE);
L_Ender's Cataclysm (L_Ender, CC-BY-NC-ND-4.0); Mekanism (Aidancbrady,
Thommy101, Thiakil, pupnewfster, dizzyd, MIT); Rats (AlexThe666,
GizmoTheMoonPig, GNU LESSER GENERAL PUBLIC LICENSE); Joy of Painting (xerca,
GPL-3.0). Their exact tested JAR digests are in upstream-credits.json. No target
JAR or asset is redistributed. Targets under restricted or custom licences get
only our own code, as described above. ElysiumAPI's biome replacer handler
(GPL-2.0 / LGPL-2.1) and Entity Model Features' getEntityVariable (LGPL-3.0) are
carried as modified copies, distributed with their source.

Ideas credited in 1.0.36. Several 1.0.36 switches follow an idea another project
described; we worked from those descriptions only (changelogs, documentation,
option names, issue and pull request text), never from their code: Collections
Of Optimizations, C2ME, Paper, Gale, Leaves, Bellows, Lithium (documentation),
TickMender, Saturn, Shinoyuki-BetterAutoSave, InventoryAdvancementAccelerator,
Sodium, Oculus (pull request #760), AzureLib, Mekanism (its 1.20.4+ commit
messages) and Mekanism: Overclocked. The "Idea:" line of each switch in
bons_and_furious.properties says which idea it follows; "ours" marks our own
designs.

Folded in 1.0.42. Three of our own separate performance mods became switches here: a JEI join-recipe deferral
(jei_join_recipe_deferral), a spawn-area preparation (vanilla_spawn_area_preload) and the tick optimizations of a
Ben's Sharks companion (benssharks_*). Target added in 1.0.42: Ben's Sharks (benndevs, GNU Lesser General Public
License version 3). Its exact tested JAR digest is in upstream-credits.json. No target JAR or asset is
redistributed; the Ben's Sharks switches carry only our own code and call Ben's original methods unchanged.

Targets added in 1.0.45: NaturesAura (Ellpeck, MIT); Subtle Effects
(MincraftEinstein, All rights reserved); Text Animator (Snownee, Apache License
2.0); HT's TreeChop (hammertater, MIT
(https://github.com/hammertater/treechop/blob/main/LICENSE)); AzureLib
(AzureDoom, Gecko, Eliot, Chappie, DerToaster, Tslat, Bvanseg, ZigyTheBird,
MIT); Brutality (goo, MIT); Cable Facades (Leclowndu93150, Thepigcat76,
SuperMartijn642, Heather White, IMS212, ARR); Celestisynth (AquTheSeal, Meme
Man, UnanimousVoid, Massachusetts Institute of Technology (MIT)); Item Filters
(LatvianModder, GNU LGPLv3); L2 Hostility (Love, Cheese and small house plants,
All rights reserved); LowDragLib (KilaBash, WarmthDawn, GPL-3.0 license); More
Artifacts (gobies, All Rights Reserved); Punchy (Punchy, ARR); Species (Ninni,
Noon, Bornulhu, FloofHips, GLaDOS edition, ARR); Unique Dungeons
(beckenrandspringer, MIT). Their exact tested JAR digests are in upstream-
credits.json. No target JAR or asset is redistributed. Targets under restricted
or custom licences get only our own code, as described above. Text Animator's
Effect.create (Snownee, Apache License 2.0) is carried as a modified copy in
textanimator_parse_prechecks (TextAnimatorParsePrecheck.createQuietly; its
source says what changed), distributed with its source; the Apache License 2.0
text is in resources/META-INF/licenses/Snownee--TextAnimator.txt.

Ideas credited in 1.0.45. Several 1.0.45 switches follow an idea another project
described; we worked from those descriptions only (option names and
descriptions, changelogs, issue text), never from their code: Collections Of
Optimizations (its option descriptions and changelogs). The "Idea:" line of each
switch in bons_and_furious.properties says which idea it follows; "ours" marks
our own designs.

Targets added in 1.0.46: Botania (Vazkii, wiiv, williewillus, dylan4ever, Hubry,
Alwinfy, artemisSystem, Falkory220, Wormbo, Botania License); Cerbons API
(CerbonXD, GNU LGPL 3.0); Galosphere (Orcinus, All rights reserved); Quark
(Vazkii, WireSegal, MCVinnyq, Sully,
https://github.com/VazkiiMods/Quark/blob/master/LICENSE.md); Zeta (Vazkii, quat,
IThundxr, siuol, wiresegal, MehVahdJukaar,
https://github.com/VazkiiMods/Zeta/blob/master/LICENSE.md); Amendments
(MehVahdJukaar, Supplementaries Team License v.1.3); Aquamirae (Obscuria,
Obscuria Modding License (OML)); Advanced Wall Climber API (Nyfaria, ARR);
Clanging Howl (Mongoose_artist, Polarice3, All Rights Reserved); Flywheel
(Jozufozu, PepperCode1, MIT); Immersive Aircraft (Luke100000, GPL-3.0-only);
Knight Lib (Xylonity, GNU LGPLv3); Reliquary (authors in its mods.toml, GNU
General Public License v3.0); Smart Particles (Andrew-Chedid, MIT); Spawn
(Ninni, All Rights Reserved). Their exact tested JAR digests are in upstream-
credits.json. No target JAR or asset is redistributed. Targets under restricted
or custom licences get only our own code, as described above.

Ideas credited in 1.0.46. Several 1.0.46 switches follow an idea another project
described; we worked from those descriptions only (option names and
descriptions, changelogs, issue text), never from their code: Moonrise,
NeoForge, Valkyrien Skies, Flywheel, Create and ModernFix. The "Idea:" line of
each switch in bons_and_furious.properties says which idea it follows; "ours"
marks our own designs.

Targets credited from 1.0.47. The two MCreator switches added in 1.0.45 and the
Under the Moon animation guard also target Born in Chaos (Mongoose_artist, All
Rights Reserved), Luminous: Beasts (RipleysMods, Apache License version 2.0),
Rustic Engineer (Mizz, licence not stated) and Under the Moon (Portakal Cevheri,
licence not stated); the 1.0.45 and 1.0.46 notices did not list them. Those
switches carry none of their code, only our own injections. 1.0.47's new switch
changes TerraBlender, credited above. The exact tested JAR digests are in
upstream-credits.json.
