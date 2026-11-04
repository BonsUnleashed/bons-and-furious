package bons.furious.mixin.torchmaster_coo49;

import bons.furious.patch.torchmaster_coo49.TorchmasterRegistryHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.xalcon.torchmaster.common.logic.entityblocking.EntityBlockingEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * torchmaster_level_registry_handle (Torchmaster 20.1.9, server; kind change, off by default (exact answers, speed within
 * 0.91-1.17x of the original); no Torchmaster code carried):
 * EntityBlockingEventHandler.shouldBlockEntity answers from TorchmasterRegistryHandle.shouldBlock, the same capability
 * answer (through forge_capability_handle_layer's Level table) and the same registry call without the lambda, the Optional
 * and the boxed answer.
 */
@Mixin(value = EntityBlockingEventHandler.class, remap = false)
public abstract class TorchmasterRegistryHandleMixin {
    @Inject(method = "shouldBlockEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private static void bons$torchmasterLevelRegistryHandle(Entity entity, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (TorchmasterRegistryHandle.enabled) cir.setReturnValue(TorchmasterRegistryHandle.shouldBlock(entity, pos));
    }
}
