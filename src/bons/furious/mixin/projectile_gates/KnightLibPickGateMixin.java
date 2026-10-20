package bons.furious.mixin.projectile_gates;

import bons.furious.patch.projectile_gates.BoneHitboxPickGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.xylonity.knightlib.client.entity.layer.BoneHitboxPicker;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * knightlib_bone_hitbox_gate (KnightLib 2.0.3, LGPL-3.0; client; since 1.0.46).
 *
 * BoneHitboxPicker.pick(Minecraft, float) runs every frame (KnightLib's GameRenderer.pick TAIL handler): after resetting its
 * selection and computing the camera ray it walks ClientLevel.entitiesForRendering() and, for every entity but the camera,
 * asks isSpectator() and isPickable() before testing instanceof BoneHitboxHolder; only holders do further work. This
 * WrapOperation on that single entitiesForRendering() call asks the level exactly as before and then hands the loop either
 * that iterable or, when BoneHitboxPickGate says the loop can act on no entity and its calls change nothing, an empty one: the
 * loop body never runs, no holder is found, and pick ends as it does when none is found (selection stays null, the
 * crosshair target is untouched). Our own logic only; KnightLib's code is unchanged (require = 0: degrades to KnightLib's
 * behaviour). No allocation: the empty iterable is a shared constant.
 */
@Mixin(value = BoneHitboxPicker.class, remap = false)
public abstract class KnightLibPickGateMixin {
    @WrapOperation(method = "pick", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_104735_()Ljava/lang/Iterable;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Iterable bons$boneHitboxCandidates(ClientLevel level, Operation<Iterable> original) {
        return BoneHitboxPickGate.candidates(level, original.call(level));
    }
}
