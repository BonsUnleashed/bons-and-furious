package bons.furious.mixin.modularrouters_coo49;

import bons.furious.patch.modularrouters_coo49.RouterExtruderSides;
import me.desht.modularrouters.block.tile.ModularRouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * modularrouters_extruder_side_fast_path (Modular Routers 12.1.1+mc1.20.1, server; kind change, off by default (exact; opt-in under the speed rule); no Modular
 * Routers code carried). ModularRouterBlockEntity.calculateIncomingRedstonePower(pos): while the router's extension data is
 * empty (every router that never ran an extruder) RouterExtruderSides answers it with the same six getSignal reads in the
 * same order, without the "ExtruderDist" + side key lookups (they would all answer 0); with extension data the router's
 * own code runs. getExtensionData() is called first, as the router's own first step does, so the compound is created as
 * before.
 */
@Mixin(value = ModularRouterBlockEntity.class, remap = false)
public abstract class RouterRedstoneMixin {
    @Shadow
    public abstract CompoundTag getExtensionData();

    @Inject(method = "calculateIncomingRedstonePower(Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$modularroutersExtruderSides(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (RouterExtruderSides.enabled && this.getExtensionData().m_128456_())
            cir.setReturnValue(RouterExtruderSides.power((ModularRouterBlockEntity) (Object) this, pos));
    }
}
