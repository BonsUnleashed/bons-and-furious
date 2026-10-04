package bons.furious.mixin.curios;

import bons.furious.patch.curios.CuriosSlotlessTick;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.event.CuriosEventHandler;

/**
 * curios_slotless_tick_skip (Curios API 5.14.1+1.20.1, LGPL-3.0, both sides).
 *
 * CuriosEventHandler.tick (every living entity, every tick) does the Player menu check and then
 * CuriosApi.getCuriosInventory(entity).ifPresent(...). The getCuriosInventory call is redirected to
 * CuriosSlotlessTick.inventory, which answers the same LazyOptional without the capability walk for an entity whose type
 * has no Curios slots and on which the lookup is already known to be empty (see that class for why the answer is
 * identical); everything else in tick, the Player check first, stays Curios' own. A redirect allocates nothing here.
 */
@Mixin(value = CuriosEventHandler.class, remap = false)
public abstract class CuriosEventHandlerSlotlessMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Ltop/theillusivec4/curios/api/CuriosApi;getCuriosInventory(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private LazyOptional<ICuriosItemHandler> bons$slotlessInventory(LivingEntity entity) {
        return CuriosSlotlessTick.inventory(entity);
    }
}
