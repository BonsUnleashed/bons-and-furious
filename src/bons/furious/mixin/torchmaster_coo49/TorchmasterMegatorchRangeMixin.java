package bons.furious.mixin.torchmaster_coo49;

import bons.furious.patch.torchmaster_coo49.TorchmasterMegatorchRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.xalcon.torchmaster.common.logic.entityblocking.megatorch.MegatorchEntityBlockingLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * torchmaster_megatorch_range_first (Torchmaster 20.1.9, server; kind change, off by default (exact answers; slower only for
 * a torch in range asked about an unlisted type); no Torchmaster code carried):
 * MegatorchEntityBlockingLight.shouldBlockEntity answers with the distance test first and the entity block list only in
 * range (the same two pure tests in the other order). See TorchmasterMegatorchRange.
 */
@Mixin(value = MegatorchEntityBlockingLight.class, remap = false)
public abstract class TorchmasterMegatorchRangeMixin {
    @Shadow
    private BlockPos pos;

    @Inject(method = "shouldBlockEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private void bons$torchmasterMegatorchRangeFirst(Entity entity, BlockPos at, CallbackInfoReturnable<Boolean> cir) {
        if (TorchmasterMegatorchRange.enabled) cir.setReturnValue(TorchmasterMegatorchRange.shouldBlock(entity, at, this.pos));
    }
}
