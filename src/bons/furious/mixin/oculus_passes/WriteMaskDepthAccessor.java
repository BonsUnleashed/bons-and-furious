package bons.furious.mixin.oculus_passes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.20.1, client): read access to RenderStateShard$WriteMaskStateShard's
 * writeDepth flag (f_110357_, the constructor's second argument; its setup calls RenderSystem.depthMask(false) when it is
 * false). The class is protected inside RenderStateShard, so it is named by string. Adds no behaviour and carries no
 * Minecraft code.
 */
@Mixin(targets = "net.minecraft.client.renderer.RenderStateShard$WriteMaskStateShard", remap = false)
public interface WriteMaskDepthAccessor {
    @Accessor(value = "f_110357_", remap = false)
    boolean bons$writeDepth();
}
