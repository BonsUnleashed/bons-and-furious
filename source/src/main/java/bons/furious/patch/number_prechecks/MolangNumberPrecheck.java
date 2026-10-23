package bons.furious.patch.number_prechecks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bons and Furious switch molang_number_precheck (GeckoLib 4.8.4, tested build; client only; since 1.0.45).
 *
 * What GeckoLib does. MolangParser.parseJson turns every keyframe value of every animation file into a Molang value; for
 * a JSON string it first tries Double.parseDouble and, on NumberFormatException, parses the string as an expression
 * (parseExpression). Expressions such as "math.sin(query.anim_time*720)*10" or Blockbench's "linear" therefore each
 * create an exception with a filled stack trace (an explicit throw in the JDK parser; HotSpot's fast-throw does not apply)
 * on the resource-reload workers. GeckoLibCache parses every animation file of every namespace at each client reload.
 *
 * What the switch does. The Double.parseDouble call is wrapped (GeckoMolangPrecheckMixin). When FloatGrammar.mustThrow
 * says the string cannot be a Java double literal, a fresh QuietNumberFormatException (no stack trace) is thrown at the
 * same place, into the same catch block; every other string goes to Double.parseDouble as before. The catch block stores
 * the exception and never reads it (GeckoLib 4.8.4: astore_3, then parseExpression(string)), so the same expression is
 * parsed and the same MolangValue returned. JSON numbers never reach this call (they use getAsDouble).
 *
 * -Dbons_and_furious.molangNumberPrecheck=false switches it off at run time.
 * -Dbons_and_furious.molangNumberPrecheck.shadow=true (verification runs only): Double.parseDouble runs for every string as
 * in GeckoLib; SHADOW_CHECKS / SHADOW_SKIPPABLE / SHADOW_MISMATCHES as in EmfTokenPrecheck.
 */
public final class MolangNumberPrecheck {
    static final String KEY = "molang_number_precheck";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.molangNumberPrecheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.molangNumberPrecheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    private MolangNumberPrecheck() {
    }

    /** software.bernie.geckolib.core.molang.MolangParser.parseJson: its Double.parseDouble(string) call. */
    public static double parseDouble(String value, Operation<Double> original) {
        if (!enabled || value == null) return original.call(value);
        if (SHADOW) return shadow(value, original);
        if (FloatGrammar.mustThrow(value)) {
            if (!announced) announce();
            throw new QuietNumberFormatException(value);
        }
        return original.call(value);
    }

    private static double shadow(String value, Operation<Double> original) {
        if (!announced) announce();
        boolean quiet = FloatGrammar.mustThrow(value);
        SHADOW_CHECKS.incrementAndGet();
        double parsed;
        try {
            parsed = original.call(value);
        } catch (NumberFormatException e) {
            if (quiet) SHADOW_SKIPPABLE.incrementAndGet();
            throw e;
        }
        if (quiet) PrecheckLog.mismatch(KEY, SHADOW_MISMATCHES, "Double.parseDouble accepted \"" + value + "\", which the pre-check rejects");
        return parsed;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        PrecheckLog.applies(KEY, "GeckoLib keyframe expressions go to parseExpression without a NumberFormatException stack trace", SHADOW);
    }
}
