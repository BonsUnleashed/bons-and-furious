package bons.furious.patch.vanilla_glyph_fonts;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious 1.0.49 switch vanilla_glyph_font_set_memo (Minecraft 1.20.1 on Forge 47.4.28, client). SRG member names.
 * Idea: Collections Of Optimizations (vanilla.memoGlyphFontSet), idea text only.
 *
 * What vanilla does. Font$StringRenderOutput.accept (m_6411_) is called once per glyph of every string drawn and first asks
 * Font.getFontSet(style.getFont()) (m_92863_): the font manager's lookup function (rename map, then font-set map) and, in
 * this pack, Supplementaries' and Biomancy's HEAD injections on getFontSet, each with a CallbackInfoReturnable.
 *
 * What the switch does. Each StringRenderOutput (one per string draw: Font's draw methods create it and drop it when the
 * draw returns) remembers the last font id (by identity) and the FontSet getFontSet returned for it, and hands that FontSet
 * back while the next glyph asks for the same id object; another id goes through getFontSet and is remembered instead.
 * Why the result is identical: within one string draw getFontSet is a pure function of the id. The font manager's maps
 * change only on a resource reload, which never runs inside a draw; Biomancy's injection depends on the id and a flag set
 * from a player tick; Supplementaries' antique-ink flag is set on the Font around whole block-entity renders and book or
 * map screens (BlockEntityRendererDispatcherMixin, BookViewScreenMixin, CartographyTableScreenMixin, PageButtonMixin),
 * never between two glyphs of one string. So every glyph gets the same FontSet object as before, and the other work of
 * accept is untouched (Jade's accept and constructor injections included).
 *
 * ImmediatelyFast's fast_text_lookup (on in this pack) already makes exactly this memo with a @Redirect of the same call
 * (MixinTextRenderer_Drawer.reduceGetFontStorageCalls); MixinExtras applies our @WrapOperation around its redirect, so both
 * would run. When ImmediatelyFast's mixin is in the class (its field immediatelyFast$lastFontStorage exists) this switch
 * stands aside at run time: every call goes straight to ImmediatelyFast's memo, with one INFO line.
 *
 * -Dbons_and_furious.vanillaGlyphFontSetMemo=false switches it off at run time.
 * -Dbons_and_furious.vanillaGlyphFontSetMemo.shadow=true (verification): every remembered answer is compared with a fresh
 * getFontSet call (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20) and the fresh answer is used.
 */
public final class GlyphFontSets {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaGlyphFontSetMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaGlyphFontSetMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private GlyphFontSets() {
    }

    /** True when the memo is used: the switch is on and ImmediatelyFast's own memo is not in the class. */
    public static boolean memo() {
        if (!enabled) return false;
        if (ImmediatelyFastMemo.PRESENT) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_glyph_font_set_memo stands aside: ImmediatelyFast's fast_text_lookup already remembers the font set per string");
            }
            return false;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_glyph_font_set_memo applies (each drawn string looks its font set up once per font){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return true;
    }

    /** Shadow mode: a remembered FontSet against the fresh answer (identity). */
    public static void verify(Object remembered, Object fresh, Object id) {
        SHADOW_CHECKS.incrementAndGet();
        if (remembered != fresh) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_glyph_font_set_memo shadow mismatch #{}: font {} gave another font set within one string", m, id);
        }
    }

    /** Whether ImmediatelyFast's fast_text_lookup mixin is in Font$StringRenderOutput, read once the class exists. */
    static final class ImmediatelyFastMemo {
        static final boolean PRESENT = detect();

        private ImmediatelyFastMemo() {
        }

        private static boolean detect() {
            try {
                Class<?> c = Class.forName("net.minecraft.client.gui.Font$StringRenderOutput", false, GlyphFontSets.class.getClassLoader());
                for (Field f : c.getDeclaredFields()) if (f.getName().equals("immediatelyFast$lastFontStorage")) return true;
            } catch (Throwable ignored) {
                // no such class here (offline tools): no ImmediatelyFast memo either
            }
            return false;
        }
    }
}
