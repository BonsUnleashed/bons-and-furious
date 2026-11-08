package bons.furious.mixin.modularrouters_coo49;

import bons.furious.patch.modularrouters_coo49.RouterVacuumClamp;
import me.desht.modularrouters.logic.compiled.CompiledVacuumModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * modularrouters_vacuum_negative_split_fix (Modular Routers 13.2.7+mc1.21.1, server; kind change (gameplay fix), off by
 * default; no Modular Routers code carried). CompiledVacuumModule.handleItemMode: the amount handed to
 * stackOnGround.split(...) is clamped at 0, so a buffer above the regulator amount takes nothing instead of growing the item
 * on the ground. The handler tests the amount first: every amount of 0 or more passes through untouched. See
 * RouterVacuumClamp.
 */
@Mixin(value = CompiledVacuumModule.class, remap = false)
public abstract class VacuumClampMixin {
    @ModifyArg(method = "handleItemMode(Lme/desht/modularrouters/block/tile/ModularRouterBlockEntity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;split(I)Lnet/minecraft/world/item/ItemStack;"),
            index = 0, require = 1, allow = 1)
    private int bons$modularroutersVacuumClamp(int amount) {
        return amount >= 0 ? amount : RouterVacuumClamp.amount(amount);
    }
}
