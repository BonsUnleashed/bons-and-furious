package bons.furious.mixin.morerelics_coo49;

import bons.furious.patch.morerelics_coo49.MrHoist;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * morerelics_equipped_curios_hoist (More Relics 1.7.7, same bytes in 1.7.6, with Curios API 5.14.1; All Rights Reserved:
 * no code of the mod is carried; both sides; since 1.0.49). MoreRelicsUtil.actuallyGetEquippedRelics walks the curio slots
 * in its lambda with "for (i = 0; i < handler.getEquippedCurios().getSlots(); ++i) handler.getEquippedCurios()
 * .getStackInSlot(i)", building a new combined inventory wrapper over every slot type twice per slot (2N+1 per call).
 * @Inject HEAD (cancellable) of the lambda: when the handler is quiet before the walk (MrHoist.scan:
 * CuriosPresence.quiet(ICuriosItemHandler)) the walk runs once over one wrapper and the original is skipped; otherwise the
 * original runs as shipped, untouched (its wrappers stay local to its own code, as without the switch). Nothing is kept
 * beyond the call.
 */
@Mixin(targets = "com.blorb.morerelics.MoreRelicsUtil", remap = false)
public abstract class MrEquippedHoistMixin {
    private static final String LAMBDA = "lambda$actuallyGetEquippedRelics$0(Ljava/util/List;Ltop/theillusivec4/curios/api/type/capability/ICuriosItemHandler;)V";

    @Inject(method = LAMBDA, at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
    private static void bons$morerelicsCoo49Scan(List<Object> items, ICuriosItemHandler handler, CallbackInfo ci) {
        if (MrHoist.scan(items, handler)) ci.cancel();
    }
}
