package bons.furious.patch.subtleeffects_c49;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch subtleeffects_display_tick_state_cache (Subtle Effects 1.15.1 for NeoForge 1.21.1, client), a
 * trade-off that is off by default. Subtle Effects is All Rights Reserved: no Subtle Effects code is carried. Ours, idea
 * from Collections Of Optimizations (subtleeffects.displayTickPlan), idea text only: the part of that idea that is not exact.
 *
 * What Subtle Effects does: for every non-air ambient sample (half of the 1,334 per client tick) its ClientLevelMixin
 * handler walks ModBlockTickers.REGISTERED_SPECIAL with a new lambda, testing every special-ticker predicate on the
 * state (instanceof and property tests, and three that read Subtle Effects' configured block lists), and calls
 * ChestBlockEntityTicker.trySpawn, which reads two settings and asks the level for the block entity at the position.
 *
 * What the switch does: (a) whether any special-ticker predicate holds for a block state is worked out once per state and
 * kept (at most 65,536 states) until ModBlockTickers.init runs again (Subtle Effects runs it at start-up and whenever its
 * block or environment settings are saved), and the walk is left out for states where none holds; (b) the chest check is
 * left out for block states that have no block entity.
 * Trade-off (off by default): a change to what a special ticker reacts to that does not come through Subtle Effects'
 * settings (another mod changing its block lists while the game runs) reaches block kinds already seen only after the
 * next save of those settings; and the chest check no longer asks the level about positions whose block has no block
 * entity (a leftover block entity there is not looked at by this check).
 *
 * Flag: -Dbons_and_furious.subtleeffectsDisplayTickStateCache=false restores both at runtime.
 */
public final class DisplayTickStateCache {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.subtleeffectsDisplayTickStateCache", "true"));
    private static final int LIMIT = 1 << 16;
    /** The kept answers (1 = no predicate holds, 2 = one does); used by the owner thread only (the client thread). */
    private static final Reference2ByteOpenHashMap<BlockState> SPECIAL = new Reference2ByteOpenHashMap<>();
    private static Thread owner;
    private static BlockState current;
    private static volatile int generation;
    private static int seenGeneration;
    private static volatile boolean announced;
    /** Plain counters for probes and the offline proof. */
    public static long walksSkipped, chestsSkipped;

    private DisplayTickStateCache() {
    }

    /** ModBlockTickers.init: the special-ticker map is cleared and filled again, so every kept answer is dropped. */
    public static void invalidate() {
        generation++;
    }

    /** The handler's air test: the state it is about to work on (its own local, passed through unchanged). */
    public static void state(BlockState state) {
        current = state;
    }

    /** True when the handler's special-ticker walk over `map` can be left out: no predicate holds for the current state. */
    public static boolean noSpecial(Map<?, ?> map) {
        BlockState state = current;
        current = null;
        if (!enabled || state == null) return false;
        Thread t = Thread.currentThread();
        if (owner == null) owner = t;
        if (owner != t) return false;
        int gen = generation;
        if (gen != seenGeneration) {
            SPECIAL.clear();
            seenGeneration = gen;
        }
        byte known = SPECIAL.getByte(state);
        if (known == 0) {
            known = 1;
            for (Object p : map.keySet()) {
                @SuppressWarnings("unchecked") Predicate<BlockState> test = (Predicate<BlockState>) p;
                if (test.test(state)) {
                    known = 2;
                    break;
                }
            }
            if (SPECIAL.size() < LIMIT) SPECIAL.put(state, known);
        }
        if (known == 1) {
            walksSkipped++;
            if (!announced) announce();
            return true;
        }
        return false;
    }

    /** True when the chest check can be left out: the block state has no block entity. */
    public static boolean noChest(BlockState state) {
        if (!enabled || state.hasBlockEntity()) return false;
        chestsSkipped++;
        return true;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: subtleeffects_display_tick_state_cache applies (Subtle Effects' special block effects are planned per block state; chest checks skip blocks without block entities)");
    }
}
