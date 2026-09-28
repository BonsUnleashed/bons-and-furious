![Bons and Furious cover — AI-generated promotional concept art](https://github.com/dialectikproductions/bons-and-furious/releases/download/v1.0.15/bons-and-furious-cover.png)

# Bons and Furious

**Big systems. Less wasted work.**

Lighter Minecraft terrain preparation. Faster ship bookkeeping. Repaired world-generation stalls. Then deeper savings inside the renderer, shader system and animation library your pack already uses.

**Minecraft 1.20.1 · Forge · 74 individual controls · Vanilla + 33 optional mod targets**

## The biggest breakthroughs

| System | What changes for the better | Measured result |
| --- | --- | --- |
| **Minecraft itself** | Prepare terrain with far less allocation. | **59.6% less** memory allocated in terrain preparation. 1.474 GB → 0.595 GB. |
| **Valkyrien Skies** | Seven rounds of ship improvements, united. | **94% less** chunk-bookkeeping time in the full-pack fixture. 329 µs → 21 µs. |
| **Distant Horizons + major content mods** | Repair the paths that can stall a world. | **6 fixes** for reproduced generation-context failures. 6.057 → 20 TPS · Scuba Gear + DH. |
| **GeckoLib** | Cut the cost of mixed animation easing. | **49.6% less** time per mixed-easing evaluation. 67.35 ns → 33.92 ns. |
| **Embeddium** | Even the optimizer has more to give. | **59.2% less** upload-classification time. 151.23 ns → 61.63 ns. |
| **Oculus** | Stop rebuilding empty shader graphs. | **90.2% less** time per all-empty graph reset. 166.37 ns → 16.38 ns. |



### What makes these changes significant

**Minecraft + ModernFix:** density-graph transformation reuse cut allocated bytes by 59.6% and thread CPU by 56.3% across 64 Overworld height queries. The existing ModernFix wrapper cache remains intact. This is terrain preparation, not a claim about total world-generation speed.

**Valkyrien Skies:** seven rounds of work are now included in 1.0.15. Alongside the 329 → 21 µs chunk-bookkeeping result, physics terrain conversion took 65–69% less time, and one grown-ship collision-check fixture fell from 1,422 → 250 ns. The work also addresses initial terrain snapshot ordering, ship-edge NaN motion and sculk compatibility. These are successive historical companion comparisons, not stock VS versus the final combined build. The latest sweep did not show a measurable whole-tick improvement.

**Distant Horizons with Alex’s Caves, Ice and Fire and other content mods:** six generation-context fixes address reproduced stalls, worker-thread crashes and exceptions. The 6.057 → 20 TPS recovery belongs specifically to a Scuba Gear/DH stall reproduction; it is not the result of toggling every fix together.

**GeckoLib, Embeddium and Oculus:** remove repeated work in systems that other mods depend on. GeckoLib mixed-easing time fell 49.6%, with eight million float-component comparisons matching. Embeddium’s eight-output upload classification fell 59.2%. Oculus’s all-empty graph reset fell 90.2%, with 808 → 0 allocated bytes. These are native method fixtures; simple linear easing, populated shader graphs and full GPU uploads have their own costs.

## More improvements at a glance

| System | Result and context |
| --- | --- |
| **Frame pacing** | **83.6% lower variation.** Buffer-swap interval variation at p95; 7.6261 → 1.2473 ms at the same 120 FPS cap. Achieved FPS stayed about 116.6. |
| **Architectury API** | **19× faster dispatch.** 649 → 34 ns in the two-million-call listener-dispatch harness. Listener execution itself is outside this measurement. |
| **AmbientSounds** | **88.1% lower phase p95.** Terrain analysis: 5.4517 → 0.6496 ms. Whole-frame p99 did not improve in that run. |
| **ImmediatelyFast** | **46.4% less method time.** Horse-layer ordering: 28.85 → 15.46 ns, with 64 → 0 allocated bytes in the native fixture. |



**Ars Nouveau:** four-piece equipment-perk snapshots took 33.6% less time (592.51 → 393.16 ns). **Curios API:** the no-cached-modifier cleanup path removed 392 allocated bytes per call. Alex’s Caves equipment enumeration and Ice and Fire reference-list initialization also receive targeted allocation improvements.

> Every number above measures its named workload and historical build. Method, phase, allocation and stall-repair results are different measurements. They are not additive and do not establish a combined FPS/TPS gain. The matched whole-modpack with/without comparison remains pending.

## Vanilla + 33 optional mods, covered

**74 individually configurable controls**, plus the separate Trackwork model-parent repair. The target set includes one Bons addon. Install the mods you use; none of these targets is mandatory. Coverage refers to the specific tested versions and paths, not every feature of a mod.

**Rendering, shaders and ambience:** Embeddium, Oculus, ImmediatelyFast, Ryoamic Lights, Presence Footsteps, AmbientSounds.

**Shared libraries:** GeckoLib, Architectury API, Curios API, Structure Gel API.

**Ships, structures and distant terrain:** Valkyrien Skies, Trackwork, Distant Horizons, Structurify, Sakes Structures.

**Content and gameplay:** Alex’s Caves, Ice and Fire, Ars Nouveau, Timeless and Classics Zero (TaCZ), Terramity, Ad Astra, Fowl Play, Butterflies, Goblins Tyranny, Under the Moon, Nether Depths Upgrade, Spawn, Cryptic Foes, Hostile Villages, Scuba Gear, Occult, Scorched, Bons in a Lifetime / Living Engineering.

## Built alongside your optimization stack

Keep Embeddium, ImmediatelyFast, ModernFix, FerriteCore, Radium and C2ME. Bons adds separately implemented changes to specific paths that still did unnecessary work in the tested pack, including methods inside optimization mods. Compatibility is version-specific; this is not a universal zero-overlap claim.

## Installation and control

Minecraft Java **1.20.1**, **Forge 47.3.22+ within 47.x**; tested with **47.4.16**. Install the same JAR on client and server where you want the applicable fixes. Every target mod is optional.

**Upgrading:** replace the old Bons and Furious JAR and remove the separate Bons Valkyrien Fixes / Bons to Be Afloat JAR. Its work is included in 1.0.15; do not install both companions.

Set individual controls in `config/bons_pure_optimizations.properties` and restart. Unsupported target bytecode is skipped and logged. Generation repairs, frame timing, loading/range changes and some ship fixes have documented deliberate behavior differences. The Trackwork model-parent compatibility repair is separate from the 74 controls.

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

## Tested, open source, and built to give back

The published 1.0.15 JAR passed the 470-mod server regression, ship save-data checks and focused initial/reload checks for five Trackwork models. The independent source build matched all 47 staged classes. These are correctness and regression checks, separate from the historical timings above.

[Source, license and build instructions](https://github.com/dialectikproductions/bons-and-furious) · [Download 1.0.15](https://github.com/dialectikproductions/bons-and-furious/releases/tag/v1.0.15) · [Report an issue](https://github.com/dialectikproductions/bons-and-furious/issues)

Licensed GPL-3.0-only, with upstream attribution and notices retained. Contributions are being prepared for the original projects: 42 external drafts across 32 mods. Source ports and upstream validation remain in progress; no completed PR submission is claimed.

## Development disclosure

The implementation was substantially written by Bons outside agent sessions. Generative AI assisted portions of the code, testing, packaging and documentation. Original mod authors retain credit for their work.

The project logo is AI-generated promotional concept art. It does not depict content added by this mod.
