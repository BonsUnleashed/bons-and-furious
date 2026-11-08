package bons.furious.mixin.pehkui_c49;

import bons.furious.patch.pehkui_c49.PehkuiTypeIndex;
import bons.furious.patch.pehkui_c49.ScaleIndexCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import virtuoel.pehkui.api.ScaleData;

/**
 * pehkui_scale_cache_all_types (Pehkui 3.8.3+1.21 for NeoForge, MIT; both sides): a MixinExtras @WrapOperation of the first
 * Map.get in Pehkui's pehkui_getScaleData (merged into Entity by Pehkui's ThreadSafeScaledEntityMixin, or
 * ThreadUnsafeScaledEntityMixin with -Dpehkui.disableThreadSafety), the lookup Pehkui makes for every type its nine-slot
 * cache does not hold: answered from our per-entity slot array when the slot is filled, else Pehkui's map answers and a
 * non-null answer fills the slot. Pehkui's nine cached types return before that call and run unchanged. Priority 1100:
 * after Pehkui's mixins (priority 1000), whose merged method it targets. require = 0: without that method (another mod
 * cancelled Pehkui's mixin) nothing applies. See ScaleIndexCache for why the answers are identical.
 */
@Mixin(value = Entity.class, priority = 1100, remap = false)
public abstract class EntityScaleIndexMixin {
    /** Index = the type's PehkuiTypeIndex; each filled slot holds the object Pehkui's map returned for that type. */
    @Unique
    private volatile ScaleData[] bons$pehkuiC49Slots;

    @WrapOperation(method = "pehkui_getScaleData(Lvirtuoel/pehkui/api/ScaleType;)Lvirtuoel/pehkui/api/ScaleData;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0),
            require = 0, expect = 0, allow = 1)
    private Object bons$pehkuiC49MapGet(Map<?, ?> scales, Object type, Operation<Object> original) {
        if (!ScaleIndexCache.enabled || !(type instanceof PehkuiTypeIndex t)) return original.call(scales, type);
        int i = t.bons$pehkuiC49Index();
        ScaleData[] slots = this.bons$pehkuiC49Slots;
        if (slots != null && i < slots.length) {
            ScaleData d = slots[i];
            if (d != null) {
                if (ScaleIndexCache.SHADOW) ScaleIndexCache.shadow(d, scales, type);
                return d;
            }
        }
        Object v = original.call(scales, type);
        if (v instanceof ScaleData d) this.bons$pehkuiC49Slots = ScaleIndexCache.store(slots, i, d);
        return v;
    }
}
