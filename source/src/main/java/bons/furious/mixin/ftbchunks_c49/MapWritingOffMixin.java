package bons.furious.mixin.ftbchunks_c49;

import bons.furious.patch.ftbchunks_c49.MapWritingOff;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.map.MapTask;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ftbchunks_map_writing_off (FTB Chunks 2101.1.23, All Rights Reserved; client; kind change, off by default): cancellable
 * HEAD injects on FTBChunksClient.queueOrExecute(MapTask) (in 2101.1.23 only the chunk update tasks go through it) and
 * FTBChunksClient.rerender(BlockPos). What changes is described in MapWritingOff.
 */
@Mixin(value = FTBChunksClient.class, remap = false)
public abstract class MapWritingOffMixin {
    @Inject(method = "queueOrExecute(Ldev/ftb/mods/ftbchunks/client/map/MapTask;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$ftbchunksC49NoMapTask(MapTask task, CallbackInfo ci) {
        if (MapWritingOff.dropTask()) ci.cancel();
    }

    @Inject(method = "rerender(Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$ftbchunksC49NoRerender(BlockPos pos, CallbackInfo ci) {
        if (MapWritingOff.dropRerender()) ci.cancel();
    }
}
