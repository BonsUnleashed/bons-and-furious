package bons.furious.mixin.jei;

import bons.furious.patch.jei.ServerItemRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.common.platform.IPlatformHelper;
import mezz.jei.common.platform.IPlatformRegistry;
import mezz.jei.library.plugins.vanilla.ingredients.ItemStackHelper;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * jei_server_item_registry (JEI 15.59.0.212, client only).
 *
 * isIngredientOnServer(ItemStack) built a new registry wrapper (a lookup in Forge's registry manager) for every stack of
 * every recipe slot JEI indexes, and then asked it whether the item is registered. The wrapper of the active item
 * registry is now kept once that registry exists (ServerItemRegistry explains why it can never go stale); the membership
 * check itself is unchanged. With the runtime switch off, or before the item registry exists, the original call runs.
 * JEI is MIT; no JEI code is carried.
 */
@Mixin(value = ItemStackHelper.class, remap = false)
public abstract class ServerItemRegistryMixin {
    @WrapOperation(method = "isIngredientOnServer(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At(value = "INVOKE", target = "Lmezz/jei/common/platform/IPlatformHelper;getRegistry(Lnet/minecraft/resources/ResourceKey;)Lmezz/jei/common/platform/IPlatformRegistry;"))
    private IPlatformRegistry<?> bons$keptItemRegistry(IPlatformHelper platform, ResourceKey<?> key, Operation<IPlatformRegistry<?>> original) {
        if (!ServerItemRegistry.enabled) return original.call(platform, key);
        Object kept = ServerItemRegistry.kept(platform, key);
        if (kept != null) return (IPlatformRegistry<?>) kept;
        IPlatformRegistry<?> fresh = original.call(platform, key);
        ServerItemRegistry.offer(platform, key, fresh);
        return fresh;
    }
}
