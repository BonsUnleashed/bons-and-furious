package bons.furious.mixin.client_shake_gate;

import bons.furious.patch.client_shake_gate.ShakeGate;
import com.bobmowzie.mowziesmobs.client.ClientEventHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mowziesmobs_shake_scan_presence_gate (Mowzie's Mobs 1.7.3, custom licence: no code carried; client).
 * ClientEventHandler.onSetupCamera searches EntityCameraShake +-20 blocks around the player every frame; while the client
 * level holds none (vanilla_entity_class_count_layer), the search is marked so that vanilla_entity_walk_skip leaves out its
 * empty walk over the entity sections. The listener runs unchanged on the same empty list. See ShakeGate.
 */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class MowziesShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onSetupCamera(Lnet/neoforged/neoforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$shakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ShakeGate.scan(ShakeGate.mowziesmobs, ShakeGate.KEY_MOWZIESMOBS, level, searched, box, original);
    }
}
