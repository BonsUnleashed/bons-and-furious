package bons.furious.patch.relics;

import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch relics_foreign_capability_fast_path (Relics 1.20.1-0.8.0.13, both sides). No Relics code here.
 *
 * Relics attaches IRelicsCapability.RelicsCapabilityProvider to every player. Forge's capability dispatcher asks that
 * provider about EVERY capability anyone queries on a player (TConstruct's hotbar overlay asks for its own on each HUD
 * overlay, Curios for its inventory, ...), and the provider answered each question with
 * CapabilityRegistry.DATA.orEmpty(cap, LazyOptional.of(() -> backend)): it built a lambda, a LazyOptional, its lock
 * object and its listener HashSet (about 136 bytes) before Forge's orEmpty threw them away for any other capability and
 * returned the shared LazyOptional.empty(). The mixin returns that same empty singleton directly for any capability that is
 * not DATA; DATA queries run Relics' original code. This class only holds the runtime switch and announces itself once.
 */
public final class RelicsForeignCapability {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.relicsForeignCapability=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.relicsForeignCapability", "true"));

    static {
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: relics_foreign_capability_fast_path: Relics' player capability answers other "
                + "mods' capability queries without building a LazyOptional{}", enabled ? "" : " (runtime switch off)");
    }

    private RelicsForeignCapability() {
    }
}
