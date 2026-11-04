package bons.furious.mixin.oculus_passes;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.20.1 on Forge 47.4.28, client): calls RenderType$CompositeRenderType's
 * protected state() (m_173265_), the CompositeState the render type was created with (a final field). The class is package
 * private, so it is named by string. Adds no behaviour and carries no Minecraft code.
 */
@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType", remap = false)
public interface CompositeRenderTypeStateAccessor {
    @Invoker(value = "m_173265_", remap = false)
    RenderType.CompositeState bons$state();
}
