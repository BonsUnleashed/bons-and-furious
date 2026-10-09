package bons.furious.patch.oculus_glyph_fix;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch oculus_glyph_extended_vertex_fix (Oculus 1.8.0 + Embeddium 0.3.31, client only). Helper of
 * bons.furious.mixin.oculus_glyph_fix.GlyphExtSerializerFixMixin: the runtime flag, a counter and one INFO line.
 */
public final class GlyphFix {
    /** Runtime switch; -Dbons_and_furious.oculusGlyphExtendedVertexFix=false also turns it off (the original call then runs). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oculusGlyphExtendedVertexFix", "true"));
    /** Oculus's IrisVertexFormats.GLYPH stride (52 bytes in 1.8.0; the guard on IrisVertexFormats.<clinit> keeps it so). */
    public static final int STRIDE = 52;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private GlyphFix() {
    }

    public static void fixed() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: oculus_glyph_extended_vertex_fix: glyph quads written through Embeddium get their normal, tangent "
                    + "and mid-texture coordinate on their own four vertices");
        }
    }
}
