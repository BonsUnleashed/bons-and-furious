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
 * oddaccessories_name_update_memo (Odd Accessories 1.1.6, All Rights Reserved; server side): two MixinExtras
 * @WrapOperation in GutsItem.updateName. String.format of the damage-reduction suffix is answered from the last call with
 * the same pattern, value bits and default FORMAT locale; stack.getHoverName() is answered without parsing when the stored
 * display.Name JSON is exactly what setHoverName(Component.literal(name)) writes for the wanted name (then the method's
 * comparison is equal and it writes nothing, as shipped). Every other case runs as shipped (OddNames). Our own logic only.
 */
@Mixin(targets = "net.oddaccessories.item.GutsItem", remap = false)
public abstract class OddGutsNameMixin {
    @WrapOperation(method = "updateName(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41786_()Lnet/minecraft/network/chat/Component;"),
            require = 1, allow = 1)
    private static Component bons$oddaccessoriesCoo49GutsHoverName(ItemStack stack, Operation<Component> original, @Local(ordinal = 1) String name) {
        return OddNames.hoverName(stack, name, OddNames.GUTS, original);
    }

    @WrapOperation(method = "updateName(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"),
            require = 1, allow = 1)
    private static String bons$oddaccessoriesCoo49GutsFormat(String pattern, Object[] args, Operation<String> original) {
        return OddNames.format(pattern, args, original);
    }
}
