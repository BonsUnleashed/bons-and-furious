package bons.furious.mixin.xaeroworldmap_gpu_c49;

import bons.furious.patch.xaeroworldmap_gpu_c49.UnpackPbo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.pool.buffer.PoolTextureDirectBufferUnit;
import xaero.map.region.texture.RegionTexture;

/**
 * xaeroworldmap_unpack_pbo_orphan (Xaero's World Map 1.47.0, client, since 1.0.49; exact variant). @Inject at the head of
 * RegionTexture.writeToUnpackPBO(int, PoolTextureDirectBufferUnit, boolean) notes the source buffer (render thread), and a
 * MixinExtras @WrapOperation on its PixelBuffers.glMapBuffer(int, int) call chooses the map call: see UnpackPbo. No World
 * Map code is carried (All Rights Reserved).
 */
@Mixin(value = RegionTexture.class, remap = false)
public abstract class WmUnpackPboMixin {
    @Inject(method = "writeToUnpackPBO(ILxaero/map/pool/buffer/PoolTextureDirectBufferUnit;Z)V", at = @At("HEAD"))
    private void bons$xaeroworldmapGpuC49UnpackSource(int pboIndex, PoolTextureDirectBufferUnit buffer, boolean retrying, CallbackInfo ci) {
        UnpackPbo.source(buffer);
    }

    @WrapOperation(method = "writeToUnpackPBO(ILxaero/map/pool/buffer/PoolTextureDirectBufferUnit;Z)V",
            at = @At(value = "INVOKE", target = "Lxaero/map/graphics/PixelBuffers;glMapBuffer(II)Ljava/nio/ByteBuffer;"))
    private ByteBuffer bons$xaeroworldmapGpuC49MapUnpack(int target, int access, Operation<ByteBuffer> original) {
        return UnpackPbo.map(target, access, original);
    }
}
