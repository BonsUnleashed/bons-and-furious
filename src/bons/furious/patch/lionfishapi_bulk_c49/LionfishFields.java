package bons.furious.patch.lionfishapi_bulk_c49;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.client.model.tools.LionfishModelRenderUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.joml.Vector3f;

/**
 * lionfishapi_model_bulk_vertices: Lionfish API keeps a box's quads and their corners in package-private classes
 * (LionfishModelRenderUtils.TexturedQuad and PositionTextureVertex) with public fields; those fields are read here through
 * method handles made once (constants to the JIT, so the reads cost what Lionfish's own field reads cost). When a field
 * is missing or not readable (another Lionfish build), READY stays false and the switch stands down.
 */
final class LionfishFields {
    /** Every handle was made: the fields exist with the types Lionfish API 2.8 / 3.0 give them. */
    static final boolean READY;
    private static final MethodHandle QUADS;
    private static final MethodHandle NORMAL;
    private static final MethodHandle CORNERS;
    private static final MethodHandle POSITION;
    private static final MethodHandle TEXTURE_U;
    private static final MethodHandle TEXTURE_V;

    static {
        MethodHandle quads = null, normal = null, corners = null, position = null, textureU = null, textureV = null;
        boolean ready = false;
        try {
            String utils = "com.github.L_Ender.lionfishapi.client.model.tools.LionfishModelRenderUtils$";
            ClassLoader loader = AdvancedModelBox.class.getClassLoader();
            Class<?> quad = Class.forName(utils + "TexturedQuad", false, loader);
            Class<?> corner = Class.forName(utils + "PositionTextureVertex", false, loader);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(quad, MethodHandles.lookup());
            quads = lookup.findGetter(LionfishModelRenderUtils.ModelBox.class, "quads", quad.arrayType())
                    .asType(MethodType.methodType(Object[].class, LionfishModelRenderUtils.ModelBox.class));
            normal = lookup.findGetter(quad, "normal", Vector3f.class).asType(MethodType.methodType(Vector3f.class, Object.class));
            corners = lookup.findGetter(quad, "vertexPositions", corner.arrayType()).asType(MethodType.methodType(Object[].class, Object.class));
            position = lookup.findGetter(corner, "position", Vector3f.class).asType(MethodType.methodType(Vector3f.class, Object.class));
            textureU = lookup.findGetter(corner, "textureU", float.class).asType(MethodType.methodType(float.class, Object.class));
            textureV = lookup.findGetter(corner, "textureV", float.class).asType(MethodType.methodType(float.class, Object.class));
            ready = true;
        } catch (Throwable unknown) {
            // another Lionfish build, or the fields cannot be read: Lionfish's own loop draws every box
        }
        QUADS = quads;
        NORMAL = normal;
        CORNERS = corners;
        POSITION = position;
        TEXTURE_U = textureU;
        TEXTURE_V = textureV;
        READY = ready;
    }

    private LionfishFields() {
    }

    /** ModelBox.quads (TexturedQuad[]). */
    static Object[] quads(LionfishModelRenderUtils.ModelBox cube) throws Throwable {
        return (Object[]) QUADS.invokeExact(cube);
    }

    /** TexturedQuad.normal. */
    static Vector3f normal(Object quad) throws Throwable {
        return (Vector3f) NORMAL.invokeExact(quad);
    }

    /** TexturedQuad.vertexPositions (PositionTextureVertex[]). */
    static Object[] corners(Object quad) throws Throwable {
        return (Object[]) CORNERS.invokeExact(quad);
    }

    /** PositionTextureVertex.position. */
    static Vector3f position(Object corner) throws Throwable {
        return (Vector3f) POSITION.invokeExact(corner);
    }

    /** PositionTextureVertex.textureU. */
    static float textureU(Object corner) throws Throwable {
        return (float) TEXTURE_U.invokeExact(corner);
    }

    /** PositionTextureVertex.textureV. */
    static float textureV(Object corner) throws Throwable {
        return (float) TEXTURE_V.invokeExact(corner);
    }
}
