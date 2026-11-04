package bons.furious.patch.blessfulled_c49;

import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aqutheseal.blessfulled.BSClientConfig;
import org.aqutheseal.blessfulled.util.HurtTypes;

/**
 * Bons and Furious switches for Blessfulled 0.6's hurt overlay (All Rights Reserved; client side). Ours; no Blessfulled
 * code is carried.
 *
 * What the mod does. After a mob's model is drawn it draws the model a second time as a red-tinted overlay whose opacity
 * is hurtTime / 10, so it fades out over the 10 ticks after a hit: HurtOverlayTriggers.renderOverlay for ordinary mobs
 * (only while the mob has a damage source, i.e. up to 40 ticks after a hit), and a GeckoLib render layer
 * (BSGeckoLib$FrostboundGeoLayer.render, a full GeoRenderer.reRender of the baked model) for GeckoLib mobs. The GeckoLib
 * layer does not check for a missing damage source: with the "only by player" setting it throws a NullPointerException on
 * the render thread for every GeckoLib mob that was not hurt recently, and with the default setting it re-renders every
 * such mob every frame at opacity 0.
 *
 * Why skipping is invisible. The overlay is drawn with RenderType.entityTranslucent; the vertex alpha is
 * (int) (hurtTime / 10 * 255): 0 at hurtTime 0 and 25 at hurtTime 1, and the entity translucent shader discards every
 * fragment whose alpha is below 0.1 (25.5 / 255), whatever the texture. The overlay is the same model in the same pose
 * drawn right after the mob itself, so it cannot add depth the mob did not already write.
 *
 * blessfulled_geckolib_overlay_null_source (fix): the GeckoLib layer returns without drawing for a mob with no damage
 * source when the setting is "only by player" (where it threw) or the overlay is invisible (hurtTime 0 or 1).
 * blessfulled_invisible_overlay_skip (variant): both overlay paths return without drawing while hurtTime is 0 or 1.
 *
 * -Dbons_and_furious.blessfulledGeckolibOverlayNullSource=false / -Dbons_and_furious.blessfulledInvisibleOverlaySkip=false
 * switch them off at run time.
 */
public final class HurtOverlaySkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean nullSourceFix = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.blessfulledGeckolibOverlayNullSource", "true"));
    public static volatile boolean invisibleSkip = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.blessfulledInvisibleOverlaySkip", "true"));
    private static volatile boolean announcedNull, announcedInvisible;

    private HurtOverlaySkip() {}

    /** The GeckoLib layer's answer for a mob without a damage source: true = draw nothing. */
    public static boolean nullSourceSkip(Object animatable) {
        if (!nullSourceFix || !(animatable instanceof LivingEntity e)) return false;
        // the layer's own first call: answers null for a source older than 40 ticks (and clears it), the same on every call
        if (e.m_21225_() != null) return false;
        if (BSClientConfig.hurtConfiguration != HurtTypes.ONLY_BY_PLAYER && e.f_20916_ > 1) return false;
        if (!announcedNull) {
            announcedNull = true;
            LOGGER.info("Bons and Furious: blessfulled_geckolib_overlay_null_source applies (Blessfulled's GeckoLib hurt overlay handles mobs without a damage source)");
        }
        return true;
    }

    /** True while the hurt overlay would be invisible (hurtTime 0 or 1: vertex alpha 0 or 25, below the shader's cut). */
    public static boolean invisible(Object entity) {
        if (!invisibleSkip || !(entity instanceof LivingEntity e) || e.f_20916_ > 1) return false;
        if (!announcedInvisible) {
            announcedInvisible = true;
            LOGGER.info("Bons and Furious: blessfulled_invisible_overlay_skip applies (Blessfulled's hurt overlay is not drawn while it is invisible)");
        }
        return true;
    }
}
