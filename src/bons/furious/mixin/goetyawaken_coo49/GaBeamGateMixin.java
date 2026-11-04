package bons.furious.mixin.goetyawaken_coo49;

import bons.furious.patch.goetyawaken_coo49.GaGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * goetyawaken_scan_presence_gates (Goety Awaken 1.3.9.4, LGPL-2.1 code: no code carried; client; exact). The SorcererServant
 * and BoundSorcerer searches of ClientEvents.onRenderLevelStage (AFTER_TRANSLUCENT_BLOCKS, +-64 blocks, every frame) are
 * marked EMPTY while the client level holds none of the searched class, so vanilla_entity_walk_skip leaves out their empty
 * walks; the beam loops run unchanged on the same empty lists. require 1, allow 2: each of the two calls stands alone. See
 * GaGates.
 */
@Mixin(targets = "com.k1sak1.goetyawaken.client.ClientEvents", remap = false)
public abstract class GaBeamGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onRenderLevelStage(Lnet/minecraftforge/client/event/RenderLevelStageEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 2)
    private static List bons$goetyawakenBeamScan(ClientLevel level, Class<?> searched, AABB box, Operation<List> original) {
        // stand-aside: the search runs as shipped, called right here (GaGates.scan holds the same logic in one call)
        int kind = GaGates.markableKind(level, searched);
        return kind < 0 ? original.call(level, searched, box) : GaGates.scanMarked(kind, level, searched, box, original);
    }
}
