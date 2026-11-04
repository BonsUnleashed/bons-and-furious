package bons.furious.mixin.goety_coo49;

import bons.furious.patch.goety_coo49.GoetyGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * goety_shake_scan_presence_gate (Goety 2.5.60.0, MIT: no code carried; client; exact). ClientEvents.onSetupCamera searches
 * CameraShake +-20 blocks around the player every frame; while the client level holds none (vanilla_entity_class_count_layer)
 * the search is marked so that vanilla_entity_walk_skip leaves out its empty walk. The listener runs unchanged on the same
 * empty list. See GoetyGates.
 */
@Mixin(targets = "com.Polarice3.Goety.client.events.ClientEvents", remap = false)
public abstract class GoetyShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onSetupCamera(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$goetyShakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return GoetyGates.scan(GoetyGates.shake, GoetyGates.CAMERA_SHAKE, level, searched, box, original);
    }
}
