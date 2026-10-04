package bons.furious.patch.artifacts;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch artifacts_living_tick_order (Artifacts 9.5.16, both sides). No Artifacts code here; the
 * reordered handlers are in ArtifactEventsForgeTickOrderMixin.
 *
 * Artifacts' living-tick listener runs two checks for every living entity every tick, on both sides:
 *  - kitty slippers: KITTY_SLIPPERS_ENABLED && KITTY_SLIPPERS.isEquippedBy(entity.getLastHurtByMob()) && the entity is a
 *    creeper. While an entity's last attacker is set (100 ticks after any hit by a living attacker) the middle test scans
 *    the attacker's whole Curios inventory every tick, although only creepers can pass the last test.
 *  - umbrella: isInWater = entity.isInWater() && !CharmOfSinkingItem.shouldSink(entity) is computed first, and the charm
 *    test is a Curios capability lookup plus inventory scan, for every entity in water every tick, although isInWater only
 *    matters when the entity is also airborne, falling, without slow falling and holding an umbrella upright.
 * The mixin evaluates the same tests in a cheaper order: the creeper test first, and the water/charm test last.
 *
 * Why the result is identical: each condition is a conjunction of side-effect-free tests (a game-rule value, a registry
 * supplier, entity fields, an entity-type tag, held items, active effects, Curios lookups that only read), so the order
 * of its operands does not change its value, and the branch taken, the attribute modifier added or removed and the fall
 * distance reset are the same. The else branch of the umbrella check does not read isInWater.
 *
 * -Dbons_and_furious.artifactsLivingTickOrder.shadow=true (verification runs only) also evaluates both conditions in the
 * original order and counts disagreements in SHADOW_CHECKS / SHADOW_MISMATCHES; the reordered result is the one used.
 */
public final class ArtifactsTickOrder {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.artifactsLivingTickOrder=false evaluates in Artifacts' order. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.artifactsLivingTickOrder", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.artifactsLivingTickOrder.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    static {
        LOGGER.info("Bons and Furious: artifacts_living_tick_order applies (Artifacts' living tick tests the creeper type before the kitty slippers scan "
                + "and the charm of sinking last){}", SHADOW ? " - shadow verification on" : enabled ? "" : " - runtime switch off");
    }

    private ArtifactsTickOrder() {
    }

    /** Shadow bookkeeping: the reordered condition against the original-order one (verification runs only). */
    public static boolean shadow(String which, boolean reordered, boolean original) {
        SHADOW_CHECKS.incrementAndGet();
        if (reordered != original && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: artifacts_living_tick_order shadow mismatch in the {} check (reordered {}, original {})", which, reordered, original);
        return reordered;
    }
}
