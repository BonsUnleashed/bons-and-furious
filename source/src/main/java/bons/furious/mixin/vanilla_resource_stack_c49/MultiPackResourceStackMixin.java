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
 * vanilla_resource_stack_miss_cache (Minecraft 1.21.1, client): a MixinExtras @WrapMethod on
 * MultiPackResourceManager.getResourceStack(ResourceLocation). For a namespace whose packs are all immutable archives without
 * filters (ResourceStackMisses.cacheable), an id whose stack came back empty is remembered on the namespace's
 * FallbackResourceManager and answered with a new empty stack of the same kind; every other lookup (unknown namespaces,
 * files that exist, namespaces with folder or generated packs) runs the game's own code.
 */
@Mixin(value = MultiPackResourceManager.class, remap = false)
public abstract class MultiPackResourceStackMixin {
    @Shadow
    @Final
    private Map<String, FallbackResourceManager> namespacedManagers;

    @WrapMethod(method = "getResourceStack(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;")
    private List<Resource> bons$rsStackC49Lookup(ResourceLocation id, Operation<List<Resource>> original) {
        if (!ResourceStackMisses.enabled || id == null) return original.call(id);
        Object fallback = this.namespacedManagers.get(id.getNamespace());
        if (!(fallback instanceof RsStackViews.Fallback view) || !ResourceStackMisses.cacheable(view)) return original.call(id);
        Set<ResourceLocation> misses = view.bons$rsStackC49Misses();
        if (misses.contains(id)) return ResourceStackMisses.hit(original, id);
        List<Resource> found = original.call(id);
        if (found.isEmpty()) misses.add(id);
        return found;
    }
}
