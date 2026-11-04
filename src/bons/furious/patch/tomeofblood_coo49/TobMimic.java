package bons.furious.patch.tomeofblood_coo49;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix tomeofblood_mimic_index_guard (Tome of Blood: Rebirth 0.4.5, LGPL-3.0; both sides). Ours; no Tome of
 * Blood code is carried. Idea: Collections Of Optimizations (tomeofblood.fixMimicAugmentIndex), idea text only.
 *
 * The bug. The Mimic augment copies the augment in front of the part being resolved: AugmentMimic.applyModifiers reads
 * spell.recipe.get(spellContext.getCurrentIndex() - 1) and, when that part is an AbstractAugment, applies its modifiers, then
 * its own (none). Nothing checks the index: when the part being resolved is the first one of the spell (index 0: a form
 * resolving its stats with Mimic behind it, or a context that has not advanced yet), the read is get(-1) and throws
 * IndexOutOfBoundsException out of spell resolution, which takes down the server tick or the client.
 *
 * The fix. The redirected get (TobMimicIndexMixin) answers null for an index outside [0, size): null is not an
 * AbstractAugment, so Mimic applies only its own modifiers, exactly what it does for a part in front that is not an augment.
 * Every in-range read makes the same get call, so every spell where Mimic has a real part in front behaves as before; the
 * in-range path pays one bounds test (no operation object, no argument array). A null list makes the call (which throws as
 * before). One INFO line the first time the fix acts.
 *
 * Runtime flag: -Dbons_and_furious.tomeofbloodMimicIndexGuard=false.
 */
public final class TobMimic {
    public static final String KEY = "tomeofblood_mimic_index_guard";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tomeofbloodMimicIndexGuard", "true"));
    /** Statistics for probes (plain field): out-of-range reads answered null. */
    public static long guarded;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private TobMimic() {
    }

    /** spell.recipe.get(index) in AugmentMimic.applyModifiers, redirected: in range (and a null list) the same get call. */
    public static Object part(List<?> recipe, int index) {
        if (recipe == null || (index >= 0 && index < recipe.size()) || !enabled) return recipe.get(index);
        guarded++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (the Mimic augment found no part in front of the resolving part, index {}; it applies only its own modifiers instead of throwing)", KEY, index);
        }
        return null;
    }
}
