package bons.furious.mixin.vanilla_resource_stack_c49;

import bons.furious.patch.vanilla_resource_stack_c49.ResourceStackMisses;
import bons.furious.patch.vanilla_resource_stack_c49.RsStackViews;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_resource_stack_miss_cache (Minecraft 1.20.1 + Forge 47.4.28; client side): a MixinExtras @WrapMethod on
 * MultiPackResourceManager.getResourceStack (m_213829_). For a namespace whose packs are all immutable archives without
 * filters (ResourceStackMisses.cacheable), an id whose stack came back empty is remembered on the namespace's
 * FallbackResourceManager and answered with a new empty stack of the same kind; everything else runs the game's own code.
 */
@Mixin(value = MultiPackResourceManager.class, remap = false)
public abstract class MultiPackResourceStackMixin {
    @Shadow
    @Final
    private Map<String, FallbackResourceManager> f_203794_;

    @WrapMethod(method = "m_213829_(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;")
    private List<Resource> bons$rsStackC49Lookup(ResourceLocation id, Operation<List<Resource>> original) {
        if (!ResourceStackMisses.enabled || id == null) return original.call(id);
        Object fallback = this.f_203794_.get(id.m_135827_());
        if (!(fallback instanceof RsStackViews.Fallback view) || !ResourceStackMisses.cacheable(view)) return original.call(id);
        Set<ResourceLocation> misses = view.bons$rsStackC49Misses();
        if (misses.contains(id)) return ResourceStackMisses.hit(original, id);
        List<Resource> found = original.call(id);
        if (found.isEmpty()) misses.add(id);
        return found;
    }
}
