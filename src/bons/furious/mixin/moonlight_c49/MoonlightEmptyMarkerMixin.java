package bons.furious.mixin.moonlight_c49;

import bons.furious.patch.moonlight_c49.EmptyMarkers;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * moonlight_empty_marker_refresh_skip (Moonlight Lib 1.20-2.16.19 and 2.16.37, both sides): a MixinSquared @TargetHandler
 * HEAD injection into Moonlight's MapDataMixin.checkCustomDeco handler (merged into MapItemSavedData at the end of
 * checkBanners, m_77930_) that returns at once while the map holds no custom block marker (EmptyMarkers explains why nothing
 * observable changes). Our own logic only. Priority 1500: after Moonlight's mixin.
 */
@Mixin(value = MapItemSavedData.class, priority = 1500, remap = false)
public abstract class MoonlightEmptyMarkerMixin {
    @TargetHandler(mixin = "net.mehvahdjukaar.moonlight.core.mixins.MapDataMixin", name = "checkCustomDeco")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$skipEmptyMarkerRefresh(BlockGetter level, int x, int z, CallbackInfo theirs, CallbackInfo ci) {
        if (EmptyMarkers.nothingToRefresh(this)) ci.cancel();
    }
}
