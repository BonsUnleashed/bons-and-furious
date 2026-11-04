package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.OrderFreeType;
import bons.furious.patch.oculus_passes.SortSkip;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.20.1 on Forge 47.4.28 RenderType, client; `change`, off by default).
 * RenderType.end (m_276775_) calls BufferBuilder.setQuadSorting (m_277127_) for every sortOnUpload type before ending the
 * batch. MixinExtras @WrapWithCondition on that call: it is left out when this render type is order-free
 * (bons$orderFreeQuads, decided once per render type by SortSkip.classify from its CompositeState: vanilla's ADDITIVE,
 * LIGHTNING or GLINT transparency, a write mask without depth, no colour logic op, QUADS) and no shader pack is in use
 * (SortSkip.leaveOutSort). end() then writes no sorted index buffer and draws the quads in buffer order with the shared
 * sequential index buffer; the vertex bytes are the same. The mixin extends RenderStateShard (RenderType's superclass) only
 * to read vanilla's protected shard constants it hands to SortSkip.classify; it adds the interface OrderFreeType to every
 * RenderType (Oculus's SegmentedBufferBuilder sorts ask it through SegmentedSortSkipMixin). require = 0: a mod that
 * rewrites end() keeps its sort. Carries no Minecraft code.
 */
@Mixin(value = RenderType.class, remap = false)
public abstract class RenderTypeOrderFreeMixin extends RenderStateShard implements OrderFreeType {
    /** 0 = not decided yet, 1 = order-free, 2 = keeps its sort. */
    @Unique
    private byte bons$orderFree;

    private RenderTypeOrderFreeMixin(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    @Override
    public boolean bons$orderFreeQuads() {
        byte c = this.bons$orderFree;
        if (c == 0) {
            c = SortSkip.classify((RenderType) (Object) this, RenderStateShard.f_110135_, RenderStateShard.f_110136_, RenderStateShard.f_110137_,
                    RenderStateShard.f_285585_) ? (byte) 1 : (byte) 2;
            this.bons$orderFree = c;
        }
        return c == 1;
    }

    @WrapWithCondition(method = "m_276775_", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;m_277127_(Lcom/mojang/blaze3d/vertex/VertexSorting;)V"))
    private boolean bons$sortQuads(BufferBuilder buffer, VertexSorting sorting) {
        return !(this.bons$orderFreeQuads() && SortSkip.leaveOutSort());
    }
}
