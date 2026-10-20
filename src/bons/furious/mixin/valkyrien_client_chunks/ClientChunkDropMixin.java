package bons.furious.mixin.valkyrien_client_chunks;

import bons.furious.patch.valkyrien_client_chunks.WorldChunkDrop;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * valkyrien_client_chunk_drop (Valkyrien Skies 2.4.11), client only. [FIX]
 *
 * Valkyrien Skies' client.world.MixinClientChunkCache.preUnload, its cancellable HEAD handler of ClientChunkCache.drop,
 * ends with ci.cancel() for every chunk, shipyard or not, so Minecraft's own drop never runs on a Valkyrien Skies client.
 * The one cancel call is wrapped (MixinSquared @TargetHandler): it is made as before for a shipyard chunk, and skipped for
 * any other chunk, so drop goes on into Minecraft's code exactly as without Valkyrien Skies
 * (bons.furious.patch.valkyrien_client_chunks.WorldChunkDrop). Valkyrien Skies' handler itself runs unchanged.
 */
@Mixin(value = ClientChunkCache.class, priority = 1500, remap = false)
public abstract class ClientChunkDropMixin {
    @Shadow
    @Final
    ClientLevel f_104411_;

    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.client.world.MixinClientChunkCache", name = "preUnload")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;cancel()V"))
    private void bons$cancelShipChunksOnly(CallbackInfo ci, Operation<Void> original,
                                           @Local(argsOnly = true, ordinal = 0) int chunkX, @Local(argsOnly = true, ordinal = 1) int chunkZ) {
        if (WorldChunkDrop.vanillaDrop(this.f_104411_, chunkX, chunkZ)) return;   // a world chunk: Minecraft's drop runs
        original.call(ci);
    }
}
