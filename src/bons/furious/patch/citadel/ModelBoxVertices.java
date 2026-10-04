package bons.furious.patch.citadel;

import com.mojang.blaze3d.systems.RenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Bons and Furious switches citadel_model_vertices (Citadel 2.6.1) and lionfishapi_model_vertices (LionfishAPI 3.0, the
 * copy L_Ender's Cataclysm uses), client.
 *
 * Citadel's model boxes (Alex's Mobs, Alex's Caves, Ice and Fire, Rats, Untamed Wilds, ...) and LionfishAPI's are drawn
 * by doRender: per quad new Vector3f(normal) then mul(normalMatrix), per vertex new Vector4f(x/16, y/16, z/16, 1) then
 * mul(poseMatrix), and the vertex call with the vectors' components. In play the JIT does not remove these vectors
 * (review-8 JFR: 90 MB of org.joml.Vector4f in 90 s under AdvancedModelBox.doRender, 1.85% of the render thread's
 * allocation, 1.18% of its time): the call sites are megamorphic in a full pack.
 *
 * On the render thread both constructions now return one reused vector set to the same components. The loop then runs
 * the same JOML mul on it, so the same floats come out, and it reads them into the vertex call's arguments before the
 * next construction could reuse the vector (doRender does not recurse inside its quad loop: children render after it).
 * Other threads allocate as before. The vertex calls themselves are the mods' own.
 *
 * -Dbons_and_furious.modelBoxVertices=false restores the mods' own allocations at runtime.
 */
public final class ModelBoxVertices {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** citadel_model_vertices / lionfishapi_model_vertices: false = the mods' own allocations. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modelBoxVertices", "true"));

    private static final Vector3f NORMAL = new Vector3f();
    private static final Vector4f POSITION = new Vector4f();
    private static volatile boolean announced;

    private ModelBoxVertices() {}

    /** new Vector3f(normal), as the quad loop builds it before transforming it with the pose's normal matrix. */
    public static Vector3f normal(Vector3fc source) {
        if (enabled && RenderSystem.isOnRenderThread()) {
            announce();
            return NORMAL.set(source);
        }
        return new Vector3f(source);
    }

    /** new Vector4f(x, y, z, w), as the vertex loop builds it before transforming it with the pose matrix. */
    public static Vector4f position(float x, float y, float z, float w) {
        if (enabled && RenderSystem.isOnRenderThread()) {
            return POSITION.set(x, y, z, w);
        }
        return new Vector4f(x, y, z, w);
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: citadel_model_vertices / lionfishapi_model_vertices apply (model boxes are transformed in reused vectors)");
        }
    }
}
