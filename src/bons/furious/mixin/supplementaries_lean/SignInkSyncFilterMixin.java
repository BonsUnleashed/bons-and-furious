package bons.furious.mixin.supplementaries_lean;

import bons.furious.patch.supplementaries_lean.SignInkSync;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * supplementaries_sign_ink_sync_filter (Supplementaries 1.20-3.1.41, server side incl. the integrated server): a
 * MixinSquared @TargetHandler injection at the HEAD of Supplementaries' forge ChunkHolderMixin.sendBlockEntityCaps
 * handler, merged into ClientboundLevelChunkPacketData.BlockEntityInfo.create (m_195691_). When SignInkSync.skip says the
 * handler's queued task would find no antique ink capability on that block entity (not a sign, capabilities valid, the
 * same getCapability answer empty, on the server thread), the handler returns before it builds the lambda and the
 * TickTask; otherwise it runs unchanged. Our own logic only; no Supplementaries code is carried. Priority 1500 (after
 * Supplementaries' mixin), require = 0, expect = 0: without that handler nothing is injected. The CallbackInfo is the only
 * allocation (scalar-replaced once inlined).
 */
@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo", priority = 1500, remap = false)
public abstract class SignInkSyncFilterMixin {
    @TargetHandler(mixin = "net.mehvahdjukaar.supplementaries.mixins.forge.ChunkHolderMixin", name = "sendBlockEntityCaps")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void bons$skipNoInkTask(BlockEntity te, CallbackInfoReturnable<?> theirCir, CallbackInfo ci) {
        if (SignInkSync.skip(te)) ci.cancel();
    }
}
