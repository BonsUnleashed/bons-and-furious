package bons.furious.mixin.ribbits_c2;

import bons.furious.patch.ribbits_c2.PerformerGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yungnickyoung.minecraft.ribbits.player.PlayerInstrumentTracker;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ribbits_performer_absent_guard (Ribbits 3.0.2, LGPL-3.0; both sides, acts on the server; a fix): the audience lookup in
 * removePerformer answers an empty set for a performer that is not in the map (see {@link PerformerGuard}).
 */
@Mixin(value = PlayerInstrumentTracker.class, remap = false)
public abstract class PerformerAbsentGuardMixin {
    @WrapOperation(method = "removePerformer", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ConcurrentHashMap;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$absentPerformer(ConcurrentHashMap<?, ?> map, Object performer, Operation<Object> original) {
        return PerformerGuard.audience(map, performer, original);
    }
}
