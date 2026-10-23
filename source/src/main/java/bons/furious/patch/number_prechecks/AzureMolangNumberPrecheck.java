package bons.furious.patch.number_prechecks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bons and Furious switch azurelib_molang_number_precheck (AzureLib 3.0.13, tested build; client only; since 1.0.45).
 *
 * AzureLib carries the same MolangParser.parseJson as GeckoLib (byte-for-byte the same logic: Double.parseDouble in a
 * try, parseExpression in the NumberFormatException handler, which stores the exception in slot 3 and never reads it),
 * and its two animation caches (AzureLibCache and AzBakedAnimationCache) each parse every animation file of every
 * namespace at each client reload: two of the three Molang parse passes of this pack. The switch is the GeckoLib one
 * (MolangNumberPrecheck) on AzureLib's class; it is a separate key because the two libraries install independently and a
 * guard covers one mod (a key whose guards span two mods is off whenever one of them is missing).
 *
 * -Dbons_and_furious.azurelibMolangNumberPrecheck=false switches it off at run time.
 * -Dbons_and_furious.azurelibMolangNumberPrecheck.shadow=true (verification runs only): as MolangNumberPrecheck.
 */
public final class AzureMolangNumberPrecheck {
    static final String KEY = "azurelib_molang_number_precheck";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.azurelibMolangNumberPrecheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.azurelibMolangNumberPrecheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    private AzureMolangNumberPrecheck() {
    }

    /** mod.azure.azurelib.core.molang.MolangParser.parseJson: its Double.parseDouble(string) call. */
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
        PrecheckLog.applies(KEY, "AzureLib keyframe expressions go to parseExpression without a NumberFormatException stack trace", SHADOW);
    }
}
