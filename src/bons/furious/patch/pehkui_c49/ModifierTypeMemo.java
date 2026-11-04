package bons.furious.patch.pehkui_c49;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import virtuoel.pehkui.api.ScaleModifiers;

/**
 * Bons and Furious switch pehkui_typed_modifier_memo (Pehkui 3.8.2+1.20.1-forge, MIT; both sides). Ours; no Pehkui code is
 * carried.
 *
 * What Pehkui does. TypedScaleModifier.getType() calls its Supplier every time; Pehkui calls getType() whenever one of its
 * typed modifiers (base, motion, width, height and reach multipliers and divisors) works out a scale, so every
 * modifier-chain step pays an interface call through many different lambda classes.
 *
 * What the switch does. The first non-null answer is kept on the modifier and returned from then on, but only for the
 * suppliers Pehkui itself creates in ScaleModifiers: lambdas whose bodies read a static final ScaleTypes field (checked
 * by the switch's fingerprint of ScaleModifiers). Every other supplier (other mods' modifiers) is asked every time, as
 * before.
 *
 * Why the result is identical. Those lambdas return a static final field, so after ScaleTypes is initialised they return
 * the same object on every call; a null (asked while ScaleTypes is still initialising) is never kept.
 *
 * -Dbons_and_furious.pehkuiTypedModifierMemo=false switches it off at run time.
 * -Dbons_and_furious.pehkuiTypedModifierMemo.shadow=true: every kept answer is also compared with the supplier's answer
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class ModifierTypeMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.pehkuiTypedModifierMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.pehkuiTypedModifierMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Marks a modifier whose supplier is not Pehkui's own: never kept, asked every time. */
    public static final Object NEVER = new Object();
    private static volatile boolean announced;

    /** True for the lambda classes Pehkui's ScaleModifiers defines (its suppliers of static final ScaleTypes fields). */
    private static final ClassValue<Boolean> PEHKUI_OWN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.isHidden() && type.getNestHost() == ScaleModifiers.class;
            } catch (SecurityException e) {
                return false;
            }
        }
    };

    private ModifierTypeMemo() {}

    /** Whether the answer of this supplier may be kept. */
    public static boolean keepable(Object supplier) {
        boolean own = supplier != null && PEHKUI_OWN.get(supplier.getClass());
        if (own && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: pehkui_typed_modifier_memo applies (Pehkui's typed scale modifiers keep their scale type){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return own;
    }

    /** Shadow mode: the kept answer must be the supplier's answer. */
    public static void shadow(Object kept, Object fresh) {
        SHADOW_CHECKS.incrementAndGet();
        if (kept != fresh) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: pehkui_typed_modifier_memo shadow mismatch #{}: kept {} supplier {}", m, kept, fresh);
        }
    }
}
