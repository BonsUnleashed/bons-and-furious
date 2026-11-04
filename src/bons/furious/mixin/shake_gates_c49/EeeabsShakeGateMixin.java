package bons.furious.mixin.shake_gates_c49;

import bons.furious.patch.shake_gates_c49.ShakeGates49;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * eeeabsmobs_shake_scan_presence_gate (EEEAB's Mobs 0.98.1, LGPL-3.0: no code carried; client).
 * ClientEventHandler.onSetupCamera searches EntityCameraShake +-20 blocks around the player every frame; while the client
 * level holds none (vanilla_entity_class_count_layer), the search is marked so that vanilla_entity_walk_skip leaves out its
 * empty walk over the entity sections. The listener runs unchanged on the same empty list. See ShakeGates49.
 */
@Mixin(targets = "com.eeeab.eeeabsmobs.sever.handler.ClientEventHandler", remap = false)
public abstract class EeeabsShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onSetupCamera(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$shakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ShakeGates49.scan(ShakeGates49.eeeabsmobs, ShakeGates49.EEEAB, level, searched, box, original);
    }
}
