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
 * mythsandlegends_shake_scan_presence_gate (Myths and Legends 1.0.6, all rights reserved: no code carried; client).
 * ClientEvents.onCameraSetup (static) searches EffectScreenShake +-20 blocks around the player every frame; while the client
 * level holds none (vanilla_entity_class_count_layer), the search is marked so that vanilla_entity_walk_skip leaves out its
 * empty walk over the entity sections. The listener runs unchanged on the same empty list. The 0.0.8.8 build (COO's pin)
 * has the same listener in EventBusClientEvents: MythsLegacyShakeGateMixin, key
 * mythsandlegends_legacy_shake_scan_presence_gate (each key's guard names only its build's class, so in the other build that
 * key is ABSENT and Mixin never looks for its class). See ShakeGates49.
 */
@Mixin(targets = "com.ovinter.mythsandlegends.event.ClientEvents", remap = false)
public abstract class MythsShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onCameraSetup(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$shakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ShakeGates49.scan(ShakeGates49.mythsandlegends, ShakeGates49.MYTHS, level, searched, box, original);
    }
}
