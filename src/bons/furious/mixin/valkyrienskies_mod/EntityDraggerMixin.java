package bons.furious.mixin.valkyrienskies_mod;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.util.AcVsHotPaths;
import org.valkyrienskies.mod.common.util.EntityDragger;

/**
 * valkyrien_backoff_nan (Valkyrien Skies 2.4.11), a fix.
 *
 * EntityDragger.backOff keeps a sneaking player from walking off a ship edge by scaling the adjusted motion back to
 * its original length: transformDirection(motion).normalize().mul(length). When the adjusted motion is the zero
 * vector, normalize() divides by zero and the player's motion becomes NaN. The normalisation now leaves a zero vector
 * unchanged (AcVsHotPaths.normalizeOrZero); every non-zero vector is normalised exactly as before.
 */
@Mixin(value = EntityDragger.class, remap = false)
public abstract class EntityDraggerMixin {
    @Redirect(method = "backOff(Lnet/minecraft/world/phys/Vec3;Lorg/valkyrienskies/core/api/ships/Ship;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;normalize()Lorg/joml/Vector3d;"))
    private static Vector3d bons$normalizeOrZero(Vector3d motion) {
        return AcVsHotPaths.normalizeOrZero(motion);
    }
}
