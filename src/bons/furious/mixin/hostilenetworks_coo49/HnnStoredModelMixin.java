package bons.furious.mixin.hostilenetworks_coo49;

import bons.furious.patch.hostilenetworks_coo49.HnnModelHolders;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * hostilenetworks_model_holder_memo (Hostile Neural Networks 5.3.3 DataModelItem.getStoredModel, Placebo 8.6.3; both
 * sides). A stack that names a model gets its holder from HnnModelHolders' memo of the id string (the same object the
 * registry hands out for that id, forever); other stacks run the original. No code of either mod is carried.
 */
@Mixin(targets = "dev.shadowsoffire.hostilenetworks.item.DataModelItem", remap = false)
public abstract class HnnStoredModelMixin {
    @Inject(method = "getStoredModel(Lnet/minecraft/world/item/ItemStack;)Ldev/shadowsoffire/placebo/reload/DynamicHolder;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$hostilenetworksCoo49StoredModel(ItemStack stack, CallbackInfoReturnable<DynamicHolder<?>> cir) {
        DynamicHolder<?> holder = HnnModelHolders.stored(stack);
        if (holder != null) cir.setReturnValue(holder);
    }
}
