package bons.furious.mixin.geckolib;

import com.eliotlash.mclib.math.IValue;
import java.util.List;
import java.util.Objects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import software.bernie.geckolib.core.animation.EasingType;
import software.bernie.geckolib.core.keyframe.Keyframe;

/**
 * geckolib_animation_hashes, Keyframe part (GeckoLib 4.8.4, both sides).
 *
 * Keyframe.hashCode returned Objects.hash(length, startValue, endValue, easingType, easingArgs), which allocates a
 * five-element array and boxes the length on every call (Keyframe.equals compares hash codes, so this also runs on
 * every equality check). The same value is computed directly: Objects.hash is Arrays.hashCode, which starts at 1 and
 * folds each element in as 31 * h + hash(element), and a boxed Double hashes as Double.hashCode(value).
 */
@Mixin(value = Keyframe.class, remap = false)
public abstract class KeyframeMixin {
    @Shadow
    @Final
    private double length;

    @Shadow
    @Final
    private IValue startValue;

    @Shadow
    @Final
    private IValue endValue;

    @Shadow
    @Final
    private EasingType easingType;

    @Shadow
    @Final
    private List<IValue> easingArgs;

    /**
     * @author BonsUnleashed
     * @reason Same hash as Objects.hash(...) without the varargs array and the boxed length.
     */
    @Overwrite
    public int hashCode() {
        double length = this.length;
        IValue startValue = this.startValue;
        IValue endValue = this.endValue;
        EasingType easingType = this.easingType;
        List<IValue> easingArgs = this.easingArgs;
        return ((((31 * 1 + Double.hashCode(length)) * 31 + Objects.hashCode(startValue)) * 31 + Objects.hashCode(endValue)) * 31
                + Objects.hashCode(easingType)) * 31 + Objects.hashCode(easingArgs);
    }
}
