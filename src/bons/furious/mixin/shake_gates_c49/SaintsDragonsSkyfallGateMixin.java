package bons.furious.mixin.shake_gates_c49;

import bons.furious.patch.shake_gates_c49.ShakeGates49;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * saintsdragons_skyfall_scan_presence_gate (Saint's Dragons 0.9.88, MIT code: no code carried; client).
 * IgnivorusSkyfallScreenEffects.sample (FOV and flash of the Ignivorus skyfall, 2-3 calls per frame) loops over every
 * client entity (ClientLevel.entitiesForRendering) for an Ignivorus and only then computes anything. While the client level
 * holds no Ignivorus (vanilla_entity_class_count_layer: its sections hold exactly the entities the client's entity lookup
 * iterates), the loop gets an empty iterable instead, so sample returns {0, 0, 0} exactly as after the full loop. See
 * ShakeGates49.
 */
@Mixin(targets = "com.leon.saintsdragons.client.camera.IgnivorusSkyfallScreenEffects", remap = false)
public abstract class SaintsDragonsSkyfallGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "sample(F)[F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_104735_()Ljava/lang/Iterable;"),
            require = 1, allow = 1)
    private static Iterable bons$renderList(ClientLevel level, Operation<Iterable> original) {
        return ShakeGates49.renderList(level, original);
    }
}
