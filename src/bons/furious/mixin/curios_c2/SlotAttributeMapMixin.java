package bons.furious.mixin.curios_c2;

import bons.furious.patch.curios_c2.CuriosMaps;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotAttribute;

/**
 * curios_thread_safe_caches (Curios API 5.14.1+1.20.1, LGPL-3.0-or-later; both sides): SlotAttribute.getOrCreate, the
 * only accessor of SLOT_ATTRIBUTES (Curios' own HEAD inject included), runs under one lock (see {@link CuriosMaps}).
 */
@Mixin(value = SlotAttribute.class, remap = false)
public abstract class SlotAttributeMapMixin {
    @WrapMethod(method = "getOrCreate")
    private static SlotAttribute bons$lockedSlotAttribute(String id, Operation<SlotAttribute> original) {
        return CuriosMaps.slotAttribute(id, original);
    }
}
