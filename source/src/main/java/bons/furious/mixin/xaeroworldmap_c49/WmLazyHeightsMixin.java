package bons.furious.mixin.xaeroworldmap_c49;

import bons.furious.patch.xaeroworldmap_c49.LazyHeights;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.misc.ConsistentBitArray;
import xaero.map.region.texture.RegionTexture;

/**
 * xaeroworldmap_lazy_tile_heights (Xaero's World Map 1.47.0 for NeoForge 1.21.1, client, since 1.0.49; exact). MixinExtras
 * @WrapOperation on the two new ConsistentBitArray(13, 4096) of the RegionTexture constructor (one shared all-zero table),
 * @ModifyExpressionValue on the field read in front of the set call of putHeight(II) / putTopHeight(II) (the texture's own
 * table first; no allocation on the path) and @WrapOperation on readCacheData's three setData calls on the two fields
 * (ordinal 1 heightValues, ordinals 3 and 4 topHeightValues; 0 and 2 are the old-format thread-local table: the texture's
 * own table built from the read data). removeHeight / removeTopHeight are not touched: on the shared table they write 0
 * over 0. Why identical: see LazyHeights. No World Map code is carried (All Rights Reserved).
 */
@Mixin(value = RegionTexture.class, remap = false)
public abstract class WmLazyHeightsMixin {
    @Shadow
    protected ConsistentBitArray heightValues;

    @Shadow
    protected ConsistentBitArray topHeightValues;

    @Unique
    private ConsistentBitArray bons$xaeroworldmapC49OwnHeights() {
        synchronized (this) {
            if (this.heightValues == LazyHeights.SHARED) this.heightValues = LazyHeights.fresh();
            return this.heightValues;
        }
    }

    @Unique
    private ConsistentBitArray bons$xaeroworldmapC49OwnTopHeights() {
        synchronized (this) {
            if (this.topHeightValues == LazyHeights.SHARED) this.topHeightValues = LazyHeights.fresh();
            return this.topHeightValues;
        }
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(II)Lxaero/map/misc/ConsistentBitArray;"))
    private ConsistentBitArray bons$xaeroworldmapC49SharedTable(int bits, int entries, Operation<ConsistentBitArray> original) {
        return LazyHeights.create(bits, entries, original);
    }

    @ModifyExpressionValue(method = "putHeight(II)V", at = @At(value = "FIELD", target = "Lxaero/map/region/texture/RegionTexture;heightValues:Lxaero/map/misc/ConsistentBitArray;", opcode = 180))
    private ConsistentBitArray bons$xaeroworldmapC49PutHeight(ConsistentBitArray table) {
        return table == LazyHeights.SHARED ? bons$xaeroworldmapC49OwnHeights() : table;
    }

    @ModifyExpressionValue(method = "putTopHeight(II)V", at = @At(value = "FIELD", target = "Lxaero/map/region/texture/RegionTexture;topHeightValues:Lxaero/map/misc/ConsistentBitArray;", opcode = 180))
    private ConsistentBitArray bons$xaeroworldmapC49PutTopHeight(ConsistentBitArray table) {
        return table == LazyHeights.SHARED ? bons$xaeroworldmapC49OwnTopHeights() : table;
    }

    @WrapOperation(method = "readCacheData", at = @At(value = "INVOKE", target = "Lxaero/map/misc/ConsistentBitArray;setData([J)V", ordinal = 1))
    private void bons$xaeroworldmapC49ReadHeights(ConsistentBitArray table, long[] data, Operation<Void> original) {
        if (table != LazyHeights.SHARED) {
            original.call(table, data);
            return;
        }
        synchronized (this) {
            if (this.heightValues == LazyHeights.SHARED) this.heightValues = LazyHeights.fresh(data);
            else original.call(this.heightValues, data);
        }
    }

    @WrapOperation(method = "readCacheData", at = {@At(value = "INVOKE", target = "Lxaero/map/misc/ConsistentBitArray;setData([J)V", ordinal = 3),
            @At(value = "INVOKE", target = "Lxaero/map/misc/ConsistentBitArray;setData([J)V", ordinal = 4)})
    private void bons$xaeroworldmapC49ReadTopHeights(ConsistentBitArray table, long[] data, Operation<Void> original) {
        if (table != LazyHeights.SHARED) {
            original.call(table, data);
            return;
        }
        synchronized (this) {
            if (this.topHeightValues == LazyHeights.SHARED) this.topHeightValues = LazyHeights.fresh(data);
            else original.call(this.topHeightValues, data);
        }
    }
}
