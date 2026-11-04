package bons.furious.patch.immersiveoptimization_c49;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch immersiveoptimization_be_priority_front_cache (Immersive Optimization 0.2.0, GPL-3.0; client
 * side, where block entities are scheduled: ServerLevel overrides the hooked method without calling it). Ours; no Immersive
 * Optimization code is carried (call-site wrappers in front of its own calls).
 *
 * What Immersive Optimization does. With its block-entity option on, its Level mixin asks TickScheduler.shouldTick(level,
 * chunk) for every ticking block entity every tick: a ConcurrentHashMap lookup of the dimension's LevelData (by the
 * dimension's ResourceLocation), then blockEntityPriorities.computeIfAbsent(boxed chunk long, ...) in another
 * ConcurrentHashMap, whose Long keys hash to x ^ z so neighbouring chunks collide. Its worker thread clears the priority map
 * whenever it samples a game time divisible by 207, and LevelData objects are created by the worker and dropped by reset().
 *
 * What the switch does. Two front caches on the scheduler, used only by the thread that first asks (the client thread):
 * the last Level with the LevelData its own getLevelData returned, and the chunk priorities its own computeIfAbsent
 * returned for that LevelData (a primitive long -> int map). A generation counter, raised before and after every clear of
 * either map and around every LevelData the worker creates, invalidates both; a priority is kept only when the counter did
 * not move during the lookup that produced it. Every miss asks Immersive Optimization's own code exactly as before.
 *
 * Why the result is identical. A priority, once computeIfAbsent stored it, does not change until the map is cleared (the
 * only other writer); getLevelData's answer for a Level changes only when the level map is cleared or gains an entry;
 * every one of those raises the counter first, so a cached answer is always the one the shared maps hold at that moment
 * (a lookup racing with the worker's clear could see either side of it in the original too). The other checks of
 * shouldTick (config, forced chunks, game time) run unchanged on every call.
 *
 * -Dbons_and_furious.immersiveoptimizationBePriorityFrontCache=false switches it off at run time.
 * -Dbons_and_furious.immersiveoptimizationBePriorityFrontCache.shadow=true: every cached answer is compared with the shared
 * maps' current one (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class IoFrontCache {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersiveoptimizationBePriorityFrontCache", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.immersiveoptimizationBePriorityFrontCache.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private IoFrontCache() {}

    public static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: immersiveoptimization_be_priority_front_cache applies (Immersive Optimization's block entity schedule answers repeat questions from a front cache){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    public static void shadow(Object cached, Object shared, String what) {
        SHADOW_CHECKS.incrementAndGet();
        if (!java.util.Objects.equals(cached, shared)) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: immersiveoptimization_be_priority_front_cache shadow mismatch #{} ({}): cached {} vs shared {}", m, what, cached, shared);
        }
    }
}
