package bons.furious.mixin.oculus_passes;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.21.1 on NeoForge 21.1.252, client): calls RenderType$CompositeRenderType's
 * protected final state(), the CompositeState the render type was created with (a final field). The class is package
 * private, so it is named by string. Adds no behaviour and carries no Minecraft code.
 */
@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType", remap = false)
public interface CompositeRenderTypeStateAccessor {
    @Invoker(value = "state", remap = false)
    RenderType.CompositeState bons$state();
}
