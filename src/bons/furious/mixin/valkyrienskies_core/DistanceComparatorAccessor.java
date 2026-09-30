package bons.furious.mixin.valkyrienskies_core;

import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * valkyrien_collision_axes (Valkyrien Skies 2.4.11, VS core): the Kotlin "compareBy distance to the feet box"
 * comparators of collide (Dk$b) and canStep (Dk$a). EntityPolygonColliderMixin reads the feet box back from the
 * comparator it receives, so its sort handler needs no captured local (which would allocate on every call).
 */
@Mixin(targets = {"org.valkyrienskies.core.impl.shadow.Dk$a", "org.valkyrienskies.core.impl.shadow.Dk$b"}, remap = false)
public interface DistanceComparatorAccessor {
    /** The box the comparator measures from (its only field). */
    @Accessor("a")
    AABBdc bons$feetBox();
}
