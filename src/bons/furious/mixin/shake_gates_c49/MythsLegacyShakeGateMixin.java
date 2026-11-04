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
 * mythsandlegends_legacy_shake_scan_presence_gate: Myths and Legends 0.0.8.8 (Collections Of Optimizations' pinned build; all
 * rights reserved: no code carried; client). EventBusClientEvents.onCameraSetup (static) is the 1.0.6 ClientEvents listener
 * under its older class name (the same bytecode fingerprint): EffectScreenShake +-20 blocks around the player, every frame;
 * the same gate. In 1.0.6 the class does not exist and this key is ABSENT. See MythsShakeGateMixin and ShakeGates49.
 */
@Mixin(targets = "com.ovinter.mythsandlegends.event.EventBusClientEvents", remap = false)
public abstract class MythsLegacyShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onCameraSetup(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$shakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ShakeGates49.scan(ShakeGates49.mythsandlegendsLegacy, ShakeGates49.MYTHS_LEGACY, level, searched, box, original);
    }
}
