package bons.furious.patch.embeddium_hidden_blocks;

import com.supermartijn642.fusion.model.modifiers.block.ModelsByRandomOffset;
import org.embeddedt.embeddium.impl.render.frapi.FRAPIModelUtils;

/**
 * Bons and Furious switch fusion_hidden_block_models (Fusion 1.3.15b next to Embeddium 1.0.15 on 1.21.1, ported from the
 * Forge switch for Fusion 1.3.14+a next to Embeddium 0.3.31; the group class has the same public constructor). Loaded only from the
 * Fusion model mixins, so only with Fusion.
 *
 * Fusion's Embeddium renderer splits a block-model modifier into groups (ModelsByRandomOffset.Entry, a combined model
 * of the chosen parts) and hands each group to Embeddium's renderModel. A group is a new object per block, so the
 * question "does Embeddium draw it with its own face loop, or through Fabric's Indigo renderer?" cannot be asked of
 * the group that will be drawn; it is a property of the class, and is asked once of a group made here.
 */
public final class FusionGroups {
    /** True when Embeddium draws a Fusion group with its own face loop (a group is not a model of Fabric's rendering API). */
    public static final boolean FACE_LOOP = faceLoop();

    private FusionGroups() {
    }

    private static boolean faceLoop() {
        try {
            return !FRAPIModelUtils.isFRAPIModel(new ModelsByRandomOffset.Entry());
        } catch (Throwable t) {
            return false;                                  // not answerable: block-model modifiers are never called face-only
        }
    }
}
