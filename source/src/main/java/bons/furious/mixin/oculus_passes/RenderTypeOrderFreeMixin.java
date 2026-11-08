package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.OrderFreeType;
import bons.furious.patch.oculus_passes.SortSkip;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.21.1 on NeoForge 21.1.252, client; `change`, off by default): adds the
 * interface OrderFreeType to every RenderType. bons$orderFreeQuads() answers, decided once per render type and kept in a
 * new byte field, whether the type is order-free (SortSkip.classify from its CompositeState: vanilla's ADDITIVE, LIGHTNING
 * or GLINT transparency, a write mask without depth, no colour logic op, QUADS). BufferSourceSortSkipMixin (the game's
 * immediate batches) and SegmentedSortSkipMixin (Iris's batched entity buffers) ask it before a batch's quad sort. Adds
 * no injector to RenderType and carries no Minecraft code.
 */
@Mixin(value = RenderType.class, remap = false)
public abstract class RenderTypeOrderFreeMixin implements OrderFreeType {
    /** 0 = not decided yet, 1 = order-free, 2 = keeps its sort. */
    @Unique
    private byte bons$orderFree;

    @Override
    public boolean bons$orderFreeQuads() {
        byte c = this.bons$orderFree;
        if (c == 0) {
            c = SortSkip.classify((RenderType) (Object) this) ? (byte) 1 : (byte) 2;
            this.bons$orderFree = c;
        }
        return c == 1;
    }
}
