package bons.furious.mixin.goetycataclysm_coo49;

import bons.furious.patch.goetycataclysm_coo49.GcGolems;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * goety_cataclysm_golem_types_once (Goety Cataclysm 1.20-1.9.2, all rights reserved: no code carried; both sides; a leak
 * fix). GCGolemTypes.addGolems runs on every LevelEvent.Load and adds five new GolemType enum constants each time; the
 * wrapper lets the first completed call register them as before and skips the later ones (GcGolems.addGolems).
 */
@Mixin(targets = "com.Polarice3.goety_cataclysm.init.GCGolemTypes", remap = false)
public abstract class GcGolemTypesOnceMixin {
    @WrapMethod(method = "addGolems()V")
    private static void bons$goetycataclysmGolemsOnce(Operation<Void> original) {
        GcGolems.addGolems(original);
    }
}
