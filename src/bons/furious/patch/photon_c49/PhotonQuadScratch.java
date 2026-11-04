package bons.furious.patch.photon_c49;

import com.mojang.blaze3d.systems.RenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

/**
 * Bons and Furious switch photon_particle_quad_scratch (Photon 1.1.17; client). Ours; no Photon code is carried.
 *
 * What Photon does. TileParticle.renderInternal draws every billboard-style particle (billboard, horizontal, vertical,
 * vertical billboard) on every frame by building a Vector3f[4] with four new corner vectors (-1,-1,0), (-1,1,0), (1,1,0),
 * (1,-1,0), scaling, rotating, scaling and moving each in place, and writing the four vertices. The JIT removes the other
 * vectors of that method (measured: 128 bytes are left per particle and frame, exactly the array and its four corners),
 * but it cannot remove the corners, because they are stored into the array.
 *
 * What the switch does. On the render thread the four corner constructions return four reused vectors set to the same
 * components; Photon's own code then runs the same JOML calls on them and reads them into the vertex calls before the
 * method returns, and nothing keeps them (the array is local). So every vertex is the one Photon writes. Other threads
 * (Photon's parallel particle rendering runs on the common fork-join pool) build new vectors as before. The array itself
 * stays (32 of the 128 bytes).
 *
 * -Dbons_and_furious.photonParticleQuadScratch=false switches it off at run time.
 */
public final class PhotonQuadScratch {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.photonParticleQuadScratch", "true"));
    private static final Vector3f C0 = new Vector3f(), C1 = new Vector3f(), C2 = new Vector3f(), C3 = new Vector3f();
    private static volatile boolean announced;

    private PhotonQuadScratch() {}

    /** new Vector3f(x, y, z) for the first corner. */
    public static Vector3f corner0(float x, float y, float z) {
        if (enabled && RenderSystem.isOnRenderThread()) {
            if (!announced) announce();
            return C0.set(x, y, z);
        }
        return new Vector3f(x, y, z);
    }

    /** new Vector3f(x, y, z) for the second corner. */
    public static Vector3f corner1(float x, float y, float z) {
        if (enabled && RenderSystem.isOnRenderThread()) return C1.set(x, y, z);
        return new Vector3f(x, y, z);
    }

    /** new Vector3f(x, y, z) for the third corner. */
    public static Vector3f corner2(float x, float y, float z) {
        if (enabled && RenderSystem.isOnRenderThread()) return C2.set(x, y, z);
        return new Vector3f(x, y, z);
    }

    /** new Vector3f(x, y, z) for the fourth corner. */
    public static Vector3f corner3(float x, float y, float z) {
        if (enabled && RenderSystem.isOnRenderThread()) return C3.set(x, y, z);
        return new Vector3f(x, y, z);
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: photon_particle_quad_scratch applies (Photon billboard particles are built in reused corner vectors)");
    }
}
