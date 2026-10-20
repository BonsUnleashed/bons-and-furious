package bons.furious.mixin.client_shake_gate;

import bons.furious.patch.client_shake_gate.ShakeGate;
import com.github.alexmodguy.alexscaves.client.event.ClientEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alexscaves_shake_scan_presence_gate (Alex's Caves 2.0.2, LGPL; client). ClientEvents.computeCameraAngles searches every
 * Mob +-64 blocks around the camera entity for ones that implement ShakesScreen, every frame. While no entity of the client
 * level implements ShakesScreen (vanilla_entity_class_count_layer), the search is marked so that vanilla_entity_walk_skip
 * leaves out its walk over the entity sections: that walk visits the other mobs, tests "instanceof ShakesScreen" (false for
 * each) and adds nothing. Forge's part-entity pass still runs (a part entity is never a Mob). The listener runs unchanged on
 * the same empty list, so the camera angles and the random draws (made only while something shakes) are the same. See
 * ShakeGate. No Alex's Caves code is carried.
 */
@Mixin(value = ClientEvents.class, remap = false)
public abstract class AlexsCavesShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "computeCameraAngles(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$shakeScan(ClientLevel level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return ShakeGate.scanShakesScreen(level, searched, box, predicate, original);
    }
}
