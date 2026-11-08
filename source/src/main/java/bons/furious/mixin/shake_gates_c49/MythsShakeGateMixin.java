package bons.furious.mixin.shake_gates_c49;

import bons.furious.patch.shake_gates_c49.MythsShakeGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mythsandlegends_shake_scan_presence_gate (Myths and Legends, all rights reserved: no code carried; 1.21.1 tested build:
 * Myths & Legends 1.1.0 for NeoForge 1.21.1; client). ClientEvents.onCameraSetup (static) searches EffectScreenShake +-20
 * blocks around the player every frame; while the client level holds none (vanilla_entity_class_count_layer), the search is
 * marked so that vanilla_entity_walk_skip leaves out its empty walk over the entity sections. The listener runs unchanged on
 * the same empty list. See MythsShakeGate.
 *
 * Ported to 1.21.1: 1.1.0's ClientEvents.onCameraSetup is the 1.0.6 listener (the same search, shake sum and angle changes;
 * the partial tick now comes from Minecraft.getTimer()).
 */
@Mixin(targets = "com.ovinter.mythsandlegends.event.ClientEvents", remap = false)
public abstract class MythsShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onCameraSetup(Lnet/neoforged/neoforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$mythsShakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return MythsShakeGate.scan(level, searched, box, original);
    }
}
