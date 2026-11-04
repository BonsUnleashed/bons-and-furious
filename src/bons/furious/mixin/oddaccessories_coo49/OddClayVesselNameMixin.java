package bons.furious.mixin.oddaccessories_coo49;

import bons.furious.patch.oddaccessories_coo49.OddNames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oddaccessories_name_update_memo (Odd Accessories 1.1.6, All Rights Reserved; server side): MixinExtras @WrapOperation on
 * stack.getHoverName() in ClayVesselItem.updateName, answered without parsing when the stored display.Name JSON is exactly
 * what setHoverName(Component.literal(name)) writes for the wanted name (then the method's comparison is equal and it
 * writes nothing, as shipped); every other case runs as shipped (OddNames). Our own logic only.
 */
@Mixin(targets = "net.oddaccessories.item.ClayVesselItem", remap = false)
public abstract class OddClayVesselNameMixin {
    @WrapOperation(method = "updateName(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41786_()Lnet/minecraft/network/chat/Component;"),
            require = 1, allow = 1)
    private static Component bons$oddaccessoriesCoo49ClayVesselHoverName(ItemStack stack, Operation<Component> original, @Local(ordinal = 0) String name) {
        return OddNames.hoverName(stack, name, OddNames.CLAY_VESSEL, original);
    }
}
