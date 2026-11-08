package bons.furious.mixin.oculus_passes;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.21.1 on NeoForge 21.1.252, client): read access to
 * RenderStateShard$WriteMaskStateShard's private writeDepth flag (the constructor's second argument; its setup calls
 * RenderSystem.depthMask(false) when it is false). Adds no behaviour and carries no Minecraft code.
 */
@Mixin(value = RenderStateShard.WriteMaskStateShard.class, remap = false)
public interface WriteMaskDepthAccessor {
    @Accessor(value = "writeDepth", remap = false)
    boolean bons$writeDepth();
}
