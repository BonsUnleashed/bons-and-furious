package bons.furious.patch.photon_c49;

import com.mojang.blaze3d.systems.RenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Bons and Furious switch photon_trail_ribbon_scratch (Photon 1.1.17; client). Ours; no Photon code is carried.
 *
 * What Photon does. TrailParticle$TailArray.renderInternal draws a trail as a ribbon: for every segment it builds about ten
 * vectors (the segment's points, its direction, the view vector, the averaged normal, the upper and lower edge) and pushes
 * two vertices. The JIT removes all of them but two (measured: 48 bytes per segment and frame): the direction vector,
 * which becomes the segment's normal and is kept as "lastNormal" for the next segment, and the upper edge, kept as
 * "lastUp" for the ribbon's end; values carried from one loop pass to the next defeat the JIT's allocation removal.
 *
 * What the switch does. On the render thread those two constructions (and the head's upper edge, which also becomes
 * "lastUp") return reused vectors set to the same components, from two alternating vectors each: each construction takes
 * the vector the previous one did not, so the previous segment's normal and upper edge stay untouched until Photon has
 * used them. Photon's own code runs the same JOML calls on them and writes the same vertices; nothing keeps them after the
 * method returns. Other threads (Photon's parallel trail rendering) build new vectors as before.
 *
 * -Dbons_and_furious.photonTrailRibbonScratch=false switches it off at run time.
 */
public final class PhotonTrailScratch {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.photonTrailRibbonScratch", "true"));
    private static final Vector3f NORMAL_A = new Vector3f(), NORMAL_B = new Vector3f(), UP_A = new Vector3f(), UP_B = new Vector3f();
    /** Which vector the last construction handed out (render thread only). */
    private static boolean normalB, upB;
    private static volatile boolean announced;

    private PhotonTrailScratch() {}

    /** new Vector3f(next): the segment direction that becomes its normal ("lastNormal" of the next segment). */
    public static Vector3f normal(Vector3fc source) {
        if (enabled && RenderSystem.isOnRenderThread()) {
            if (!announced) announce();
            Vector3f v = (normalB = !normalB) ? NORMAL_B : NORMAL_A;
            return v.set(source);
        }
        return new Vector3f(source);
    }

    /** new Vector3f(point): the upper ribbon edge of a segment or of the head ("lastUp"). */
    public static Vector3f up(Vector3fc source) {
        if (enabled && RenderSystem.isOnRenderThread()) {
            Vector3f v = (upB = !upB) ? UP_B : UP_A;
            return v.set(source);
        }
        return new Vector3f(source);
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: photon_trail_ribbon_scratch applies (Photon trail ribbons reuse their carried vectors)");
    }
}
