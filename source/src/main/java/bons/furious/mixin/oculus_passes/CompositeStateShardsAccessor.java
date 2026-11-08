package bons.furious.mixin.oculus_passes;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.21.1 on NeoForge 21.1.252, client): read access to
 * RenderType$CompositeState's package-private state list (states), the constructor's ImmutableList of its 13 shards in
 * fixed order (texture, shader, transparency, depth test, cull, lightmap, overlay, layering, output, texturing, write mask,
 * colour logic, line). The list (of the public base type) is read instead of the private typed fields; the guard
 * fingerprints the constructor that builds it. Adds no behaviour and carries no Minecraft code.
 */
@Mixin(value = RenderType.CompositeState.class, remap = false)
public interface CompositeStateShardsAccessor {
    @Accessor(value = "states", remap = false)
    ImmutableList<RenderStateShard> bons$shards();
}
