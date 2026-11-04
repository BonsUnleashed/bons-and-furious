package bons.furious.patch.vanilla_lookup_loops;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_rl_string_memo (Minecraft 1.20.1 on Forge 47.4.28, both sides): runtime switch and shadow
 * check of ResourceLocationStringMixin. No Mojang code here.
 *
 * What vanilla does. ResourceLocation.toString builds "namespace:path" as a new String on every call. Texture, sound and
 * model lookups, tooltips, registry dumps and many mods turn the same ids into text again and again (per frame on the
 * client), each time concatenating and later hashing a new string.
 *
 * What the switch does. The first toString of a ResourceLocation keeps the string it built in a field of that
 * ResourceLocation; later calls return that string. A ResourceLocation never changes (both parts are final), so the text
 * is always the same. Exact variant: repeated calls return the same String object (equal text) instead of a new one each
 * time. The field fits in the object's alignment padding, so a ResourceLocation does not get bigger (12-byte header + two
 * references = 20 bytes, padded to 24 either way, with compressed references).
 *
 * -Dbons_and_furious.rlStringMemo=false switches it off at run time.
 * -Dbons_and_furious.rlStringMemo.shadow=true (verification runs): the string is built on every call as before and
 * compared with the kept one (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines).
 */
public final class RlStrings {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.rlStringMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.rlStringMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private RlStrings() {
    }

    /** The first string built for a ResourceLocation, about to be kept. */
    public static String first(String text) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_rl_string_memo applies (each resource id builds its text once){}", SHADOW ? " - shadow verification on" : "");
        }
        return text;
    }

    /** Shadow mode: the freshly built string against the kept one. */
    public static void shadow(String kept, String fresh) {
        SHADOW_CHECKS.incrementAndGet();
        if (!kept.equals(fresh)) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_rl_string_memo shadow mismatch #{}: kept '{}', built '{}'", m, kept, fresh);
        }
    }
}
