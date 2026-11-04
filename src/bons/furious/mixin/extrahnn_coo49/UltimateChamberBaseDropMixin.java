package bons.furious.mixin.extrahnn_coo49;

import bons.furious.patch.extrahnn_coo49.BaseDropCopy;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.shadowsoffire.hostilenetworks.data.DataModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * extrahnn_ultimate_chamber_base_drop_copy (Extra Hostile Neural Networks 1.2.2 with Hostile Neural Networks 5.3.3, server;
 * kind change, a gameplay fix that is off by default): a MixinExtras @WrapOperation of the one DataModel.baseDrop() call in
 * UltimateSimChamberTileEntity.setResult hands setResult a copy of the model's base drop (BaseDropCopy.base), so its
 * setCount lands on the copy instead of Hostile Neural Networks' loaded model. Our own logic only (MIT targets; none of
 * their code is carried).
 */
@Mixin(targets = "net.lmor.extrahnn.tile.UltimateSimChamberTileEntity", remap = false)
public abstract class UltimateChamberBaseDropMixin {
    @WrapOperation(method = "setResult(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/hostilenetworks/data/DataModel;baseDrop()Lnet/minecraft/world/item/ItemStack;"),
            require = 1, allow = 1)
    private ItemStack bons$extrahnn_coo49BaseDrop(DataModel model, Operation<ItemStack> original) {
        return BaseDropCopy.base(original.call(model));
    }
}
