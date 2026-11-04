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
 * goetyawaken_scan_presence_gates (Goety Awaken 1.3.9.4, LGPL-2.1 code: no code carried; client; exact). The NamelessOne
 * searches of ClientEvents.onRenderFog and onGetFogColor (+-64 blocks, every fog event) are marked EMPTY while the client
 * level holds no NamelessOne, so vanilla_entity_walk_skip leaves out their empty walks; the listeners run unchanged on the
 * same empty list. Each wrap stands alone (require 1 each). See GaGates.
 */
@Mixin(targets = "com.k1sak1.goetyawaken.client.ClientEvents", remap = false)
public abstract class GaFogGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onRenderFog(Lnet/minecraftforge/client/event/ViewportEvent$RenderFog;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$goetyawakenFogScan(ClientLevel level, Class<?> searched, AABB box, Operation<List> original) {
        // stand-aside: the search runs as shipped, called right here (GaGates.scan holds the same logic in one call)
        int kind = GaGates.markableKind(level, searched);
        return kind < 0 ? original.call(level, searched, box) : GaGates.scanMarked(kind, level, searched, box, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onGetFogColor(Lnet/minecraftforge/client/event/ViewportEvent$ComputeFogColor;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$goetyawakenFogColorScan(ClientLevel level, Class<?> searched, AABB box, Operation<List> original) {
        // stand-aside: the search runs as shipped, called right here (GaGates.scan holds the same logic in one call)
        int kind = GaGates.markableKind(level, searched);
        return kind < 0 ? original.call(level, searched, box) : GaGates.scanMarked(kind, level, searched, box, original);
    }
}
