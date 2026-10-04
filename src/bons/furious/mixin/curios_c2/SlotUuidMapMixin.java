package bons.furious.mixin.curios_c2;

import bons.furious.patch.curios_c2.CuriosMaps;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.mixin.CuriosImplMixinHooks;

/**
 * curios_thread_safe_caches (Curios API 5.14.1+1.20.1, LGPL-3.0-or-later; both sides): CuriosImplMixinHooks.getUuid, the
 * only accessor of UUIDS, runs under one lock (see {@link CuriosMaps}). The class sits in a package named "mixin" but is
 * an ordinary class (Curios' mixin config package is top.theillusivec4.curios.mixin.core).
 */
@Mixin(value = CuriosImplMixinHooks.class, remap = false)
public abstract class SlotUuidMapMixin {
    @WrapMethod(method = "getUuid")
    private static UUID bons$lockedSlotUuid(SlotContext slotContext, Operation<UUID> original) {
        return CuriosMaps.slotUuid(slotContext, original);
    }
}
