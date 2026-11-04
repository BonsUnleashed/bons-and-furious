package bons.furious.mixin.curios_presence;

import bons.furious.patch.curios_presence.CuriosInventoryLookup;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.mixin.CuriosImplMixinHooks;

/**
 * curios_inventory_lookup_layer (Curios API 5.14.1+1.20.1, LGPL-3.0; both sides; since 1.0.49).
 *
 * CuriosImplMixinHooks.getCuriosInventory(entity) is where Curios' own mixin sends every CuriosApi.getCuriosInventory call.
 * It is wrapped as a whole (MixinExtras @WrapMethod, no CallbackInfo): CuriosInventoryLookup answers from the entity's kept
 * record when that is the same answer (see that class), otherwise the original runs unchanged and the answer is learned.
 * No Curios code is carried.
 */
@Mixin(value = CuriosImplMixinHooks.class, remap = false)
public abstract class CuriosImplHooksLookupMixin {
    @WrapMethod(method = "getCuriosInventory")
    private static LazyOptional<ICuriosItemHandler> bons$keptInventory(LivingEntity entity, Operation<LazyOptional<ICuriosItemHandler>> original) {
        return CuriosInventoryLookup.inventory(entity, original);
    }
}
