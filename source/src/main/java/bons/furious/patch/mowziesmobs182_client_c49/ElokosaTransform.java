package bons.furious.patch.mowziesmobs182_client_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Bons and Furious switch mowziesmobs_elokosa_blank_transform_skip (Mowzie's Mobs; 1.21.1 tested build: Mowzie's Mobs
 * 1.21.1-1.8.2 with GeckoLib 4.9.3; client; since 1.0.49; an exact variant). Idea: Collections Of Optimizations
 * (mowziesmobs.skipBlankElokosaTransform), idea text only. Our own logic; no Mowzie's Mobs code (custom licence, wrappers only).
 *
 * The Elokosa's transform layer (ElokosaTransformGeoLayer) draws the whole model a second time every frame with its
 * "transforming" texture: it asks the buffer source for an entity-translucent buffer and has the renderer re-render the
 * model into it, every vertex carrying the colour FastColor.ARGB32.colorFromFloat(opacity, 1, 1, 1), the opacity being the
 * model's transformTextureController (negated; 0 before the model has started). While the Elokosa is not transforming the
 * colour's alpha is 0, every vertex of the pass carries alpha 0, and the game's entity-translucent shader throws away every
 * fragment whose alpha is below 0.1, so the pass draws nothing.
 *
 * What changes: while that alpha is 0, the buffer the layer got is the translucent buffer itself (a BufferBuilder: not a
 * glowing Elokosa's outline pair or another mod's wrapper) and no shader pack is in use (Iris' API; a shader pack may treat
 * alpha differently, then the pass runs as before), the re-render still runs in full (the renderer's matrices, the bones'
 * tracked positions and the tail's chain bones are worked out as before), but the vertices it would write into that one
 * translucent buffer are dropped instead of being stored, sorted, uploaded and thrown away by the GPU. The layer still asks
 * the buffer source for the same buffer. The one difference (exact variant): those invisible vertices are not in the
 * translucent buffer.
 *
 * -Dbons_and_furious.mowziesmobsElokosaBlankTransformSkip=false always writes them.
 */
public final class ElokosaTransform {
    public static final String KEY = "mowziesmobs_elokosa_blank_transform_skip";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsElokosaBlankTransformSkip", "true"));
    /** Takes vertices and keeps nothing. */
    public static final VertexConsumer DISCARD = new Discard();
    private static volatile boolean announced, resolved;
    private static MethodHandle irisInstance, irisInUse;

    private ElokosaTransform() {
    }

    /** The wrapped GeoRenderer.reRender call of ElokosaTransformGeoLayer.render (its colour carries the layer's opacity). */
    public static void reRender(GeoRenderer<?> renderer, BakedGeoModel model, PoseStack poseStack, MultiBufferSource buffers, GeoAnimatable animatable,
                                RenderType renderType, VertexConsumer buffer, float partialTick, int light, int overlay, int colour,
                                Operation<Void> original) {
        VertexConsumer into = enabled && (colour >>> 24) == 0 && buffer instanceof BufferBuilder && !shaderPack() ? DISCARD : buffer;
        if (into == DISCARD && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (the Elokosa's transform layer keeps its fully transparent vertices out of the translucent buffer)", KEY);
        }
        original.call(renderer, model, poseStack, buffers, animatable, renderType, into, partialTick, light, overlay, colour);
    }

    /** True while a shader pack is in use (Iris' public API); false without Iris; true when that cannot be told. */
    static boolean shaderPack() {
        if (!resolved) resolve();
        if (irisInUse == null) return false;
        try {
            return (boolean) irisInUse.invoke(irisInstance.invoke());
        } catch (Throwable t) {
            return true;   // unknown: behave as with a shader pack (the vertices are written as before)
        }
    }

    private static synchronized void resolve() {
        if (resolved) return;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisInstance = MethodHandles.publicLookup().findStatic(api, "getInstance", MethodType.methodType(api)).asType(MethodType.methodType(Object.class));
            irisInUse = MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .asType(MethodType.methodType(boolean.class, Object.class));
        } catch (ClassNotFoundException absent) {
            irisInUse = null;   // no Iris: the game's own shaders
        } catch (Throwable t) {
            irisInUse = MethodHandles.dropArguments(MethodHandles.constant(boolean.class, true), 0, Object.class);   // unknown: as with a shader pack
            irisInstance = MethodHandles.constant(Object.class, null);
            LOGGER.warn("Bons and Furious: {} cannot read Iris' shader-pack state ({}); the Elokosa's transform layer renders as before", KEY, t.toString());
        } finally {
            resolved = true;   // volatile: a thread that sees it also sees the handles
        }
    }

    /** A vertex consumer that keeps nothing (the 1.21.1 interface's abstract methods, and the bulk vertex GeckoLib writes). */
    static final class Discard implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int colour, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        }
    }
}
