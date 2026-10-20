package bons.furious.mixin.client_shake_gate;

import bons.furious.patch.client_shake_gate.ShakeGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.miauczel.legendary_monsters.client.event.ClientEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * legendary_monsters_shake_scan_presence_gate (Legendary Monsters 2.2.2, All Rights Reserved: no code carried; client).
 * ClientEvent.onCameraSetup searches CameraShakeEntity +-20 blocks around the player every frame, and setDynamicZoom (third
 * person) DynamicCameraZoomEntity +-20 around the camera entity. While the client level holds none of the searched class
 * (vanilla_entity_class_count_layer), the search is marked so that vanilla_entity_walk_skip leaves out its empty walk over
 * the entity sections; the listener runs unchanged on the same empty list. Nests inside legendary_monsters_camera_null_guard
 * (MixinExtras @WrapMethod on onCameraSetup, group legendarymonsters_c2): that wrapper decides whether the listener runs,
 * this one acts on the search inside it. Both listeners are static, so the wrappers are static too. See ShakeGate.
 */
@Mixin(value = ClientEvent.class, remap = false)
public abstract class LegendaryMonstersShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onCameraSetup(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$shakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ShakeGate.scan(ShakeGate.legendaryMonsters, ShakeGate.KEY_LEGENDARY_MONSTERS, level, searched, box, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "setDynamicZoom(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$zoomScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ShakeGate.scan(ShakeGate.legendaryMonsters, ShakeGate.KEY_LEGENDARY_MONSTERS, level, searched, box, original);
    }
}
