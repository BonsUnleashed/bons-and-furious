package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtRoot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fallentraits_root_slot_list_lazy (Fallen Traits & Relics 1.0.0 with L2 Hostility 2.5.19 and Curios; server side):
 * MixinExtras @WrapOperation on CurioCompat.getItemAccess(wearer), the first call of YggdrasilRoot.curioTick after its
 * client and alive checks: an empty list is answered when the list would change nothing this tick (the stack's unsealing
 * tag is set and a Curios walk would only read, or no slot holds a SealedItem); otherwise it is built as shipped (FtRoot).
 * Our own logic only.
 */
@Mixin(targets = "net.kayn.fallen_traits.content.item.curio.YggdrasilRoot", remap = false)
public abstract class FtRootSlotListMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Ldev/xkmc/l2hostility/compat/curios/CurioCompat;getItemAccess(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$fallentraitsCoo49RootSlotList(LivingEntity wearer, Operation<List> original, @Local(argsOnly = true) ItemStack stack) {
        return FtRoot.itemAccess(wearer, original, stack);
    }
}
