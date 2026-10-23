package bons.furious.patch.number_prechecks;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import snownee.textanimator.effect.Effect;
import snownee.textanimator.effect.EffectFactory;
import snownee.textanimator.effect.params.EmptyParams;
import snownee.textanimator.effect.params.Params;
import snownee.textanimator.effect.params.TypedParams;

/**
 * Bons and Furious switch textanimator_parse_prechecks (Text Animator 2.5.4, tested build; both sides; since 1.0.45).
 *
 * What Text Animator does. Its StringDecomposer mixin sends every string drawn as text through
 * CommonProxy.iterateFormatted, every frame. For every "<...>" in the string it calls Effect.create(split, false) inside a
 * try / catch (Exception) that discards the exception. Two kinds of tags throw there with a filled stack trace:
 *  (b) a tag whose first word is not a registered effect type: every chat line "<Player> hello", every "<3", every
 *      "<typewriter>" outside a typewriter context. create parses the parameters (no side effects) and then
 *      EffectFactory.create throws IllegalArgumentException("Unknown effect type: ...") (or create throws "Typewriter
 *      effect is not allowed here" first);
 *  (a) a registered tag with a parameter value that is not a number, e.g. a colour "c=#ff8800": create calls
 *      Double.parseDouble on it, catches the NumberFormatException and keeps the string.
 *
 * What the switch does, at the Effect.create call in iterateFormatted (TextAnimatorIterateMixin):
 *  (b) when the type is not in EffectFactory.listTypes() (the factory map create looks the type up in) or is "typewriter"
 *      where it is not allowed, a fresh QuietIllegalArgumentException is thrown into the same catch block instead of
 *      calling create, which would have thrown too after side-effect-free work. The catch block stores the exception and
 *      never reads it (Text Animator 2.5.4: astore 21, the slot is dead afterwards), so newEffects stays null and the '<'
 *      is drawn as text, exactly as before;
 *  (a) when the type is registered and some parameter value fails FloatGrammar.mustThrow (Double.parseDouble would throw),
 *      createQuietly builds the effect: Text Animator 2.5.4's Effect.create logic (Apache-2.0, carried with its order of
 *      checks, splits, puts and exceptions) where such a value goes straight to the string put that create's own
 *      NumberFormatException handler makes. Every other call goes to Effect.create unchanged.
 * Effect.create itself cannot be hooked: it is a static interface method and Mixin 0.8.5 has no injectors there.
 *
 * -Dbons_and_furious.textanimatorParsePrechecks=false switches it off at run time.
 * -Dbons_and_furious.textanimatorParsePrechecks.shadow=true (verification runs only): Effect.create runs for every tag as
 * in Text Animator; SHADOW_CHECKS counts tags, SHADOW_SKIPPABLE the exceptions the switch would leave out,
 * SHADOW_MISMATCHES a tag the switch would skip that create accepted, or a createQuietly result that differs from
 * create's (WARN, at most 20).
 */
public final class TextAnimatorParsePrecheck {
    static final String KEY = "textanimator_parse_prechecks";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.textanimatorParsePrechecks", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.textanimatorParsePrechecks.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    private TextAnimatorParsePrecheck() {
    }

    /** CommonProxy.iterateFormatted: its Effect.create(split, false) call. */
    public static Effect create(String[] split, boolean allowTypewriter, Operation<Effect> original) {
        if (!enabled || split == null || split.length == 0) return original.call(split, allowTypewriter);
        if (SHADOW) return shadow(split, allowTypewriter, original);
        String type = split[0];
        boolean typewriter = !allowTypewriter && "typewriter".equals(type);
        if (typewriter || !EffectFactory.listTypes().contains(type)) {
            if (!announced) announce();
            throw new QuietIllegalArgumentException(type, typewriter);
        }
        if (split.length > 1 && hasQuietValue(split)) {
            if (!announced) announce();
            return createQuietly(split, allowTypewriter);
        }
        return original.call(split, allowTypewriter);
    }

    /**
     * True when some parameter of the tag looks like key=value with a value that is not "true"/"false" and that
     * Double.parseDouble would reject. It only chooses between two exact paths (createQuietly or Effect.create itself),
     * so it scans without allocating and needs no exact copy of StringUtils.split: the value is taken as what follows
     * the first run of '=' after the key, which is StringUtils.split(param, "=", 2)[1] for every key=value parameter.
     */
    static boolean hasQuietValue(String[] split) {
        for (int i = 1; i < split.length; i++) {
            String p = split[i];
            if (p == null) continue;
            int n = p.length(), k = 0;
            while (k < n && p.charAt(k) == '=') k++;          // separators before the key
            int key = k;
            while (k < n && p.charAt(k) != '=') k++;          // the key
            if (k == key || k == n) continue;                 // no key or no separator: a flag
            while (k < n && p.charAt(k) == '=') k++;          // the separator run
            if (k == n) continue;                             // "key=": a flag
            int len = n - k;
            if (len == 4 && p.startsWith("true", k) || len == 5 && p.startsWith("false", k)) continue;
            if (FloatGrammar.mustThrow(p, k, n)) return true;
        }
        return false;
    }

    /**
     * Text Animator 2.5.4's Effect.create(String[], boolean) (Apache-2.0, Snownee), with one change: a parameter value
     * that FloatGrammar.mustThrow rejects is put as the string directly, which is what create's NumberFormatException
     * handler does after Double.parseDouble threw. Same checks, splits, puts, boxing and exceptions in the same order.
     */
    static Effect createQuietly(String[] split, boolean allowTypewriter) {
        if (split.length == 0) throw new IllegalArgumentException("Empty input");
        if (!allowTypewriter && "typewriter".equals(split[0])) throw new IllegalArgumentException("Typewriter effect is not allowed here");
        Params params = EmptyParams.INSTANCE;
        if (split.length > 1) {
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builderWithExpectedSize(split.length - 1);
            for (int i = 1; i < split.length; i++) {
                String[] kv = StringUtils.split(split[i], "=", 2);
                if (kv.length == 1) {
                    builder.put(kv[0], Boolean.valueOf(true));
                } else if (!"true".equals(kv[1]) && !"false".equals(kv[1])) {
                    if (FloatGrammar.mustThrow(kv[1])) {
                        builder.put(kv[0], kv[1]);
                    } else {
                        try {
                            builder.put(kv[0], Double.valueOf(Double.parseDouble(kv[1])));
                        } catch (NumberFormatException e) {
                            builder.put(kv[0], kv[1]);
                        }
                    }
                } else {
                    builder.put(kv[0], Boolean.valueOf(Boolean.parseBoolean(kv[1])));
                }
            }
            params = new TypedParams(builder.build());
        }
        return EffectFactory.create(split[0], params);
    }

    private static Effect shadow(String[] split, boolean allowTypewriter, Operation<Effect> original) {
        if (!announced) announce();
        SHADOW_CHECKS.incrementAndGet();
        String type = split[0];
        boolean skip = !allowTypewriter && "typewriter".equals(type) || !EffectFactory.listTypes().contains(type);
        boolean quietValues = !skip && split.length > 1 && hasQuietValue(split);
        Effect stock;
        try {
            stock = original.call(split, allowTypewriter);
        } catch (RuntimeException e) {
            if (skip || quietValues) SHADOW_SKIPPABLE.incrementAndGet();
            if (quietValues) {
                try {
                    Effect ours = createQuietly(split, allowTypewriter);
                    PrecheckLog.mismatch(KEY, SHADOW_MISMATCHES, "Effect.create threw " + e + " for " + Arrays.toString(split) + " but the pre-checked copy built " + describe(ours));
                } catch (RuntimeException expected) {
                    // both threw: the caller's catch discards either exception
                }
            }
            throw e;
        }
        if (skip) {
            PrecheckLog.mismatch(KEY, SHADOW_MISMATCHES, "Effect.create accepted " + Arrays.toString(split) + " although the switch would skip it");
        } else if (quietValues) {
            SHADOW_SKIPPABLE.incrementAndGet();
            String ours;
            try {
                ours = describe(createQuietly(split, allowTypewriter));
            } catch (RuntimeException e) {
                ours = "an exception " + e;
            }
            if (!ours.equals(describe(stock)))
                PrecheckLog.mismatch(KEY, SHADOW_MISMATCHES, "for " + Arrays.toString(split) + " Effect.create built " + describe(stock) + ", the pre-checked copy " + ours);
        }
        return stock;
    }

    private static String describe(Effect e) {
        return e == null ? "null" : e.getClass().getName() + "|" + e.getName() + "|" + e.serialize();
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        PrecheckLog.applies(KEY, "text tags Text Animator would reject, and non-numeric tag parameters, no longer throw exceptions with stack traces", SHADOW);
    }
}
