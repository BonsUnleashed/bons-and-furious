package bons.furious.mixin.biomancy_coo49;

import bons.furious.patch.biomancy_coo49.SpatialMemo;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * biomancy_spatial_query_memo, Biomancy 2.8.19.0 (not in this pack; string target, @Pseudo, offered by BiomancyPlugin only
 * when the guard matched 2.8.19.0; server): SpatialShapeStorage.getInstance answers from SpatialMemo when it holds this
 * server's database (HEAD), keeps what a lookup returned (RETURN); close() drops the kept database (TAIL).
 */
@Pseudo
@Mixin(targets = "com.github.elenterius.biomancy.world.spatial.SpatialShapeStorage", remap = false)
public abstract class SpatialStorageMemoMixin {
    @Inject(method = "getInstance", at = @At("HEAD"), cancellable = true)
    private static void bons$keptStorage(ServerLevel level, CallbackInfoReturnable<Object> cir) {
        Object kept = SpatialMemo.storage(level);
        if (kept != null) cir.setReturnValue(kept);
    }

    @Inject(method = "getInstance", at = @At("RETURN"))
    private static void bons$keepStorage(ServerLevel level, CallbackInfoReturnable<Object> cir) {
        SpatialMemo.remember(level, cir.getReturnValue());
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void bons$forgetStorage(CallbackInfo ci) {
        SpatialMemo.forget(this);
    }
}
