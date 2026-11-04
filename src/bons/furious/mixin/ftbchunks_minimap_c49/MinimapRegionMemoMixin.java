package bons.furious.mixin.ftbchunks_minimap_c49;

import bons.furious.patch.ftbchunks_minimap_c49.MinimapRegionMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.math.XZ;
import net.minecraft.client.gui.GuiGraphics;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ftbchunks_minimap_region_memo (FTB Chunks 2001.3.8, All Rights Reserved; client; exact variant): within one rebuild loop
 * of FTBChunksClient.renderHud (from its write of updateMinimapScheduled to its write of currentPlayerChunkX), the loop's
 * MapDimension.getRegion and MapRegion.getRenderedMapImage calls run once per region; the other tiles of the region get
 * the same objects (see MinimapRegionMemo).
 */
@Mixin(value = FTBChunksClient.class, remap = false)
public abstract class MinimapRegionMemoMixin {
    @Inject(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "FIELD", target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;updateMinimapScheduled:Z", opcode = Opcodes.PUTFIELD), require = 1, allow = 1)
    private void bons$ftbchunksMinimapC49MemoStart(GuiGraphics graphics, float tickDelta, CallbackInfo ci) {
        MinimapRegionMemo.loopStart();
    }

    @Inject(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "FIELD", target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;currentPlayerChunkX:I", opcode = Opcodes.PUTFIELD), require = 1, allow = 1)
    private void bons$ftbchunksMinimapC49MemoEnd(GuiGraphics graphics, float tickDelta, CallbackInfo ci) {
        MinimapRegionMemo.loopEnd();
    }

    @WrapOperation(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbchunks/client/map/MapDimension;getRegion(Ldev/ftb/mods/ftblibrary/math/XZ;)Ldev/ftb/mods/ftbchunks/client/map/MapRegion;"),
            require = 1, allow = 1)
    private MapRegion bons$ftbchunksMinimapC49MemoRegion(MapDimension dimension, XZ pos, Operation<MapRegion> original) {
        return MinimapRegionMemo.region(dimension, pos, original);
    }

    @WrapOperation(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbchunks/client/map/MapRegion;getRenderedMapImage()Lcom/mojang/blaze3d/platform/NativeImage;"),
            require = 1, allow = 1)
    private NativeImage bons$ftbchunksMinimapC49MemoImage(MapRegion region, Operation<NativeImage> original) {
        return MinimapRegionMemo.image(region, original);
    }
}
