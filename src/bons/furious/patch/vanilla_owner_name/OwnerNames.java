package bons.furious.patch.vanilla_owner_name;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_owner_name_empty_skip (Minecraft 1.20.1 on Forge 47.4.16; logical server, i.e. the
 * dedicated server and the integrated server; since 1.0.45). SRG member names.
 *
 * What vanilla does. TamableAnimal.readAdditionalSaveData and AbstractHorse.readAdditionalSaveData (m_7378_), and 27 mod
 * classes with their own copy, read the owner of every loaded animal: without a UUID "Owner" tag they call
 * OldUsersConverter.convertMobOwnerIfNecessary (m_11083_) with getString("Owner"), which is "" for every wild animal.
 * For an empty (or null, or longer than 16 characters) name m_11083_ goes straight to
 * try { return UUID.fromString(name); } catch (IllegalArgumentException e) { return null; }, and UUID.fromString("")
 * always throws an explicit IllegalArgumentException("Invalid UUID string: ") (JDK 21 and JDK 25: fromString1 finds no
 * dashes), so every wild-animal load fills a stack trace that is thrown away. In this pack Untamed Wilds' ComplexMob
 * extends TamableAnimal: 203,239 of 268,529 saved overworld entities (75.7 %, census 2026-10-13).
 *
 * What the switch does. The UUID.fromString call in m_11083_ is wrapped (OldUsersConverterOwnerNameMixin): for the empty
 * string it returns null, which is what m_11083_ returns after catching that exception; every other name (null included:
 * UUID.fromString(null) throws NullPointerException, which m_11083_ does not catch) goes to UUID.fromString as before.
 * The handler stores the exception and never reads it (javap: astore_2; aconst_null; areturn). Same return value for
 * every caller and every input; the profile cache and the server are never touched on this branch either way.
 *
 * -Dbons_and_furious.vanillaOwnerNameEmptySkip=false switches it off at run time.
 * -Dbons_and_furious.vanillaOwnerNameEmptySkip.shadow=true (verification runs only): UUID.fromString runs as in vanilla;
 * SHADOW_CHECKS counts the empty owner names, SHADOW_SKIPPABLE the exceptions the switch leaves out, SHADOW_MISMATCHES an
 * empty name UUID.fromString accepted (cannot happen; WARN, at most 20).
 */
public final class OwnerNames {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "vanilla_owner_name_empty_skip";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaOwnerNameEmptySkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaOwnerNameEmptySkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    private OwnerNames() {
    }

    /** OldUsersConverter.m_11083_: its UUID.fromString(name) call (reached for null, empty and names over 16 characters). */
    public static UUID fromString(String name, Operation<UUID> original) {
        if (!enabled || name == null || !name.isEmpty()) return original.call(name);
        if (!announced) announce();
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            UUID parsed;
            try {
                parsed = original.call(name);
            } catch (IllegalArgumentException e) {
                SHADOW_SKIPPABLE.incrementAndGet();
                throw e;
            }
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: UUID.fromString(\"\") returned {}", KEY, m, parsed);
            return parsed;
        }
        return null;   // what m_11083_ returns after UUID.fromString("") throws IllegalArgumentException
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (animals saved without an owner no longer create an IllegalArgumentException on load){}",
                KEY, SHADOW ? " - shadow verification on" : "");
    }
}
