package bons.furious.mixin.oculus_passes;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_order_independent_sort_skip (Minecraft 1.20.1 on Forge 47.4.28, client): read access to RenderType$CompositeState's
 * state list (f_110592_), the constructor's ImmutableList of its 13 shards in fixed order (texture, shader, transparency,
 * depth test, cull, lightmap, overlay, layering, output, texturing, write mask, colour logic, line). The shard classes are
 * protected inside RenderStateShard, so the list (of the public base type) is read instead of the typed fields; the guard
 * fingerprints the constructor that builds it. Adds no behaviour and carries no Minecraft code.
 */
@Mixin(value = RenderType.CompositeState.class, remap = false)
public interface CompositeStateShardsAccessor {
    @Accessor(value = "f_110592_", remap = false)
    ImmutableList<RenderStateShard> bons$shards();
}
