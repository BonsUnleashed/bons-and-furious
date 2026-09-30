package bons.furious.mixin.geckolib;

import java.util.List;
import java.util.Objects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * geckolib_animation_hashes, RawAnimation part (GeckoLib 4.8.4, both sides).
 *
 * RawAnimation.hashCode returned Objects.hash(animationList), which allocates a one-element varargs array per call.
 * Objects.hash of one element is 31 * 1 + Objects.hashCode(element), computed here directly.
 */
@Mixin(value = RawAnimation.class, remap = false)
public abstract class RawAnimationMixin {
    @Shadow
    @Final
    private List<RawAnimation.Stage> animationList;

    /**
     * @author BonsUnleashed
     * @reason Same hash as Objects.hash(animationList) without the varargs array.
     */
    @Overwrite
    public int hashCode() {
        List<RawAnimation.Stage> animationList = this.animationList;
        return 31 * 1 + Objects.hashCode(animationList);
    }
}
