package bons.furious.patch.vanilla_outline_memos;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Style;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_outline_style_memo (Minecraft 1.20.1, client). Helper of FontOutlineStyleMixin; no
 * Minecraft code is carried.
 *
 * Outlined text (glowing sign text, and the outlined labels of Jade, Create, gravestones and other mods) is drawn by
 * Font.drawInBatch8xOutline: eight passes over the text, and for every glyph of every pass the pass lambda recolours the
 * glyph's style with style.withColor(outlineColour), which in 1.20.1 always builds a new TextColor and a new Style. The
 * renderer (StringRenderOutput.accept, 458 bytes of bytecode, too large for the JIT to inline into the lambda) only reads
 * that style, so both objects are garbage: 80 bytes per glyph and pass.
 *
 * What changes: the last (input style, colour) pair and its recoloured style are kept; a glyph whose style is that same
 * Style object, recoloured with the same colour, gets the kept style. Anything else calls withColor as before and becomes
 * the kept pair. Why identical: Style is immutable and withColor(int) is a pure function of the style's fields and the
 * colour, so the kept style has exactly the fields a new call would give (equal TextColor value, the same bold / italic /
 * underline / strikethrough / obfuscated / click / hover / insertion / font references); the renderer reads only those
 * fields, never the object's identity. Exact variant: one recoloured Style per input style and colour instead of one per
 * glyph and pass. Only exact Style objects are kept (a subclass, if a mod made one, always calls withColor).
 * Runtime flag: -Dbons_and_furious.outlineStyleMemo=false. SHADOW MODE for rigs:
 * -Dbons_and_furious.outlineStyleMemo.shadow=true calls withColor for every glyph, returns its result and compares it field
 * by field with the kept style (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class OutlineStyle {
    /** Runtime switch (the config switch acts when Font is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.outlineStyleMemo", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.outlineStyleMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    /** The kept pair: immutable, replaced as a whole, so a thread always sees a consistent one. */
    private record Kept(Style in, int colour, Style out) {
    }

    private static volatile Kept kept;

    private OutlineStyle() {
    }

    /** In place of style.withColor(colour) in drawInBatch8xOutline's pass lambda. */
    public static Style recolour(Style style, int colour, Operation<Style> original) {
        if (!enabled || style == null || style.getClass() != Style.class) return original.call(style, colour);
        Kept k = kept;
        if (k != null && k.in == style && k.colour == colour) return SHADOW ? shadow(k, original) : k.out;
        Style out = original.call(style, colour);
        kept = new Kept(style, colour, out);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_outline_style_memo: outlined text reuses the recoloured style of each glyph style{}",
                    SHADOW ? " (SHADOW MODE: withColor runs for every glyph and is compared)" : "");
        }
        return out;
    }

    private static Style shadow(Kept k, Operation<Style> original) {
        Style fresh = original.call(k.in, k.colour);
        SHADOW_CHECKS.incrementAndGet();
        if (!same(fresh, k.out) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: vanilla_outline_style_memo SHADOW MISMATCH {}: withColor gave {}, kept {}", SHADOW_MISMATCHES.get(), fresh, k.out);
        }
        return fresh;
    }

    /** Every field the renderer can read: equal colour value, and the same flag / event / insertion / font objects. */
    public static boolean same(Style a, Style b) {
        return a.getColor() != null && b.getColor() != null && a.getColor().getValue() == b.getColor().getValue()
                && java.util.Objects.equals(a.getColor(), b.getColor())
                && a.isBold() == b.isBold() && a.isItalic() == b.isItalic() && a.isStrikethrough() == b.isStrikethrough()
                && a.isUnderlined() == b.isUnderlined() && a.isObfuscated() == b.isObfuscated()
                && a.getClickEvent() == b.getClickEvent() && a.getHoverEvent() == b.getHoverEvent() && a.getInsertion() == b.getInsertion()
                && a.getFont() == b.getFont() && a.equals(b);
    }
}
