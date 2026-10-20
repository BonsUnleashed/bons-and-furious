package bons.furious.patch.ldlib_metadata;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib.client.model.custommodel.LDLMetadataSection;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch ldlib_metadata_no_throw (LDLib 1.0.52.a, tested build; client only; since 1.0.45).
 *
 * What LDLib does. Its SpriteResourceLoader mixin asks LDLMetadataSection.getMetadata(ResourceLocation) for every sprite of
 * every atlas at each resource reload (the answer is cached until the next reload). For a sprite file that exists,
 * getMetadata runs ret = resource.metadata().getSection(Serializer.INSTANCE).get() inside try / catch (Exception), with
 * ret already holding MISSING. A texture without an "ldlib" section in its .mcmeta (nearly all of them) gives an empty
 * Optional, whose get() throws an explicit NoSuchElementException with a full stack trace; the catch block ignores it and
 * ret stays MISSING. A pack the size of ours would do this ~50,000 times per reload (51,846 block/item textures).
 *
 * What the switch does. That second Optional.get call is wrapped (LDLMetadataSectionMixin): for an empty Optional it
 * returns LDLMetadataSection.MISSING, which is then stored into ret (the value ret already held), instead of throwing; a
 * present Optional (and a null one, whose get() throws NullPointerException into the same catch) goes to Optional.get as
 * before. IOException from metadata() and parse errors from getSection still reach the same catch block. The handler
 * never reads the exception (javap: astore 4, then the cache put), so the same value is cached and returned.
 *
 * -Dbons_and_furious.ldlibMetadataNoThrow=false switches it off at run time.
 * -Dbons_and_furious.ldlibMetadataNoThrow.shadow=true (verification runs only): Optional.get runs as in LDLib;
 * SHADOW_CHECKS counts the calls, SHADOW_SKIPPABLE the exceptions the switch leaves out, SHADOW_MISMATCHES an empty
 * Optional whose get() did not throw (cannot happen; WARN, at most 20).
 */
public final class LdlibMetadata {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "ldlib_metadata_no_throw";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ldlibMetadataNoThrow", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.ldlibMetadataNoThrow.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    private LdlibMetadata() {
    }

    /** LDLMetadataSection.getMetadata(ResourceLocation): its second Optional.get() call (the metadata section). */
    public static Object section(Optional<?> section, Operation<Object> original) {
        if (!enabled || section == null || section.isPresent()) return original.call(section);
        if (!announced) announce();
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            Object value;
            try {
                value = original.call(section);
            } catch (java.util.NoSuchElementException e) {
                SHADOW_SKIPPABLE.incrementAndGet();
                throw e;
            }
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: an empty Optional returned {}", KEY, m, value);
            return value;
        }
        return LDLMetadataSection.MISSING;   // the value ret already holds when the stock get() throws into the catch block
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (sprites without LDLib metadata no longer throw NoSuchElementException at each reload){}",
                KEY, SHADOW ? " - shadow verification on" : "");
    }
}
