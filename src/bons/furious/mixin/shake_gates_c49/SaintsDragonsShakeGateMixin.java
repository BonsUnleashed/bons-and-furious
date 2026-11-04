package bons.furious.mixin.shake_gates_c49;

import bons.furious.patch.shake_gates_c49.ShakeGates49;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * saintsdragons_shake_scan_presence_gate (Saint's Dragons 0.9.88, MIT code: no code carried; client).
 * ClientEventHandler.onComputeCamera searches every Mob +-64 blocks around the camera entity for ones that implement the
 * mod's ShakesScreen, every frame. While no entity of the client level implements it (vanilla_entity_class_count_layer,
 * interface entry), the search is marked so that vanilla_entity_walk_skip leaves out its walk over the entity sections: that
 * walk visits the other mobs, tests "instanceof ShakesScreen" (false for each, no side effect) and adds nothing; Forge's part
 * pass still runs (a part entity is never a Mob). The listener runs unchanged on the same empty list (no shake, no
 * Math.random draw). See ShakeGates49.
 */
@Mixin(targets = "com.leon.saintsdragons.forge.client.event.ClientEventHandler", remap = false)
public abstract class SaintsDragonsShakeGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onComputeCamera(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$shakeScan(ClientLevel level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return ShakeGates49.scanShakesScreen(level, searched, box, predicate, original);
    }
}
