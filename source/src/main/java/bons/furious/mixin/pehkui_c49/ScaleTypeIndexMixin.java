package bons.furious.mixin.pehkui_c49;

import bons.furious.patch.pehkui_c49.PehkuiTypeIndex;
import bons.furious.patch.pehkui_c49.ScaleIndexCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import virtuoel.pehkui.api.ScaleType;

/**
 * pehkui_scale_cache_all_types (Pehkui 3.8.3+1.21 for NeoForge, MIT; both sides): every ScaleType gets a dense index when it
 * is constructed (PehkuiTypeIndex). Nothing of Pehkui's is read or changed.
 */
@Mixin(value = ScaleType.class, remap = false)
public abstract class ScaleTypeIndexMixin implements PehkuiTypeIndex {
    @Unique
    private final int bons$pehkuiC49Index = ScaleIndexCache.nextIndex();

    @Override
    public int bons$pehkuiC49Index() {
        return this.bons$pehkuiC49Index;
    }
}
