package bons.furious.patch.endinglibrary_coo49;

import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch endinglibrary_cap_fallback_lazy (EndingLibrary 2.2.2 and EndingLibraryTweaked 2.2.1fix, both
 * sides). EndingLibrary is MIT; the wrapped getters' one-line bodies are repeated on the fast path (MIT allows it), nothing
 * else of EndingLibrary is carried. Ours, idea from Collections Of Optimizations (endinglibrary.leanCameraCapLookup), idea
 * text only.
 *
 * What EndingLibrary does: CommonProxy.getCameraCap / getCameraCapOptional / getLivingCapOptional / getEntityCapOptional /
 * getTextCapOptional (called by about thirty of its mixins: camera, key mappings, GUI, renderers, player and server ticks,
 * many times per frame and per tick) answer entity.getCapability(X_CAP.orElse(ELCapabilityManager.getCapability(
 * NAME.toString()))): the fallback argument is built on every call (a new String from the capability's ResourceLocation
 * and a hash-map lookup with it, which throws NullPointerException while NAME is not registered) although X_CAP, a static
 * LazyOptional whose supplier makes that same lookup, holds the capability once it has resolved, and orElse then never
 * uses the fallback.
 *
 * What the switch does (a MixinExtras @WrapMethod on four getters, a cancellable HEAD inject on getCameraCap, whose
 * return type is EndingLibrary's own class): {@link #present} answers the capability only when the
 * static field still holds the LazyOptional CommonProxy's static initialiser made (captured at its end) and that optional
 * resolves (orElse with a private marker, exactly the resolution the getter's own orElse makes) to a capability; the
 * getter's body then runs with it and without the fallback (entity.getCapability(capability), plus
 * orElseThrow(NullPointerException::new) for getCameraCap). Why that is the same: the optional resolved through its own
 * supplier, so NAME was registered, and EndingLibrary never removes a registration (its only writes to the registration
 * map are puts; the four static fields are written only by the static initialiser: both checked on the bytecode of both
 * builds), so the skipped fallback would have found it and orElse would have ignored it. Anything else (a replaced or
 * null field, an invalidated optional, a supplier answer of null, the optional not resolvable yet) runs the getter as
 * shipped; before registration both throw ELCapabilityManager's NullPointerException ("... is not registered").
 *
 * Flag: -Dbons_and_furious.endinglibraryCapFallbackLazy=false runs every getter as shipped.
 */
public final class CapFallbacks {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.endinglibraryCapFallbackLazy", "true"));
    private static final Object NONE = new Object();
    /** PLAYER_CAP, LIVING_CAP, ENTITY_CAP, TEXT_CAP as CommonProxy's static initialiser left them. */
    private static final LazyOptional<?>[] ORIGINAL = new LazyOptional<?>[4];
    /** Getter calls answered without the fallback (a plain counter for probes and the harness). */
    public static long skipped;
    private static volatile boolean announced;

    private CapFallbacks() {
    }

    /** From the end of CommonProxy's static initialiser. */
    public static void capture(LazyOptional<?> player, LazyOptional<?> living, LazyOptional<?> entity, LazyOptional<?> text) {
        ORIGINAL[0] = player;
        ORIGINAL[1] = living;
        ORIGINAL[2] = entity;
        ORIGINAL[3] = text;
    }

    /** The capability the static optional of getter family {@code index} holds (resolving it as orElse does), or null: run
     *  the getter as shipped. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object present(LazyOptional lo, int index) {
        if (!enabled || lo == null || lo != ORIGINAL[index]) return null;
        Object v = lo.orElse(NONE);
        if (v == NONE) return null;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: endinglibrary_cap_fallback_lazy applies (EndingLibrary's capability getters skip the unused fallback lookup)");
        }
        return v;
    }
}
