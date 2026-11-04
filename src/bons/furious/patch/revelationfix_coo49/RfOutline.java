package bons.furious.patch.revelationfix_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mega.endinglib.api.client.text.TextColorInterface;
import com.mega.revelationfix.client.enums.ModChatFormatting;
import it.unimi.dsi.fastutil.ints.Int2CharOpenHashMap;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group revelationfix_coo49 (RevelationFix 4.8 in Goety Revelation 2.3.5, EndingLib 2.2.2):
 * revelationfix_outline_precheck (exact; client). RevelationFix is all rights reserved, EndingLib MIT: no code carried.
 *
 * <p>RevelationFix's gr.FontMixin.drawOutLine wraps the drawInternal call of Font.drawInBatch(FormattedCharSequence...)
 * (every drawn line of text that is not Modern UI's): it builds EndingLib's TextColorUtils.getColorChars(sequence) - a new
 * Int2CharOpenHashMap filled by one pass over the sequence, a put(index, the colour's EndingLib chat code) for every
 * coloured character, through a capturing lambda and an Optional per character - and asks whether any value is the
 * APOLLYON or EDEN code; only then does it draw through its own font (with that map), otherwise the original drawInternal
 * runs and the map is dropped.
 *
 * <p>The switch makes the one pass itself with a small reusable sink (no lambda, no Optional) that keeps every coloured
 * character's (index, code) in order until one of the two codes turns up. None there: RevelationFix gets an empty map,
 * for which its own test answers "no" exactly as for the full map, and the original drawInternal runs. One there: a new
 * map made as getColorChars makes it gets the kept pairs and then every later coloured character, the same puts in the
 * same order, so RevelationFix gets a map equal to the one getColorChars builds. While RevelationFix has not created its
 * two codes yet (null), or if this thread is already inside the pass, the original call runs.
 *
 * <p>Runtime flag (=false: off): bons_and_furious.revelationfixOutlinePrecheck.
 * -Dbons_and_furious.revelationfixOutlinePrecheck.count=true counts both answers (offline proofs only). No shadow mode
 * (the two answers are compared on the real code over generated and vanilla-styled text offline).
 */
public final class RfOutline {
    static final String KEY = "revelationfix_outline_precheck";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.revelationfixOutlinePrecheck", "true"));
    static final boolean COUNT = Boolean.getBoolean("bons_and_furious.revelationfixOutlinePrecheck.count");
    /** The map handed over when no APOLLYON / EDEN character is present: RevelationFix only reads it (its "has" test). */
    static final Int2CharOpenHashMap EMPTY = new Int2CharOpenHashMap(0);
    /** Offline statistics (only with COUNT): sequences answered with the empty map, sequences given the replayed map. */
    public static long lean, full;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** The thread that first draws text through here (the render thread) keeps its pass in a constant, others in PASS. */
    private static final Thread OWNER = Thread.currentThread();
    private static final Pass OWN = new Pass();
    private static final ThreadLocal<Pass> PASS = ThreadLocal.withInitial(Pass::new);
    private static boolean announced;

    private RfOutline() {
    }

    /**
     * One pass over a sequence. Until one of the two codes turns up, every coloured character's (index, code) is kept in
     * order (packed in one long); at the first of them the map is made, the kept pairs are put into it in order, and from
     * then on each coloured character goes straight in: the same puts in the same order as getColorChars.
     */
    static final class Pass implements FormattedCharSink {
        char apollyon, eden;
        boolean busy;
        Int2CharOpenHashMap map;
        int n;
        long[] kept = new long[64];

        @Override
        public boolean m_6411_(int i, Style style, int codePoint) {
            TextColor color = style.m_131135_();
            if (color != null) {
                char c = ((TextColorInterface) (Object) color).endinglib$getCode();
                Int2CharOpenHashMap m = map;
                if (m != null) {
                    m.put(i, c);
                } else if (c == apollyon || c == eden) {
                    (map = replay()).put(i, c);
                } else {
                    if (n == kept.length) kept = Arrays.copyOf(kept, n * 2);
                    kept[n++] = (long) i << 16 | c;
                }
            }
            return true;
        }

        /** The map, with the kept pairs put in order (its own method: its loop compiles hot, not as a cold branch). */
        private Int2CharOpenHashMap replay() {
            Int2CharOpenHashMap m = new Int2CharOpenHashMap();
            long[] k = kept;
            for (int j = 0, e = n; j < e; j++) m.put((int) (k[j] >> 16), (char) k[j]);
            return m;
        }
    }

    /** The TextColorUtils.getColorChars call inside RevelationFix's drawOutLine handler. */
    public static Int2CharOpenHashMap colorChars(FormattedCharSequence sequence, Operation<Int2CharOpenHashMap> original) {
        ChatFormatting apollyon = ModChatFormatting.APOLLYON, eden = ModChatFormatting.EDEN;
        if (!enabled || sequence == null || apollyon == null || eden == null) return original.call(sequence);
        Pass p = Thread.currentThread() == OWNER ? OWN : PASS.get();
        if (p.busy) return original.call(sequence);
        p.busy = true;
        try {
            p.apollyon = apollyon.m_178510_();
            p.eden = eden.m_178510_();
            p.map = null;
            p.n = 0;
            sequence.m_13731_(p);
            Int2CharOpenHashMap map = p.map;
            if (map == null) {
                if (COUNT) lean++;
                if (!announced) announce();
                return EMPTY;
            }
            if (COUNT) full++;
            return map;
        } finally {
            p.map = null;
            p.busy = false;
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {}: RevelationFix's outline check no longer builds a colour map for text without its two colours", KEY);
    }
}
