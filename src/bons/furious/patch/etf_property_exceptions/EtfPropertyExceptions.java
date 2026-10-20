package bons.furious.patch.etf_property_exceptions;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch etf_stackless_property_exception (Entity Texture Features 7.1 with Entity Model Features
 * 3.2.4, tested builds; client only; since 1.0.45).
 *
 * What ETF does. For every rule number of every random-texture / CEM rule file, RandomProperties
 * .getAllRegisteredRandomPropertiesOfIndex asks every registered property factory (56 of ETF, 6 of EMF in this pack)
 * whether the rule uses its property. Each factory constructs its property; the constructor calls
 * RandomProperty.readPropertiesOrThrow, which throws RandomProperty$RandomPropertyException (a checked Exception,
 * explicitly constructed, so its stack trace is always filled) when the key is absent, and the factory's
 * getPropertyOrNull catches it and returns null. So almost every factory throws for almost every rule: ~8,200 stack
 * traces per full parse of this pack's 136 rules, made on the render thread the first time each entity texture with
 * rules is used after a resource reload.
 *
 * What the switch does. RandomPropertyExceptionMixin gives RandomPropertyException a fillInStackTrace() that returns the
 * exception without walking the stack while the switch is on. The message, the class, the cause and every catch block
 * stay as they are. Why nothing can tell: tools/mixedproof/EtfPropertyExceptionsAudit.java followed every
 * RandomPropertyException ETF 7.1 and EMF 3.2.4 can create (12 construction methods), through every method it can escape
 * (76) to every handler that can receive it (66): 65 discard the value, 1 rethrows it into those, none reads it, no
 * lambda or reflective call carries it; no other jar of the pack mentions the class. The guard fingerprints all 142 of
 * those methods and the exception class's method set, so a changed handler, a new throw site in a guarded method or a
 * fillInStackTrace override of ETF's own turns the switch off.
 *
 * Tripwire: the mixin also overrides getStackTrace() and both printStackTrace(...) to call stackRead() first (then the
 * stock method). Nothing calls them in the tested builds; if anything ever does while the switch is on, one WARN names it.
 *
 * -Dbons_and_furious.etfStacklessPropertyException=false switches it off at run time (checked at each construction).
 * -Dbons_and_furious.etfStacklessPropertyException.shadow=true (verification runs only): stack traces are filled as in
 * ETF; SHADOW_CHECKS / SHADOW_SKIPPABLE count the exceptions whose stack walk the switch would leave out, and
 * SHADOW_MISMATCHES counts reads of such a stack (each would have seen an empty one; WARN, at most 20).
 */
public final class EtfPropertyExceptions {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "etf_stackless_property_exception";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.etfStacklessPropertyException", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.etfStacklessPropertyException.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced, warned;

    private EtfPropertyExceptions() {
    }

    /** RandomPropertyException.fillInStackTrace: true = walk the stack as stock does, false = leave it empty. */
    public static boolean fillStack() {
        if (!enabled) return true;
        if (!announced) announce();
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            SHADOW_SKIPPABLE.incrementAndGet();
            return true;
        }
        return false;
    }

    /** RandomPropertyException.getStackTrace / printStackTrace: never called in the tested builds (see the audit). */
    public static void stackRead(Throwable t) {
        if (SHADOW) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: something read the stack trace of {} (it would be empty with the switch on)", KEY, m, t.toString());
        } else if (enabled && !warned) {
            warned = true;
            LOGGER.warn("Bons and Furious: {}: something read the stack trace of an ETF RandomPropertyException ({}), which this switch leaves empty; "
                    + "set {}=false in bons_and_furious.properties and please report it", KEY, t.toString(), KEY);
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (ETF property rule parsing no longer fills a stack trace for every absent property){}",
                KEY, SHADOW ? " - shadow verification on" : "");
    }
}
