package bons.furious.patch.lionfishapi_bulk_c49;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lionfishapi_model_bulk_vertices (Lionfish API 2.8, also checked on 3.0, with Embeddium 0.3.31;
 * client), a trade-off, off by default: the bytes it writes are the game's, and it is faster where it writes a box in one
 * block (Embeddium's own entity buffer, no shader pack), but with a shader pack every box takes the stand-down path, where
 * the added class test makes each box a few per cent slower. Lionfish API is LGPL; none of its code is carried. Ours, idea
 * from Collections Of Optimizations (lionfishapi.fastModelRender), idea text only.
 *
 * What Lionfish API does: AdvancedModelBox.doRender (every box of L_Ender's Cataclysm's bosses, mobs and 3D items, every
 * frame) transforms each quad's normal and each corner's position and hands every corner to VertexConsumer.vertex with 14
 * arguments, 24 calls per cube. With Embeddium, an entity renderer's consumer is Embeddium's SodiumBufferBuilder, which
 * packs each call into the buffer and then advances it by one vertex.
 *
 * What the switch does (EmbeddiumBoxWriter): the same transforms with the same JOML calls in the same order, the corners
 * packed by Embeddium's own entity-vertex writer (ModelVertex: the same position, colour, texture, overlay, light and normal
 * packers SodiumBufferBuilder uses) into a stack buffer, then pushed into the consumer in one block, as Embeddium does for
 * vanilla model parts. The bytes in the buffer and its vertex count are the same as before. It stands down (Lionfish's own
 * loop runs) unless the consumer is exactly Embeddium's SodiumBufferBuilder over a buffer of the entity format with no fixed
 * colour, the call is on the render thread, no shader pack is in use (Oculus: other buffer formats and batched rendering),
 * every quad has its normal and four corners and the box fits the stack.
 *
 * Flag: -Dbons_and_furious.lionfishapiModelBulkVertices=false runs Lionfish's loop for every box.
 */
public final class BulkVertices {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lionfishapiModelBulkVertices", "true"));
    /** Boxes written in one block (render thread only; for probes and the harness). */
    public static long bulk;
    /** Embeddium's fast consumer class, null without Embeddium (the mixin's handler tests the consumer against it first). */
    public static final Class<?> FAST = find("me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder");
    /** Embeddium's fast consumer and entity-vertex writer are present (the writer class is loaded only then). */
    static final boolean EMBEDDIUM = FAST != null && find("net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex") != null;
    /** Oculus' API is present (asked whether a shader pack is in use). */
    static final boolean IRIS = find("net.irisshaders.iris.api.v0.IrisApi") != null;
    private static volatile boolean announced;

    private BulkVertices() {
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, BulkVertices.class.getClassLoader());
        } catch (Throwable absent) {
            return null;
        }
    }

    /**
     * Writes the box in one block when the switch may; true means it was written (the caller skips doRender), false means
     * nothing was written (doRender runs). The mixin's handler tests the consumer's class before calling this (tested here
     * again for any other caller); EmbeddiumBoxWriter tests the rest.
     */
    public static boolean render(AdvancedModelBox box, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
                                 float red, float green, float blue, float alpha) {
        if (!enabled || !EMBEDDIUM || consumer == null || consumer.getClass() != FAST
                || !EmbeddiumBoxWriter.write(box, pose, consumer, light, overlay, red, green, blue, alpha)) return false;
        bulk++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lionfishapi_model_bulk_vertices applies (Lionfish API model boxes are written to Embeddium's buffer in one block)");
        }
        return true;
    }
}
