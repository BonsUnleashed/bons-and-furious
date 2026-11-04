package bons.furious.patch.crittersandcompanions_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch crittersandcompanions_behaviour_fast_path (Critters and Companions 2.7.1; both sides).
 *
 * Critters and Companions 2.7.1 gives every entity a Behaviours holder (a map from behaviour class to behaviour); its
 * critters' models and goals ask the(type) for one behaviour, which goes through optional(type) (an Optional of the map
 * entry, mapped through the identity function) and orElseThrow: two Optionals per lookup. For a plain Behaviours holder (a
 * subclass, which could change optional(), keeps the original method) the switch returns the map entry directly when there
 * is one; an absent type still goes through the original and throws its own NoSuchElementException from the same place.
 * Same answers, same exceptions.
 *
 * Measured and left alone (offline, against the jar's own class in the same JVM): the walk of an empty holder (forEach),
 * which costs the original about 1-4 ns, came out 2.4x faster in one run and 0.52x with 24 bytes per call in another (a
 * HEAD injection's callback object is not always removed by the JIT); optional(type) gained nothing and its absent case
 * got slower. Runtime flag -Dbons_and_furious.crittersandcompanionsBehaviourFastPath=false. No Critters and Companions
 * code is carried (2.7.1 All Rights Reserved).
 */
public final class BehaviourFastPath {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.crittersandcompanionsBehaviourFastPath", "true"));

    private BehaviourFastPath() {
    }

    /** Called once from the transformed Behaviours class's static initializer (the hot path carries no log check). */
    public static void loaded() {
        if (enabled) LOGGER.info("Bons and Furious: crittersandcompanions_behaviour_fast_path: behaviour lookups answer straight from the map");
    }
}
