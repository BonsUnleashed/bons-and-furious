package bons.furious.patch.storagedrawers;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch storagedrawers_count_label_memo (Storage Drawers 12.15.1, MIT; client).
 *
 * With the Quantify Key, every drawer face within the render distance draws its item count every frame, and
 * CountFormatter.formatApprox turns any count from 10,000 up into text with String.format ("%.1fK", "%.0fM", ...): a new
 * Formatter, a format-string parse and float formatting per slot per frame (~0.5-1 us), for numbers that rarely change.
 *
 * Each of those String.format calls goes through format(): on the render thread a table keyed by the format string (the
 * same literal object every call), the raw bits of the float argument and the default FORMAT locale - every input
 * String.format's output depends on - answers with the string that call produced before; a miss, any other thread, or an
 * argument that is not a single Float runs String.format itself. The table is open-addressed (4,096 slots, linear probing)
 * and is wiped when it holds 2,048 texts, so a room of up to 2,048 labels is all hits and nothing grows without bound.
 * Strings are immutable and Storage Drawers only draws and measures the text. Counts below 10,000 are String.valueOf in
 * Storage Drawers and are not touched.
 *
 * -Dbons_and_furious.storageDrawersCountLabels=false formats every time; -Dbons_and_furious.storageDrawersCountLabels.shadow=true
 * (verification runs only) formats on every hit too and counts texts that differ (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class CountLabels {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.storageDrawersCountLabels", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.storageDrawersCountLabels.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final int CAPACITY = 4096, MASK = CAPACITY - 1, LIMIT = CAPACITY / 2;
    private static final String[] FORMATS = new String[CAPACITY];
    private static final int[] BITS = new int[CAPACITY];
    private static final Locale[] LOCALES = new Locale[CAPACITY];
    private static final String[] TEXTS = new String[CAPACITY];
    private static int size;
    private static volatile boolean announced;

    private CountLabels() {
    }

    private static int slot(String format, int bits) {
        int h = format.hashCode() * 0x9E3779B9 ^ bits * 0x85EBCA6B;
        return (h ^ h >>> 15) & MASK;
    }

    /** String.format(format, args) as CountFormatter.formatApprox calls it. */
    public static String format(String format, Object[] args) {
        if (!enabled || format == null || args == null || args.length != 1 || !(args[0] instanceof Float f) || !RenderSystem.isOnRenderThread()) {
            return String.format(format, args);
        }
        int bits = Float.floatToRawIntBits(f);
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        int i = slot(format, bits);
        for (String held; (held = FORMATS[i]) != null; i = i + 1 & MASK) {
            if (held == format && BITS[i] == bits && LOCALES[i] == locale) {
                String text = TEXTS[i];
                if (SHADOW) {
                    String fresh = String.format(format, args);
                    SHADOW_CHECKS.incrementAndGet();
                    if (!fresh.equals(text) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                        LOGGER.warn("Bons and Furious: storagedrawers_count_label_memo shadow mismatch: {} vs {}", text, fresh);
                    }
                }
                return text;
            }
        }
        String text = String.format(format, args);
        if (size >= LIMIT) {
            Arrays.fill(FORMATS, null);
            Arrays.fill(LOCALES, null);
            Arrays.fill(TEXTS, null);
            size = 0;
            i = slot(format, bits);
        }
        FORMATS[i] = format;
        BITS[i] = bits;
        LOCALES[i] = locale;
        TEXTS[i] = text;
        size++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: storagedrawers_count_label_memo applies (drawer count labels are formatted once per count){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return text;
    }
}
