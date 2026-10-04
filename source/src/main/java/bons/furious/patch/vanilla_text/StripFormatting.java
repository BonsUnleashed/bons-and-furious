package bons.furious.patch.vanilla_text;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_strip_formatting_fast_path (Minecraft 1.21.1 with NeoForge 21.1.252, both sides).
 * Mojang member names. No Minecraft code here.
 *
 * ChatFormatting.stripFormatting(s) returns null for null, else the result of replaceAll("") of the pattern
 * "(?i)§[0-9A-FK-OR]" over s. JEI calls it for every tooltip line of every ingredient while it builds its search index
 * at world join (StringUtil.removeChatFormatting, 2.6% of our test rig's 9.0 s filter window on 1.20.1), and most
 * lines carry no formatting code at all.
 *
 * Every match of that pattern starts with the character U+00A7: CASE_INSENSITIVE without UNICODE_CASE folds ASCII
 * letters only, so the non-ASCII U+00A7 matches nothing but itself. A string without U+00A7 therefore has no match, and
 * Matcher.replaceAll returns text.toString() when find() fails - for a String, the very same object. So for such a
 * string the result is the argument itself, which is what the fast path returns (String.indexOf, no Matcher, no
 * allocation). null and strings containing U+00A7 take the original method.
 *
 * Ported to 1.21.1: nothing changed - ChatFormatting.stripFormatting and STRIP_FORMATTING_PATTERN are the same source
 * text in 1.20.1 and 1.21.1, and both run on Java 21, whose Matcher.replaceAll(String) returns text.toString() when the
 * first find() fails (JDK 21.0.12 src.zip, java/util/regex/Matcher.java).
 */
public final class StripFormatting {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.vanillaStripFormattingFastPath=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaStripFormattingFastPath", "true"));
    /** Shadow mode for rigs: every fast-path answer is also computed by the original method and compared by identity. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaStripFormattingFastPath.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private StripFormatting() {
    }

    /** True when stripFormatting(s) is s itself: s is not null and holds no U+00A7. */
    public static boolean unchanged(String s) {
        if (s == null || s.indexOf('§') >= 0) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_strip_formatting_fast_path returns strings without formatting codes unchanged without a regex");
        }
        return true;
    }

    /** Shadow mode: the original's answer for a string the fast path answered with itself. */
    public static void shadow(String s, String original) {
        SHADOW_CHECKS.incrementAndGet();
        if (original != s) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: vanilla_strip_formatting_fast_path shadow mismatch for '{}': original returned '{}'", s, original);
        }
    }
}
