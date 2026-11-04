package bons.furious.patch.mowziesmobs182_client_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Bons and Furious switch mowziesmobs_elokosa_blank_transform_skip (Mowzie's Mobs 1.8.2; client; since 1.0.49; an exact
 * variant). Idea: Collections Of Optimizations (mowziesmobs.skipBlankElokosaTransform), idea text only. Our own logic; no
 * Mowzie's Mobs code (custom licence, wrappers only).
 *
 * The Elokosa's transform layer (ElokosaTransformGeoLayer) draws the whole model a second time every frame with its
 * "transforming" texture: it asks the buffer source for an entity-translucent buffer and has the renderer re-render the
 * model into it with the opacity of the model's transformTextureController as the vertex alpha (negated; 0 before the
 * model has started). While the Elokosa is not transforming that alpha is 0 (or -0), every vertex of the pass carries
 * alpha 0, and the game's entity-translucent shader throws away every fragment whose alpha is below 0.1, so the pass
 * draws nothing.
 *
 * What changes: while that alpha is exactly 0 and no shader pack is in use (Oculus' API; a shader pack may treat alpha
 * differently, then the pass runs as before), the re-render still runs in full (the renderer's matrices, the bones'
 * tracked positions, the tail's chain bones and a glowing Elokosa's outline are worked out and written as before), but
 * the vertices it would write into that one translucent buffer are dropped instead of being stored, sorted, uploaded and
 * thrown away by the GPU. The layer still asks the buffer source for the same buffer. The one difference (exact
 * variant): those invisible vertices are not in the translucent buffer.
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

    /** The wrapped GeoRenderer.reRender call of ElokosaTransformGeoLayer.render (its alpha is the layer's opacity). */
    public static void reRender(GeoRenderer<?> renderer, BakedGeoModel model, PoseStack poseStack, MultiBufferSource buffers, GeoAnimatable animatable,
                                RenderType renderType, VertexConsumer buffer, float partialTick, int light, int overlay, float red, float green,
                                float blue, float alpha, Operation<Void> original) {
        VertexConsumer into = enabled && alpha == 0.0f && !shaderPack() ? DISCARD : buffer;
        if (into == DISCARD && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (the Elokosa's transform layer keeps its fully transparent vertices out of the translucent buffer)", KEY);
        }
        original.call(renderer, model, poseStack, buffers, animatable, renderType, into, partialTick, light, overlay, red, green, blue, alpha);
    }

    /** True while a shader pack is in use (Oculus' public API); false without Oculus; true when that cannot be told. */
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
            irisInUse = null;   // no Oculus / Iris: the game's own shaders
        } catch (Throwable t) {
            irisInUse = MethodHandles.dropArguments(MethodHandles.constant(boolean.class, true), 0, Object.class);   // unknown: as with a shader pack
            irisInstance = MethodHandles.constant(Object.class, null);
            LOGGER.warn("Bons and Furious: {} cannot read Oculus' shader-pack state ({}); the Elokosa's transform layer renders as before", KEY, t.toString());
        } finally {
            resolved = true;   // volatile: a thread that sees it also sees the handles
        }
    }

    /** A vertex consumer that keeps nothing (the 1.20.1 interface's abstract methods; its defaults call these). */
    static final class Discard implements VertexConsumer {
        @Override
        public void m_5954_(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light,
                            float nx, float ny, float nz) {
        }

        @Override
        public VertexConsumer m_5483_(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer m_6122_(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer m_7421_(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer m_7122_(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer m_7120_(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer m_5601_(float x, float y, float z) {
            return this;
        }

        @Override
        public void m_5752_() {
        }

        @Override
        public void m_7404_(int r, int g, int b, int a) {
        }

        @Override
        public void m_141991_() {
        }
    }
}
