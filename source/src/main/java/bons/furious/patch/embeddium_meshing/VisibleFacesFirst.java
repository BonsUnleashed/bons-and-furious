package bons.furious.patch.embeddium_meshing;

import com.mojang.logging.LogUtils;
import com.supermartijn642.fusion.model.modifiers.block.ModelsByRandomOffset;
import com.supermartijn642.fusion.model.types.base.BaseBakedModel;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import org.slf4j.Logger;

/**
 * Bons and Furious switch embeddium_visible_faces_first (Embeddium 0.3.31 BlockRenderer with Fusion 1.3.14+a models,
 * client).
 *
 * Embeddium's BlockRenderer.renderModel asks each of the six faces for its quads first (getGeometry: reseed the
 * renderer's random to the block seed, model.getQuads) and only then whether the face is visible (isFaceVisible), and
 * drops the quads of a hidden face. With xali's Fusion pack nearly every terrain block is a Fusion model whose getQuads
 * runs the whole connected-texture pipeline, so hidden faces cost as much as visible ones. For the model types below the
 * visibility test now runs first and a hidden face gets an empty list without its quads being built.
 *
 * Why the mesh is identical: a hidden face's quads were dropped anyway; a visible face's quads are built exactly as
 * before (getGeometry reseeds the random for every face, so skipping another face's call changes nothing, and the
 * null-face call that leaves the final random state is never skipped); isFaceVisible is a pure query (its occlusion
 * cache only memoizes) and is not called twice for a visible face. The skipped getQuads calls must have no effect on
 * anything else, which holds for an allowlist only: a Fusion ModelsByRandomOffset.Entry (Fusion's Embeddium renderer
 * splits a model-modifier block into these) whose sub-models are all SimpleBakedModel, Fusion's BaseBakedModel, or a
 * WeightedBakedModel whose variants are all of those two. Their getQuads only read their model data, fill private memos
 * of deterministic values (BaseBakedModel's per-direction LazyQuadProcessor, Fusion's lazily built tile quads, the
 * render-layer memo) and consume the renderer's random (reseeded per face). A static audit of every class in Fusion
 * 1.3.14+a and BonsFusion 1.3.0-alpha.1 (evidence/embeddium_meshing/propertystore-audit.txt) found no PropertyStore
 * write or read on any quad-processing path; the only writes are in extractState, which getModelData runs for all faces
 * regardless, or in a getQuads call without model data, where they go to a store created for that call alone.
 */
public final class VisibleFacesFirst {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.embeddiumVisibleFacesFirst=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.embeddiumVisibleFacesFirst", "true"));
    private static volatile boolean announced;

    private VisibleFacesFirst() {
    }

    /** Fusion's random-offset entry (sub-model list read through EntryModels). */
    public interface EntryModels {
        List<BakedModel> bons$models();
    }

    /** A WeightedBakedModel that can tell whether all its variants are allowlisted leaves (computed once). */
    public interface AllowlistedVariants {
        boolean bons$variantsAllowlisted();
    }

    /** True when skipping a hidden face's getQuads on this model has no effect but the dropped quads. */
    public static boolean allowlisted(BakedModel model) {
        if (model == null || model.getClass() != ModelsByRandomOffset.Entry.class) return false;
        List<BakedModel> models = ((EntryModels) model).bons$models();
        int n = models.size();
        if (n == 0) return false;
        for (int i = 0; i < n; i++) {
            if (!leaf(models.get(i), true)) return false;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: embeddium_visible_faces_first tests face visibility before building Fusion block geometry");
        }
        return true;
    }

    /** SimpleBakedModel, Fusion's BaseBakedModel, or (when allowed) a WeightedBakedModel of those. Exact classes only. */
    public static boolean leaf(BakedModel model, boolean weightedAllowed) {
        if (model == null) return false;
        Class<?> c = model.getClass();
        if (c == SimpleBakedModel.class || c == BaseBakedModel.class) return true;
        return weightedAllowed && c == WeightedBakedModel.class && ((AllowlistedVariants) model).bons$variantsAllowlisted();
    }
}
