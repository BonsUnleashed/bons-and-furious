package bons.furious.patch.number_prechecks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bons and Furious switch emf_token_number_precheck (Entity Model Features 3.2.4, tested build; client only; since 1.0.45).
 *
 * What EMF does. MathExpressionParser.readVariableOrConstant hands every token of every animation expression to
 * Float.parseFloat and treats the NumberFormatException as "this token is a variable": its catch block runs the OptiFine
 * syntax check and then MathVariable.getOptimizedVariable. So every variable token (head.rx, limb_swing, var.x, if, ...)
 * creates an exception and fills its stack trace on the render thread during a model load; the JDK parser throws
 * explicitly, so HotSpot's fast-throw never applies. This pack: 59,524 per model load pass, 46,108 of them from Fresh
 * Animations (sweep 4, work/R/emf_scan.py).
 *
 * What the switch does. The Float.parseFloat call is wrapped (EmfTokenPrecheckMixin). When FloatGrammar.mustThrow says the
 * token cannot be a Java float literal, a fresh QuietNumberFormatException (no stack trace) is thrown at the same place,
 * into the same catch block; every other token goes to Float.parseFloat as before. The catch block stores the exception
 * and never reads it (EMF 3.2.4: astore_3, the slot is dead afterwards), so it runs exactly as before: same regex, same
 * config read, same variable lookup, same components, same order of getNegativeNext calls. null never reaches the
 * pre-check (EMF's tokens come from a StringBuilder; Float.parseFloat would throw NullPointerException, left to it).
 *
 * -Dbons_and_furious.emfTokenNumberPrecheck=false switches it off at run time (checked on every call).
 * -Dbons_and_furious.emfTokenNumberPrecheck.shadow=true (verification runs only): Float.parseFloat runs for every token as
 * in EMF; SHADOW_CHECKS counts tokens, SHADOW_SKIPPABLE the exceptions the switch would leave out, SHADOW_MISMATCHES a
 * token the pre-check rejects but Float.parseFloat accepted (cannot happen; WARN, at most 20).
 */
public final class EmfTokenPrecheck {
    static final String KEY = "emf_token_number_precheck";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfTokenNumberPrecheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.emfTokenNumberPrecheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    private EmfTokenPrecheck() {
    }

    /** MathExpressionParser.readVariableOrConstant: its Float.parseFloat(token) call. */
    public static float parseFloat(String token, Operation<Float> original) {
        if (!enabled || token == null) return original.call(token);
        if (SHADOW) return shadow(token, original);
        if (FloatGrammar.mustThrow(token)) {
            if (!announced) announce();
            throw new QuietNumberFormatException(token);
        }
        return original.call(token);
    }

    private static float shadow(String token, Operation<Float> original) {
        if (!announced) announce();
        boolean quiet = FloatGrammar.mustThrow(token);
        SHADOW_CHECKS.incrementAndGet();
        float value;
        try {
            value = original.call(token);
        } catch (NumberFormatException e) {
            if (quiet) SHADOW_SKIPPABLE.incrementAndGet();
            throw e;
        }
        if (quiet) PrecheckLog.mismatch(KEY, SHADOW_MISMATCHES, "Float.parseFloat accepted \"" + token + "\", which the pre-check rejects");
        return value;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        PrecheckLog.applies(KEY, "animation tokens that cannot be numbers go to EMF's variable branch without a NumberFormatException stack trace", SHADOW);
    }
}
