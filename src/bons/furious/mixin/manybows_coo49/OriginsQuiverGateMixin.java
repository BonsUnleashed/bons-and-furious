package bons.furious.mixin.manybows_coo49;

import bons.furious.patch.manybows_coo49.QuiverGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * manybows_origins_quiver_gate (Too Many Bows 4.1.2, both sides; the class is not in this pack's 3.6.7: string target,
 * @Pseudo, and the switch's guard is absent here): the tracked map's put in findProjectile is noted first; the remove in
 * onTrackedStackShrunk is skipped while nothing was ever tracked; the initialize() call in getInventoryPowers is skipped
 * once it has completed. See QuiverGate.
 */
@Pseudo
@Mixin(targets = "net.bandit.many_bows.compat.OriginsQuiverCompat", remap = false)
public abstract class OriginsQuiverGateMixin {
    @WrapOperation(method = "findProjectile", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1, allow = 1)
    private static Object bons$noteTracked(Map<Object, Object> map, Object stack, Object slot, Operation<Object> original) {
        QuiverGate.tracked();
        return original.call(map, stack, slot);
    }

    @WrapOperation(method = "onTrackedStackShrunk", at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1, allow = 1)
    private static Object bons$skipNeverTracked(Map<Object, Object> map, Object stack, Operation<Object> original) {
        return QuiverGate.skipRemove() ? null : original.call(map, stack);
    }

    @WrapOperation(method = "getInventoryPowers", at = @At(value = "INVOKE", target = "Lnet/bandit/many_bows/compat/OriginsQuiverCompat;initialize()V"),
            require = 1, allow = 1)
    private static void bons$initializeOnce(Operation<Void> original) {
        if (QuiverGate.skipInitialize()) return;
        original.call();
        QuiverGate.initialized();
    }
}
