package bons.furious.patch.embeddium_hidden_blocks;

import bons.furious.mixin.embeddium_hidden_blocks.BlockRendererOcclusionAccessor;
import bons.furious.mixin.embeddium_hidden_blocks.BlockRendererRegistryAccessor;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraftforge.client.model.data.ModelData;
import org.embeddedt.embeddium.api.BlockRendererRegistry;
import org.embeddedt.embeddium.model.UnwrappableBakedModel;
import org.embeddedt.embeddium.render.frapi.FRAPIModelUtils;
import org.slf4j.Logger;

/**
 * Bons and Furious switch embeddium_hidden_block_skip (Embeddium 0.3.31). SRG member names.
 *
 * For every block of a section whose render shape is MODEL, Embeddium's meshing task asks the model for its model
 * data, then for its render layers, and renders the model once per layer; only inside that, face by face, does it ask
 * whether the face can be seen (BlockRenderer.isFaceVisible -> BlockOcclusionCache.shouldDrawSide). In generated
 * terrain most blocks have no face that can be seen (88 to 96 % of the model blocks of our world), and for models with
 * connected textures the model data alone is a scan of the block's neighbours.
 *
 * A block emits nothing when (1) none of its six faces is drawn, by the same shouldDrawSide calls with the same
 * arguments the renderer would make (its own occlusion cache, the context's local slice, the block's state and
 * position; a mod that changes shouldDrawSide changes these answers with it), and (2) its model can only produce quads
 * that belong to a face: {@link CulledOnly}, answered by the model classes that are known (vanilla simple models whose
 * unculled list is empty, weighted models of those; Fusion's models through fusion_hidden_block_models), none of them a
 * model of Fabric's rendering API (those are drawn by their own emitBlockQuads through Indigo), and (3) no
 * custom block renderer is registered with Embeddium (BlockRendererRegistry): such a renderer draws whatever it likes
 * for a block, hidden faces or not. For such a block the meshing task's second getRenderShape() answer is replaced by
 * INVISIBLE, which is the game's own way to say "no model here": the model branch is not entered, while the fluid, the
 * block entity and the occlusion graph of the block are handled as before. Every other block gets the original answer.
 *
 * Nothing the skipped calls do reaches past the block. Model data, render layers and quads of the known model classes
 * only read (and fill private memos of fixed values); no vertex means no sprite and no buffer is touched; the task's
 * own random source is re-seeded before each use. The block renderer's random source is left as the last rendered
 * block left it instead of as this block would have: its own geometry calls re-seed it before every use, and the only
 * other readers are custom block renderers (none registered, by rule 3) and Fabric's Indigo renderer, which hands
 * models a supplier that re-seeds it first. Mods that cancel a block's rendering at the head of renderModel (A Good
 * Place while a block is animated, Cull Leaves for buried leaves) only remove geometry. Oculus sets its block id before
 * the model is drawn and clears it after the block: a block that draws nothing needs neither.
 * -Dbons_and_furious.hiddenBlocks=false keeps the original answer; .metrics=true prints a summary when the JVM exits;
 * .verify=true (rigs) draws every block as shipped and asks each block the switch would skip for its quads without a
 * face, the task's way: one such quad is counted and logged as an error.
 */
public final class HiddenBlocks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Runtime switch (the config switch acts when classes are transformed): false keeps the original answer for every
     * block. A rig flips it between full rebuilds of the same sections to compare the meshes byte for byte.
     */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.hiddenBlocks", "true"));
    /** Rigs: every block is drawn as shipped, and a block the switch would skip is asked for its quads without a face. */
    static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.hiddenBlocks.verify");
    static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.hiddenBlocks.metrics");
    public static final LongAdder SKIPPED = new LongAdder(), VISIBLE = new LongAdder(), OTHER_MODEL = new LongAdder(), CUSTOM = new LongAdder(),
            WRONG = new LongAdder();
    private static final Direction[] FACES = Direction.values();
    /** Embeddium's list of custom block renderer sources (one object for the registry's lifetime), read on first use. */
    private static volatile List<?> populators;
    private static volatile boolean customSaid;

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious embeddium_hidden_block_skip: model blocks "
                    + (VERIFY ? "the switch would skip (drawn as shipped and checked) " : "skipped ") + SKIPPED.sum() + ", with a visible face " + VISIBLE.sum()
                    + ", models not known to be face-only " + OTHER_MODEL.sum() + ", left alone because custom block renderers are registered " + CUSTOM.sum()
                    + (VERIFY ? ", of the checked ones with a quad that belongs to no face " + WRONG.sum() : "")),
                    "Bons and Furious hidden blocks summary"));
        }
    }

    private HiddenBlocks() {
    }

    /** Implemented (through mixins) by the baked model classes whose quads are known. */
    public interface CulledOnly {
        /** True when this model can only ever return quads for a face (a non-null side): never an unculled quad. */
        boolean bons$culledOnly();
    }

    /**
     * True when the model can only produce face quads and Embeddium draws it with its own face loop: a model that
     * Fabric's rendering API marks as its own (FRAPIModelUtils.isFRAPIModel: not a vanilla adapter) is drawn through
     * the Indigo renderer by whatever its emitBlockQuads does, and is never called face-only here.
     */
    public static boolean culledOnly(BakedModel model) {
        return model instanceof CulledOnly c && c.bons$culledOnly() && !FRAPIModelUtils.isFRAPIModel(model);
    }

    /** True while a mod has registered a source of custom block renderers with Embeddium. */
    private static boolean customRenderers() {
        List<?> list = populators;
        if (list == null) {
            list = ((BlockRendererRegistryAccessor) BlockRendererRegistry.instance()).bons$renderPopulators();
            populators = list;
        }
        if (list.isEmpty()) return false;
        if (!customSaid) {
            customSaid = true;
            LOGGER.info("Bons and Furious: embeddium_hidden_block_skip leaves every block to Embeddium: {} source(s) of custom block renderers "
                    + "are registered with it, and such a renderer may draw a block whose faces are all hidden", list.size());
        }
        return true;
    }

    /**
     * The verify flag: what the meshing task would draw for a block the switch calls hidden, asked the task's way (model
     * data, the block's seed, Embeddium's unwrapping of a weighted model, one pass per render layer). Its faces are not
     * drawn by the same test the renderer uses, so all that could reach the mesh is a quad that belongs to no face:
     * there must be none.
     */
    private static void verify(BakedModel model, BlockState state, BlockPos pos, BlockAndTintGetter view, Map<BlockPos, ModelData> taskData) {
        try {
            ModelData data = model.getModelData(view, pos, state, taskData.getOrDefault(pos, ModelData.EMPTY));
            long seed = state.m_60726_(pos);
            RandomSource random = new SingleThreadedRandomSource(seed);
            BakedModel drawn = UnwrappableBakedModel.unwrapIfPossible(model, random);
            random.m_188584_(seed);
            for (RenderType layer : drawn.getRenderTypes(state, random, data)) {
                random.m_188584_(seed);
                int free = drawn.getQuads(state, null, random, data, layer).size();
                if (free != 0) {
                    WRONG.increment();
                    LOGGER.error("Bons and Furious: embeddium_hidden_block_skip would have skipped {} at {}, but its model {} has {} quad(s) without a face "
                            + "in layer {}", state, pos.m_7949_(), drawn.getClass().getName(), free, layer);
                    return;
                }
            }
        } catch (Throwable t) {
            WRONG.increment();
            LOGGER.error("Bons and Furious: embeddium_hidden_block_skip could not check {} at {}", state, pos.m_7949_(), t);
        }
    }

    /** ChunkBuilderMeshingTask.execute: the render shape the model branch tests. */
    public static RenderShape shape(RenderShape shape, BlockRenderCache cache, BlockRenderContext context, BlockState state, BlockPos pos,
                                    Map<BlockPos, ModelData> taskData) {
        if (shape != RenderShape.MODEL || !enabled) return shape;
        if (customRenderers()) {
            if (METRICS) CUSTOM.increment();
            return shape;
        }
        BakedModel model = cache.getBlockModels().m_110893_(state);
        if (!culledOnly(model)) {
            if (METRICS) OTHER_MODEL.increment();
            return shape;
        }
        BlockOcclusionCache occlusion = ((BlockRendererOcclusionAccessor) cache.getBlockRenderer()).bons$occlusionCache();
        BlockAndTintGetter view = context.localSlice();
        for (Direction face : FACES) {
            if (occlusion.shouldDrawSide(state, view, pos, face)) {
                if (METRICS) VISIBLE.increment();
                return shape;
            }
        }
        if (METRICS) SKIPPED.increment();
        if (VERIFY) {
            verify(model, state, pos, view, taskData);
            return shape;                                  // drawn as shipped
        }
        return RenderShape.INVISIBLE;
    }
}
