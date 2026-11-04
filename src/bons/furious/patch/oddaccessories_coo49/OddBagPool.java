package bons.furious.patch.oddaccessories_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IdMappingEvent;
import net.oddaccessories.configuration.ConfigConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch oddaccessories_bag_reward_pool_memo (Odd Accessories 1.1.6, All Rights Reserved; server side, the
 * method runs wherever a Suspicious Bag is opened or a chest loot roll asks for a reward). Ours; no Odd Accessories code is
 * carried. Idea: Collections Of Optimizations (oddaccessories.memoBagRewardPool), idea text only.
 *
 * SuspiciousBagItem.rollReward streams every entry of the Forge item registry (tens of thousands in a large pack) through
 * four filters (Odd Accessories' namespace, its static exclusion set, the BAGBLACKLIST config list and
 * ConfigConfiguration.isDisabled) into a new list, then draws from that list with the caller's RandomSource. The list is a
 * function of: the registry's entries in their iteration order, the blacklist's contents, and isDisabled, which itself
 * answers from a set Odd Accessories keeps for the identity of the DISABLEDCURIOS list (rebuilt only when that list object
 * changes) and false while the config is not loaded.
 *
 * pool() keeps the last list together with what it was built from: the registry generation (a counter bumped by every
 * IdMappingEvent, which Forge posts after each rebuild of its registries: freeze, revert on leaving a world, snapshot
 * injection on joining one) and the registry size, whether the config is loaded, the identity of the DISABLEDCURIOS list
 * (read only while loaded, as isDisabled does), and the identity and contents of the blacklist the stream captured. When
 * all of them are unchanged the kept list is returned without running the stream; it holds the same items in the same order,
 * so the draws that follow pick the same items. The list never leaves rollReward (only isEmpty, size and get are called on
 * it) and is unmodifiable, like Stream.toList's. Otherwise the stream runs as shipped and its result is kept.
 *
 * Flag: -Dbons_and_furious.oddaccessoriesBagRewardPoolMemo=false; shadow: -Dbons_and_furious.oddaccessoriesBagRewardPoolMemo.shadow=true
 * (every kept answer is checked against the stream, which then supplies the list; at most 20 mismatches are logged).
 */
public final class OddBagPool {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oddaccessoriesBagRewardPoolMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.oddaccessoriesBagRewardPoolMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** Lists built by the stream / answers taken from the kept list (harness and log counters). */
    public static final AtomicLong BUILT = new AtomicLong();
    public static final AtomicLong KEPT = new AtomicLong();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final Object LOCK = new Object();
    private static volatile int listening;   // 0 = not yet, 1 = listening, -1 = could not register (stock code runs)
    private static volatile Kept kept;
    private static volatile boolean announced;

    private OddBagPool() {
    }

    private record Kept(int generation, int registrySize, boolean loaded, Object disabledIds, List<?> blacklist, Object[] blacklistCopy, List<?> pool) {
    }

    /** The IdMappingEvent listener body (also reachable from a harness that posts the event itself). */
    public static void registriesRebuilt() {
        GENERATION.incrementAndGet();
    }

    private static boolean listening() {
        int l = listening;
        if (l != 0) return l > 0;
        synchronized (LOCK) {
            if (listening == 0) {
                try {
                    MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, IdMappingEvent.class, e -> registriesRebuilt());
                    listening = 1;
                } catch (Throwable t) {
                    listening = -1;
                    LOGGER.warn("Bons and Furious: oddaccessories_bag_reward_pool_memo cannot listen for registry rebuilds ({}); Odd Accessories' bag pool is built as shipped", t.toString());
                }
            }
            return listening > 0;
        }
    }

    /** The wrapped Stream.toList() of rollReward; {@code blacklist} is the list the stream's filter captured. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List pool(Stream stream, List blacklist, Operation<List> original) {
        if (!enabled || blacklist == null || !listening()) return original.call(stream);
        int generation = GENERATION.get();                  // read before every input: a rebuild while we look retires the answer
        int size = ForgeRegistries.ITEMS.getEntries().size();
        boolean loaded = ConfigConfiguration.SPEC.isLoaded();
        Object disabled = loaded ? ConfigConfiguration.DISABLEDCURIOS.get() : null;
        Kept k = kept;
        if (k != null && k.generation == generation && k.registrySize == size && k.loaded == loaded && k.disabledIds == disabled
                && k.blacklist == blacklist && sameContents(k.blacklistCopy, blacklist)) {
            if (SHADOW) return shadow(stream, original, k.pool);
            KEPT.incrementAndGet();
            if (!announced) announce();
            return k.pool;
        }
        List fresh = original.call(stream);
        BUILT.incrementAndGet();
        kept = new Kept(generation, size, loaded, disabled, blacklist, blacklist.toArray(), fresh);
        return fresh;
    }

    private static boolean sameContents(Object[] copy, List<?> now) {
        if (copy.length != now.size()) return false;
        for (int i = 0; i < copy.length; i++) {
            Object a = copy[i], b = now.get(i);
            if (a != b && (a == null || !a.equals(b))) return false;
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List shadow(Stream stream, Operation<List> original, List<?> ours) {
        List fresh = original.call(stream);
        SHADOW_CHECKS.incrementAndGet();
        boolean same = fresh.size() == ours.size();
        for (int i = 0; same && i < ours.size(); i++) same = fresh.get(i) == ours.get(i);
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: oddaccessories_bag_reward_pool_memo shadow mismatch: kept {} items, the stream built {}", ours.size(), fresh.size());
        }
        return fresh;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: oddaccessories_bag_reward_pool_memo applies (Odd Accessories' bag reward pool is kept until the item registry or its config changes)");
    }
}
