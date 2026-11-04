package bons.furious.patch.enigmaticlegacy_coo49;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches enigmaticlegacy_cursed_ring_anger_candidates (kind change, off by default) and
 * enigmaticlegacy_cursed_ring_anger_candidates_exact (exact, on) for Enigmatic Legacy 2.30.1, server side. Ours; no
 * Enigmatic Legacy code is carried. Idea: Collections Of Optimizations (enigmaticlegacy.narrowCursedRingAngerScan), idea
 * text only.
 *
 * CursedRing.curioTick runs every server tick for every player wearing the Ring of the Seven Curses. For every living
 * entity in the neutral anger range it first asks player.getVisibilityPercent(entity) (vanilla checks plus a Forge
 * LivingVisibilityEvent posted on the Forge bus, a new event object each time) and compares the distance with the
 * resulting anger distance, and only then tests whether the entity is a Piglin or a NeutralMob, the only kinds the loop
 * acts on; for every other entity each path of the loop body ends in "continue". So for those entities the visibility
 * value is never used; only the call's own side effects (event listeners, mixins merged into getVisibilityPercent) can
 * make a difference.
 *
 * candidates (change): for an entity that is neither a Piglin nor a NeutralMob the call is not made (the loop gets 1.0 and
 * ends in "continue" as before). Trade-off: LivingVisibilityEvent listeners and code merged into getVisibilityPercent no
 * longer see these (player, entity) pairs from the ring.
 * exact: the same, but only while that skip is provably invisible: the Forge listener census says the
 * LivingVisibilityEvent reaches no listener (phase markers only), getVisibilityPercent and
 * ForgeHooks.getEntityVisibilityMultiplier are the plain Minecraft / Forge bodies (ElPlugin's look at the merged classes,
 * no mixin of another mod with priority 999999 or more on either class), and the player's class does not override
 * getVisibilityPercent. Then the skipped call only computes a number nobody reads. Flags:
 * -Dbons_and_furious.enigmaticlegacyCursedRingAngerCandidates=false,
 * -Dbons_and_furious.enigmaticlegacyCursedRingAngerCandidatesExact=false.
 */
public final class CursedRingScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean candidatesEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.enigmaticlegacyCursedRingAngerCandidates", "true"));
    public static volatile boolean exactEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.enigmaticlegacyCursedRingAngerCandidatesExact", "true"));
    /** What the loop gets instead of the visibility of a skipped entity (any value: that loop pass ends in "continue"). */
    public static final double SKIPPED = 1.0;
    /** Calls left out by each switch. */
    public static final AtomicLong CANDIDATE_SKIPS = new AtomicLong();
    public static final AtomicLong EXACT_SKIPS = new AtomicLong();
    private static volatile boolean announcedCandidates, announcedExact;
    /** "" once verified, the reason otherwise (null = not decided yet). */
    private static volatile String exactVerdict;

    private CursedRingScan() {
    }

    /** The census watch of LivingVisibilityEvent, made on first use. */
    private static final class Watch {
        static final Object W = ListenerCensusLink.watch(LivingEvent.LivingVisibilityEvent.class, null);
    }

    private static final ClassValue<Boolean> PLAIN_CLASS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("m_20968_", Entity.class).getDeclaringClass() == LivingEntity.class;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        }
    };

    /** True for the entities the loop acts on (a Piglin or a NeutralMob). */
    static boolean candidate(Entity e) {
        return e instanceof Piglin || e instanceof NeutralMob;
    }

    /** enigmaticlegacy_cursed_ring_anger_candidates: leave out the visibility call for {@code checked}. */
    public static boolean skipCandidates(Entity checked) {
        if (!candidatesEnabled || checked == null || candidate(checked)) return false;
        CANDIDATE_SKIPS.incrementAndGet();
        if (!announcedCandidates) {
            announcedCandidates = true;
            LOGGER.info("Bons and Furious: enigmaticlegacy_cursed_ring_anger_candidates applies (the Ring of the Seven Curses asks visibility only for piglins and neutral mobs)");
        }
        return true;
    }

    /** enigmaticlegacy_cursed_ring_anger_candidates_exact: leave out the call only when nothing could observe it. */
    public static boolean skipExact(Object player, Entity checked) {
        if (!exactEnabled || checked == null || player == null || candidate(checked)) return false;
        if (!verified()) return false;
        if (!ListenerCensusLink.onlyPhaseMarkers(Watch.W)) return false;
        if (!PLAIN_CLASS.get(player.getClass())) return false;
        EXACT_SKIPS.incrementAndGet();
        if (!announcedExact) {
            announcedExact = true;
            LOGGER.info("Bons and Furious: enigmaticlegacy_cursed_ring_anger_candidates_exact applies (no visibility listener or hook: the ring skips the visibility of entities it never acts on)");
        }
        return true;
    }

    private static boolean verified() {
        String v = exactVerdict;
        if (v == null) v = verify();
        return v.isEmpty();
    }

    private static synchronized String verify() {
        if (exactVerdict != null) return exactVerdict;
        String why = ElPlugin.livingVerdict;
        if (why == null) why = "the look at LivingEntity did not run";
        if (why.isEmpty()) why = ElPlugin.hooksVerdict == null ? "the look at ForgeHooks did not run" : ElPlugin.hooksVerdict;
        if (why.isEmpty()) why = later("net/minecraft/world/entity/LivingEntity", ElPlugin.livingPriority);
        if (why.isEmpty()) why = later("net/minecraftforge/common/ForgeHooks", ElPlugin.hooksPriority);
        if (!why.isEmpty()) LOGGER.info("Bons and Furious: enigmaticlegacy_cursed_ring_anger_candidates_exact stands aside: {}", why);
        exactVerdict = why;
        return why;
    }

    /**
     * "" when no mixin of another mod with priority at or above the look's was applied to {@code internal} (Bons and
     * Furious' own mixins are left out: none of them hooks getVisibilityPercent or getEntityVisibilityMultiplier, checked
     * by the group's offline proof over the whole source tree).
     */
    public static String later(String internal, int ours) {
        try {
            for (org.spongepowered.asm.mixin.extensibility.IMixinInfo mi : org.spongepowered.asm.mixin.transformer.ClassInfo
                    .forName(internal).getAppliedMixins()) {
                if (mi.getClassName().startsWith("bons.furious.mixin.")) continue;
                if (mi.getPriority() >= ours) return "the mixin " + mi.getClassName() + " (priority " + mi.getPriority() + ") was applied to " + internal + " after the look";
            }
            return "";
        } catch (Throwable t) {
            return "the mixins applied to " + internal + " cannot be listed (" + t + ")";
        }
    }

    /** For the offline proof: forget the decision. */
    public static void resetForProof() {
        exactVerdict = null;
    }
}
