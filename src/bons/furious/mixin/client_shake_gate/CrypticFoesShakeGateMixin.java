package bons.furious.mixin.client_shake_gate;

import bons.furious.patch.client_shake_gate.ShakeGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.min01.crypticfoes.event.ClientEventHandlerForge;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * crypticfoes_shake_scan_presence_gate (Cryptic Foes 1.0.4, All Rights Reserved: no code carried; client).
 * ClientEventHandlerForge.onComputeCameraAngles searches EntityCameraShake +-100 blocks around the player every frame (about
 * 13 x 13 chunk columns); while the client level holds none (vanilla_entity_class_count_layer), the search is marked so that
 * vanilla_entity_walk_skip leaves out its empty walk over the entity sections. The listener runs unchanged on the same empty
 * list. The listener is static, so the wrapper is too. See ShakeGate.
 */
@Mixin(value = ClientEventHandlerForge.class, remap = false)
public abstract class CrypticFoesShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onComputeCameraAngles(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$shakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ShakeGate.scan(ShakeGate.crypticfoes, ShakeGate.KEY_CRYPTICFOES, level, searched, box, original);
    }
}
