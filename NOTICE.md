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

Targets added in 1.0.49: AnotherFlashlightModPort (sozda, MCreator, Not
specified); Armageddon (choup, MCreator, Academic Free License v3.0); BCLib
(Quiqueck, paulevs, Bulldog83, Raijin2312, MIT (CC BY-NC-SA 4.0 for some
Assets)); BadOptimizations (Thosea, MIT); Block Swap (Corgi Taco, LGPLv3); Blood
Magic (WayofTime, CC BY 4.0); Creatures and Beasts: Unofficial Port (Joosh,
CGessinger, and HellionGames, LGPLv3); Caster Curios Bonus (Rinko1231, FXT1206,
GPLv3); Chimes (BlewberryPie, All Rights Reserved); Cognition (Cyanogen, MIT);
Composite Material (rawchickenNEG, BF_Meow, AmengyLoad, Nanaka_Kyou, Wusagi,
SirDurpsalot, MIT(Code), All Rights Reserved(Textures and Models)); Cosmic
Horizons (DEA__TH, All Rights Reserved); Critters n' Crawlers (FaeStickenFool,
All Rights Reserved); Dodo's Mobs (dodo3737, All Rights Reserved (no licence
given: unfilled ${mod_license} placeholder)); Echelon (d0ugdimmadome, MIT);
EndingLibrary (MegaDarkness, MIT); Enigmatic Legacy (Aizistral, Enigmatic Legacy
License); Hostile Neural Networks (authors in its mods.toml, MIT License);
Mekanism: Weapons (oMeranha, MIT); Mystical Agriculture (BlakeBr0, MIT);
Opposing Force (Unusual Modding, MIT); Patchouli (Vazkii, CC BY-NC-SA 3.0);
Peyro's Scythe (Rinko1231, Linkany121, GPLv3); Pick Up Notifier (Fuzs, MPL-2.0);
Powah (owmii,Technici4n, LGPLv3); Sanguine Neural Networks (MtcLeo05, MIT);
Shoulder Surfing Reloaded (Exopandora, MIT); Sky Arena (JrDemiurge, MIT); Terra
Curio (Westernat, Magic Harp, nakinosi, CoooBird, Gray, Amanita, LGPL v3); Terra
Curio (Confluence Team, LGPL v3); The Undergarden (quek, MIT); Tome of Wonders
(.magmastrider., -Ghoul-, platypushasnohat, MIT); Tons Of Enchants
(DeltaJay/deltajay_ on CurseForge, MIT Licence); Just Dire Things (Direwolf20,
MIT); RevelationFix (Programmer: MegaDarkness Planner: Player__7, KunZhong
Artists: XiaoWu, Nuttar, VoidRegion, BaiXi_a, CrystalSkeleton9, Fracsail , ARR);
Adam's Ars Plus (Adam aka Th3S3nate, GNU Lesser General Public License v3.0);
Advanced Bow Enchantments (Doug, All Rights Reserved); All The Leaks
(Uncandango, MIT); Animus (TeamDman, Saereth, MIT); Ars Additions (authors in
its mods.toml, LGPLv3); Ars Elemental (Alexthw, GNU Lesser General Public
License v3.0); Ars 'n' Spells (otectus, GNU GPLv3); Ars Énergistique (90,
dkmk100, LGPLv3.0); Balm (BlayTheNinth, All rights reserved); Better
Spellcasting (Kayn, LGPL-2.1-or-later); Biolith (TerraformersMC, gniftygnome,
LGPL-3.0); Biomancy 2 (Elenterius, RhinoW, MIT & CC-BY-NC-4.0); Biomancy
(Elenterius, RhinoW, MIT & CC-BY-NC-4.0); Blast From The Past (Team Recrafted,
Zigy, Clonz, Tenebris Mors, All Rights Reserved); Blessfulled (AquTheSeal, All
Rights Reserved); bloblings (myles, MCreator, Not specified); Bloblings: Genesis
(MCreator, Not specified); brutalbosses mod (Someaddons, ARR); Brutality (goo,
MIT); Call of Yucatán (Furti_Two, Sweetygamer, Not specified); Capability Syncer
(SizableShrimp, not stated in the jar (no mods.toml, no licence file));
Celestial Core (Xiaoyue, LGPL-2.1); Celestial Enchantments (Xiaoyue, LGPL-2.1);
Companions! (Xylonity, ModderG, GNU GPL v3.0); Create: Copycats+ (Lysine,
Bennyboy1695, Redcat_XVIII, All Rights Reserved); Corpse (Max Henkel, All rights
reserved); CosmeticArmorReworked (zlainsama, Minecraft Mod Public License
1.0.1); Create Crafts & Additions (MRH0, MIT License, Copyright 2022 MRH0);
Create: Solar Powered (HachiroUwU, MIT); Critters and Companions (Joosh,
EterDelta, ARR); DarkDoppelganger (Bandit-Bytes, Alshanex, All Rights Reserved);
Deeper and Darker (Kyanite Team, AGPL-3.0); Domestication Innovation
(Alexthe668, FallenReaper, Yosh, GNU LESSER GENERAL PUBLIC LICENSE);
MmmMmmMmmMmm (MehVahdJukaar, Bonusboni, Plantkillable, Supplementaries Team
License v.1.5); Duplicationless (Kall, MIT); Easy Villagers (Max Henkel, All
rights reserved); EEEAB's Mobs (EEEAB, LGPL-3.0); Enigmatic Addons (Auviotre,
All Rights Reserved); Enigmatic Delicacy (Auviotre, All Rights Reserved); Extra
Hostile Neural Networks (LMor, nexxx_7, MIT license); Fallen Lib (rtxyd,
LGPL-2.1); Fallen Traits (Kayn, LGPL-2.1-or-later); Faunify (Pepper, MIT);
Faunify (Pepper, Rat (Animations), KaktusDoesMusic (Music), MIT); Fluidium
(Kall, MIT); From the Shadows Reborn (Sonmok14,L_Ender, All Rights Reserved);
FTB Chunks (FTB Team, All Rights Reserved); FTB Library (FTB Team, All Rights
Reserved); FTB XMod Compat (FTB Team, All Rights Reserved); Goety (Polarice3,
MIT License); Goety Cataclysm (Polarice3, All Rights Reserved); Goety Awaken
(Team k1sak1, Code: LGPL-2.1 | Assets: ARR); Goety Delight (V_BlackCAT,wu1wu2,Ba
iXi_a,Upharsin,Kun_Zhong,Fracsail,bai_ze_li,vanilla_haozi,Xiao_wu,Lll252,sybsysj
,raw_chicken__, Code:LGPL-2.1-only,Assets:All Rights Reserved); Goety Hostility
(UnanimousVoid, All Rights Reserved); GTBC's Geomancy Plus (GameTechBC, All
Rights Reserved); GTBC's SpellLib (GameTechBC, All Rights Reserved); Hexerei
(JoeFoxe, MIT); IceAndFire Patcher (IAFEnvoy, LGPL-3.0); Immersive Armors
(Luke100000, GNU); Immersive Optimization (Luke100000, GPL-3.0); Immersive
Weathering (Ordana, MehVahdJukaar, Keybounce, LGPLv3); Industrial Foregoing
(authors in its mods.toml, MIT); Integrated API (CraisinLord, All rights
reserved); Integrated Villages (CraisinLord, All rights reserved); Iron's Spells
'n Spellbooks (Iron431, Lab3, All Rights Reserved); Konkrete (Keksuccino,
Apache-2.0); KubeJS (LatvianModder, GNU LGPLv3); L2 Archery (lcy0x1 and
LightLand team, LGPL v2.1); L2 Backpack (lcy0x1 and LightLand team, LGPL v2.1);
L2 Complements (Love, Cheese and small house plants, All rights reserved); L2
Library (lcy0x1 and LightLand team, LGPL v2.1); Legendary Spellbooks (Higurashi
(Higurashi34m), saadlampy, Cutlery0980, LGPL-3.0); Lethality (Daphne, MIT);
Lithostitched (Apollo, MIT); Lootr (Noobanidus,
https://github.com/noobanidus/Lootr/blob/1.16/LICENSE); Macabre: Call of False
Prophets (Pixelgeist666, Academic Free License v3.0); Mahou Tsukai (stepsword,
All Rights Reserved); Mob Grinding Utils (vadis365, flanks255, All rights
reserved); ModernFix (embeddedt, GNU LGPL 3.0); Modular Routers (authors in its
mods.toml, MIT); Monster Expansion (Saksolm, ALL RIGHTS RESERVED); Moonlight
Library (MehVahdJukaar, gplv3); More Relics (Blorbilon, All Rights Reserved);
Mowzie's Mobs (BobMowzie, Wadoo, Vakypanda, Noonyez, pau101, custom); Myths &
Legends (oVinter, All Rights Reserved); NeoBackports (Nikdo53, LGPL v2.1); Odd
Accessories (johnjohnjohn, All Rights Reserved); ParticlePresets (MinoBanana,
Academic Free License v3.0); Photon (KilaBash, GPL-3.0 license); Polymorph
(Illusive Soulworks, LGPL-3.0-or-later); Primitive Mobs (daveyx0 (original),
Primitive Mobs Revival, CC BY 3.0); Projectile Damage Attribute (Daedelus,
GPL-3.0); Pufferfish's Attributes (Pufferfish, LGPL-3.0); Realm RPG: Quests &
Rewards (NoCube, All Rights Reserved); Refined Mod (RefinedRain, All Rights
Reserved); Ritchie's Projectile Library (rbasamoyai, MIT License); Saint's
Dragons (Leon Saint, MIT); Sanguine Neural Networks (MtcLuna05, MIT); Simply
Swords Overhaul (JrDemiurge, MIT); Simply Swords (Sweenus, Timefall Development
License); Skarrier Mobs (Skarrier, All Rights Reserved); Snuffles
(SchnappDragon, Edmord17, MIT License); SomakeSpells (TeenLe, Maycoz, All Rights
Reserved); Sophisticated Backpacks (authors in its mods.toml, All Rights
Reserved); Marium's Soulslike Weaponry (Marium Bacchus, CC0-1.0); Summonity
(fevzi, All Rights Reserved); Supplementaries (MehVahdJukaar, Plantkillable,
Supplementaries Team License v.1.3); TACT (TelepathicGrunt, MIT); Timm
(CrawKatt, Pardys, GPL-3.0 License); Titanium (authors in its mods.toml, GNU
LESSER GENERAL PUBLIC LICENSE); Tome of Blood: Rebirth (mystchonky, GNU Lesser
General Public License v3.0); Too Many Bows (Bandit-Bytes, MIT); Tooltip
Overhaul (Xylonity, ModderG, GNU GPLv3); Too Many Glyphs (DerringersMods, LGPL
v2.1+); Torchmaster (Xalcon, All Rights Reserved); Touch'n Motion (STRadaT, GNU
GPL 3.0); Transmog (Hidoni, MIT); T.O Magic 'n Extras (GameTechBC, All Rights
Reserved); Unique Accessories (Genzyuro, All Rights Reserved); Vivecraft
(fayer3, Ferri_Arnus, IMS, jrbudda, minecraft player, Techjar, LGPLv3); Wither
Reincarnated (Alexander's Fun and Games, All Rights Reserved); XaeroLib
(xaero96, All Rights Reserved); Xaero's Minimap (xaero96, All Rights Reserved);
Xaero's Map - Waystones Compability (arcanealloy, All rights reserved); Xaero's
World Map (xaero96, All Rights Reserved). Their exact tested JAR digests are in
upstream-credits.json. No target JAR or asset is redistributed. Targets under
restricted or custom licences get only our own code, as described above.

Ideas credited in 1.0.49. Several 1.0.49 switches follow an idea another project
described; we worked from those descriptions only (option names and
descriptions, changelogs, issue text), never from their code: Collections Of
Optimizations (its option descriptions and changelogs), Leaf, Flywheel, Create
and Minecraft. The "Idea:" line of each switch in bons_and_furious.properties
says which idea it follows; "ours" marks our own designs.

Target added in 1.0.50: ForgeEndertech (EnderLanky, All rights reserved), the
library of AdLods and the other EnderTech mods. The switch that changes it
carries none of its code, only our own injections; its exact tested JAR digest
is in upstream-credits.json. The other 1.0.50 switches change Minecraft,
Embeddium, Fusion, Valkyrien Skies, Simply Swords and Cosmic Horizons, all
credited above.
