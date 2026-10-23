package bons.furious.mixin.client_shake_gate;

import bons.furious.patch.client_shake_gate.ShakeGate;
import com.github.L_Ender.cataclysm.client.event.ClientEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_shake_scan_presence_gate (L_Ender's Cataclysm 3.16, licence unresolved in its mods.toml: no code carried;
 * client). ClientEvent.onCameraSetup searches ScreenShake_Entity +-20 blocks around the player every frame; while the client
 * level holds none (vanilla_entity_class_count_layer), the search is marked so that vanilla_entity_walk_skip leaves out its
 * empty walk over the entity sections. The listener runs unchanged on the same empty list. See ShakeGate.
 */
@Mixin(value = ClientEvent.class, remap = false)
public abstract class CataclysmShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onCameraSetup(Lnet/neoforged/neoforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$shakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ShakeGate.scan(ShakeGate.cataclysm, ShakeGate.KEY_CATACLYSM, level, searched, box, original);
    }
}
