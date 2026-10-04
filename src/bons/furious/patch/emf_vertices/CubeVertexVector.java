package bons.furious.patch.emf_vertices;

import com.mojang.blaze3d.systems.RenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector4f;

/**
 * Bons and Furious switch emf_cube_vertex_vector (Entity Model Features 3.2.4 for 1.20.1 Forge, client only). Helper of
 * bons.furious.mixin.emf_vertices.EmfCubeVertexVectorMixin.
 *
 * EMF renders its custom model cubes (Fresh Animations and every other EMF model) through EMFModelPartCustom$EMFCube's
 * compile, which transforms each vertex as pose.transform(new Vector4f(x / 16, y / 16, z / 16, 1)) and passes the
 * vector's x, y and z to VertexConsumer.vertex. In play the JIT does not remove that vector: the 1.0.26 client recording
 * (cli10_jfr1, entities window) has 710 MB of org.joml.Vector4f allocated in that method in 38 s, 14.9% of the render
 * thread's allocation, with compile at 3.8% self of the render thread.
 *
 * On the render thread the vertex is now built in one reused Vector4f: set(x, y, z, w) assigns the same four floats the
 * constructor assigns, and the following Matrix4f.transform is EMF's own unchanged call, so the same JOML arithmetic gives
 * the same floats; compile reads x, y and z into the vertex call's arguments before that call runs, so a consumer that
 * renders again (re-entering compile on the same thread) finds the vector already read and the next vertex sets it
 * afresh. Other threads allocate as EMF does. -Dbons_and_furious.emfCubeVertexVector=false allocates again at runtime.
 */
public final class CubeVertexVector {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.emfCubeVertexVector=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfCubeVertexVector", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Vector4f SCRATCH = new Vector4f();
    private static volatile boolean announced;

    private CubeVertexVector() {
    }

    /** In place of new Vector4f(x, y, z, w) in EMFCube.compile. */
    public static Vector4f of(float x, float y, float z, float w) {
        if (enabled && RenderSystem.isOnRenderThread()) {
            if (!announced) announce();
            return SCRATCH.set(x, y, z, w);
        }
        return new Vector4f(x, y, z, w);
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: emf_cube_vertex_vector: EMF model cubes transform their vertices in one reused vector on the render thread");
    }
}
