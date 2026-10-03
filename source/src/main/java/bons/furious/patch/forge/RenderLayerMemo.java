package bons.furious.patch.forge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch forge_render_layer_memo (Forge 47.4.16 with Oculus 1.8.0, client).
 *
 * Chunk meshing asks ItemBlockRenderTypes.getRenderLayers(state) for every block. Oculus's mixin first looks the block up
 * in the shader pack's block-type map, then Forge looks it up in its render-type map; both lookups go through the block's
 * registry delegate and a hash map. For every block except leaves the answer depends only on the block, the map object
 * Oculus holds (a shader reload replaces it) and Forge's map (written only by setRenderLayer, while the client loads), so
 * each block now remembers its answer together with the Oculus map and a version that advances at the start and at the
 * return of setRenderLayer. A remembered answer is used only while both are unchanged; anything else runs the whole
 * original method, Oculus's lookup and every other mod's hook included (the mixin wraps the method, it does not replace it).
 *
 * Leaves are never remembered: their answer follows the fancy-graphics flag, which Embeddium redirects to its own leaves
 * quality setting. And the memo stays off when ItemBlockRenderTypes carries a mixin this switch does not know (its hook
 * might read other state); the known ones were checked for this pack on 2026-10-01: Oculus and Embeddium hook
 * getRenderLayers as described, Citadel, LionfishAPI, Quark, Vivecraft and Embeddium's model mixin hook other methods.
 */
public final class RenderLayerMemo {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.renderLayerMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.renderLayerMemo", "true"));
    private static final AtomicInteger VERSION = new AtomicInteger();
    private static final Set<String> KNOWN = Set.of(
            "bons.furious.mixin.forge.RenderLayerMemoMixin",
            "net.irisshaders.iris.mixin.MixinItemBlockRenderTypes",
            "org.embeddedt.embeddium.impl.mixin.features.options.render_layers.RenderLayersMixin",
            "org.embeddedt.embeddium.impl.mixin.features.render.model.RenderLayersMixin",
            "com.github.alexthe666.citadel.mixin.client.ItemBlockRenderTypesMixin",
            "com.github.L_Ender.lionfishapi.mixin.client.ItemBlockRenderTypesMixin",
            "org.violetmoon.quark.mixin.mixins.client.ItemBlockRenderTypesMixin",
            "org.vivecraft.mixin.client.renderer.ItemBlockRenderTypesMixin");
    /** 0 = not checked yet, 1 = only known mixins, 2 = another mixin is present (memo off). */
    private static volatile int neighbours;

    /** Implemented by Block through the mixin. */
    public interface Remembered {
        Object bons$renderLayers();

        void bons$renderLayers(Object entry);
    }

    /** One block's answer and the inputs it was computed from. */
    public record Entry(Object shaderMap, int version, ChunkRenderTypeSet layers) {
    }

    private RenderLayerMemo() {
    }

    /** In place of getRenderLayers (the whole method, wrapped): the remembered answer, or the original call. */
    public static ChunkRenderTypeSet layers(BlockState state, Operation<ChunkRenderTypeSet> original) {
        Block block = state.getBlock();
        if (!enabled || block instanceof LeavesBlock || !neighboursKnown()) return original.call(state);
        Object shaderMap = WorldRenderingSettings.INSTANCE.getBlockTypeIds();
        int version = VERSION.get();
        Remembered r = (Remembered) block;
        if (r.bons$renderLayers() instanceof Entry e && e.version() == version && e.shaderMap() == shaderMap) return e.layers();
        ChunkRenderTypeSet layers = original.call(state);
        r.bons$renderLayers(new Entry(shaderMap, version, layers));
        return layers;
    }

    /** Start and return of setRenderLayer(Block, ChunkRenderTypeSet), the only writer of Forge's map. */
    public static void layersChanging() {
        VERSION.incrementAndGet();
    }

    public static int version() {
        return VERSION.get();
    }

    private static boolean neighboursKnown() {
        int n = neighbours;
        if (n == 0) {
            String other = null;
            try {
                for (Method m : ItemBlockRenderTypes.class.getDeclaredMethods()) {
                    MixinMerged merged = m.getAnnotation(MixinMerged.class);
                    if (merged != null && !KNOWN.contains(merged.mixin())) {
                        other = merged.mixin();
                        break;
                    }
                }
            } catch (Throwable t) {
                other = "(check failed: " + t + ")";
            }
            n = other == null ? 1 : 2;
            if (other != null) LOGGER.info("Bons and Furious: forge_render_layer_memo stays off because ItemBlockRenderTypes also carries {}", other);
            neighbours = n;
        }
        return n == 1;
    }
}
