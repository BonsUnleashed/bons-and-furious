package bons.furious.patch.tag_ids;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_tag_membership_ids (Minecraft 1.20.1 on Forge 47.4.16; both sides). No Minecraft code
 * here. Idea: Leaf "Cache block state tags" (idea text only); this is our own design at the holder level.
 *
 * Every tag test of a block state, item, entity type, fluid, biome, ... ends in Holder.Reference.is(TagKey) (m_203656_),
 * which asks the holder's tag set: Set.copyOf(...) from bindTags, i.e. one of the JDK's immutable sets. Its contains()
 * hashes the TagKey record (registry and location) and compares it with the record's generated equals against the
 * colliding entries; that equals is reached through a megamorphic call in the JDK's shared probe loop (SetN.probe) and an
 * ObjectMethods method handle, and touches each compared key, its location and both strings. In the 1.0.26 recording
 * (cli10_jfr1) Holder.Reference.is(TagKey) was 2.5-2.9% of the integrated server thread, 90% of it in TagKey.equals.
 *
 * With the switch each tag key carries a number for its content (TagIdCarrier, assigned once per key instance from a map
 * keyed by the TagKey itself, so equal keys share a number and different keys never do), and each holder keeps the numbers
 * of its current tag set, sorted, with a 64-bit summary of them ({@link IdSet}). The holder's answer is "is the asked key's
 * number among the set's numbers" - the same answer as contains() because numbers are equal exactly when the keys are equal.
 * The record remembers which set object it was built from and is rebuilt whenever the holder's set is another object
 * (bindTags replaces it on every tag load or reload: there is no stale state). Sets that are not the JDK's immutable sets
 * (whose contents never change), a null key, or an element that is not a TagKey keep the original contains() call.
 *
 * -Dbons_and_furious.tagMembershipIds=false runs contains() on every call.
 * -Dbons_and_furious.tagMembershipIds.shadow=true (verification runs only) also runs contains() for every answer from the
 * numbers and compares (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged).
 */
public final class TagIds {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tagMembershipIds", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.tagMembershipIds.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** The JDK's immutable set classes (Set.of / Set.copyOf): their membership never changes. */
    private static final Class<?> SET_0 = Set.of().getClass(), SET_1 = Set.of(1).getClass(), SET_N = Set.of(1, 2, 3).getClass();
    /** Content of a tag key (the key itself: TagKey.equals/hashCode) -> its number; numbers start at 1. */
    private static final ConcurrentHashMap<Object, Integer> IDS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT = new AtomicInteger();
    private static volatile boolean announced;

    private TagIds() {
    }

    /** The numbers of one holder's tag set (immutable; built from {@link #source} only). */
    public static final class IdSet {
        public final Set<?> source;
        final int[] ids;
        final long summary;

        IdSet(Set<?> source, int[] ids) {
            this.source = source;
            this.ids = ids;
            long s = 0L;
            for (int id : ids) s |= 1L << id;
            this.summary = s;
        }

        /** True when {@code id} is one of the set's numbers. */
        public boolean has(int id) {
            if ((this.summary & (1L << id)) == 0L) return false;
            int[] a = this.ids;
            if (a.length <= 8) {
                for (int x : a) if (x == id) return true;
                return false;
            }
            return Arrays.binarySearch(a, id) >= 0;
        }
    }

    /** The number of a tag key; assigned on first use of the instance. */
    public static int idOf(TagIdCarrier key) {
        int id = key.bons$tagId();
        if (id != 0) return id;
        id = IDS.computeIfAbsent(key, k -> NEXT.incrementAndGet());
        key.bons$tagId(id);
        return id;
    }

    /**
     * The numbers of {@code tags}, or null when membership must stay with contains(): a set that is not one of the JDK's
     * immutable sets, or an element that is not a tag key.
     */
    public static IdSet build(Set<?> tags) {
        Class<?> c = tags.getClass();
        if (c != SET_N && c != SET_1 && c != SET_0) return null;
        int[] ids = new int[tags.size()];
        int n = 0;
        for (Object e : tags) {
            if (!(e instanceof TagIdCarrier carrier) || n == ids.length) return null;
            ids[n++] = idOf(carrier);
        }
        if (n != ids.length) return null;
        Arrays.sort(ids);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_tag_membership_ids applies (tag tests compare tag numbers; each holder's numbers follow its current tag set){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return new IdSet(tags, ids);
    }

    /** Shadow mode: the set's own answer next to the numbers' answer. */
    public static boolean shadow(Set<?> tags, Object key, boolean fromIds) {
        boolean original = tags.contains(key);
        SHADOW_CHECKS.incrementAndGet();
        if (original != fromIds) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_tag_membership_ids shadow mismatch #{}: {} in {}: numbers say {}, the set says {}",
                    m, key, tags, fromIds, original);
        }
        return original;
    }
}
