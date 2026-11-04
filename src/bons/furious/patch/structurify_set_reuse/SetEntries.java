package bons.furious.patch.structurify_set_reuse;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch structurify_set_entries_reuse (Structurify 2.0.34, CC BY-NC-ND 4.0; server side incl. the
 * integrated server). No Structurify code here: our injections only decide whether Structurify's own handler needs to run.
 *
 * What Structurify does. Its StructureSetMixin wraps StructureSet.structures() (f_210003_, the record's entry list) with
 * the handler structurify$getStructures: every call copies the record's list into a new ArrayList and compares the copy
 * with the copy kept from the last rebuild, then returns its filtered list (structurify$structures; rebuilt when it is
 * null - a config change sets it to null through structurify$setStructureSetId - or when the comparison fails). World
 * generation asks every structure set for its entries for every chunk.
 *
 * What the switch does. After Structurify's handler has answered a set, the answer is kept together with the record's list
 * it was computed for. A later call returns the kept answer at the head of the handler (MixinSquared) while the record
 * still holds that same list object, the list is of an immutable class (Guava ImmutableList, or the JDK's List.of /
 * copyOf lists: its content cannot have changed), and Structurify's own field still holds that same answer (not
 * reset). In exactly that state Structurify's handler would compare equal contents and return that field, with no other
 * effect; every other call runs the handler. The pair is published as one immutable holder, so threads see it whole.
 *
 * -Dbons_and_furious.structurifySetEntriesReuse=false switches it off at run time.
 * -Dbons_and_furious.structurifySetEntriesReuse.shadow=true (verification runs): the handler always runs and its answer
 * is compared with the kept one (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines).
 */
public final class SetEntries {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.structurifySetEntriesReuse", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.structurifySetEntriesReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private SetEntries() {
    }

    /** What Structurify's handler answered for a set, and for which record list. */
    public record Seen(List<?> original, List<?> answer, boolean immutable) {
    }

    public static Seen seen(List<?> original, List<?> answer) {
        return new Seen(original, answer, immutable(original));
    }

    static boolean immutable(List<?> list) {
        return list instanceof ImmutableList || (list != null && list.getClass().getName().startsWith("java.util.ImmutableCollections$"));
    }

    /** The kept answer when it is the one Structurify's handler would return now, else null (the handler runs). */
    public static List<?> reuse(Seen seen, List<?> original, List<?> structurifyAnswer) {
        if (!enabled || seen == null || !seen.immutable || seen.original != original || seen.answer != structurifyAnswer || structurifyAnswer == null) return null;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: structurify_set_entries_reuse applies (structure set entries reused without copying and comparing the list){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            EXPECTED.set(seen.answer);   // compared with the handler's answer at its return
            return null;
        }
        return seen.answer;
    }

    private static final ThreadLocal<List<?>> EXPECTED = new ThreadLocal<>();

    /** Shadow mode, at the handler's return: the answer it gave against the one the switch would have reused. */
    public static void shadow(List<?> answer) {
        if (!SHADOW) return;
        List<?> expected = EXPECTED.get();
        if (expected == null) return;
        EXPECTED.remove();
        SHADOW_CHECKS.incrementAndGet();
        if (expected != answer) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: structurify_set_entries_reuse shadow mismatch #{}: the handler gave another list than the kept one", m);
        }
    }
}
