package bons.furious.mixin.geckolib;

import agentcraft.pure.AcGeckoPrimitive;
import bons.furious.patch.geckolib.PrimitiveEasing;
import net.minecraft.util.Mth;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;

/**
 * geckolib_primitive_easing (GeckoLib 4.8.4, both sides).
 *
 * EasingType built and evaluated its easing functions through Double2DoubleFunction.apply(Object), which boxes the
 * input and the result on every evaluation, per bone and per frame. The 29 functions GeckoLib creates itself are now
 * created as (Double2DoubleFunction & AcGeckoPrimitive), and PrimitiveEasing.evaluate calls get(double) on those; any
 * other function (a custom one from another mod) is still evaluated through apply(Object). The values are the same.
 *
 * EasingType is an interface, so this is an interface mixin. The registered easing constants are lambdas in
 * GeckoLib's static initializer (for example LINEAR = register("linear", value -> easeIn(EasingType::linear))), which
 * javac compiles to the private synthetic methods lambda$static$N; the overwrites at the end replace the bodies of the
 * ones that wrap a method reference. An interface mixin can only declare public methods, so those bodies become
 * public static methods of EasingType; nothing but GeckoLib's own static initializer calls them.
 */
@Mixin(value = EasingType.class, remap = false)
public interface EasingTypeMixin {
    @Shadow
    Double2DoubleFunction buildTransformer(Double value);

    /**
     * @author BonsUnleashed
     * @reason Evaluate the easing function through PrimitiveEasing instead of the boxed apply.
     */
    @Overwrite
    default double apply(AnimationPoint animationPoint, Double easingValue, double lerpValue) {
        if (animationPoint.currentTick() >= animationPoint.transitionLength())
            return (float) animationPoint.animationEndValue();

        return Mth.lerp(PrimitiveEasing.evaluate(buildTransformer(easingValue), lerpValue),
                animationPoint.animationStartValue(), animationPoint.animationEndValue());
    }

    // ---> Function builders: each returned function is marked and evaluates its inner function through PrimitiveEasing

    /**
     * @author BonsUnleashed
     * @reason Mark the returned function and evaluate the inner one through PrimitiveEasing.
     */
    @Overwrite
    static Double2DoubleFunction easeOut(Double2DoubleFunction function) {
        return (Double2DoubleFunction & AcGeckoPrimitive) time -> 1 - PrimitiveEasing.evaluate(function, 1 - time);
    }

    /**
     * @author BonsUnleashed
     * @reason Mark the returned function and evaluate the inner one through PrimitiveEasing.
     */
    @Overwrite
    static Double2DoubleFunction easeInOut(Double2DoubleFunction function) {
        return (Double2DoubleFunction & AcGeckoPrimitive) time -> {
            if (time < 0.5d)
                return PrimitiveEasing.evaluate(function, time * 2d) / 2d;

            return 1 - PrimitiveEasing.evaluate(function, (1 - time) * 2d) / 2d;
        };
    }

    /**
     * @author BonsUnleashed
     * @reason Mark the returned function.
     */
    @Overwrite
    static Double2DoubleFunction stepPositive(Double2DoubleFunction function) {
        return (Double2DoubleFunction & AcGeckoPrimitive) n -> n > 0 ? 1 : 0;
    }

    /**
     * @author BonsUnleashed
     * @reason Mark the returned function.
     */
    @Overwrite
    static Double2DoubleFunction stepNonNegative(Double2DoubleFunction function) {
        return (Double2DoubleFunction & AcGeckoPrimitive) n -> n >= 0 ? 1 : 0;
    }

    /**
     * @author BonsUnleashed
     * @reason Mark the returned function.
     */
    @Overwrite
    static Double2DoubleFunction elastic(Double n) {
        double n2 = n == null ? 1 : n;

        return (Double2DoubleFunction & AcGeckoPrimitive) t -> 1 - Math.pow(Math.cos(t * Math.PI / 2f), 3) * Math.cos(t * n2 * Math.PI);
    }

    /**
     * @author BonsUnleashed
     * @reason Mark the four parts and the returned function, and evaluate the parts through PrimitiveEasing.
     */
    @Overwrite
    static Double2DoubleFunction bounce(Double n) {
        double n2 = n == null ? 0.5d : n;

        Double2DoubleFunction one = (Double2DoubleFunction & AcGeckoPrimitive) x -> 121f / 16f * x * x;
        Double2DoubleFunction two = (Double2DoubleFunction & AcGeckoPrimitive) x -> 121f / 4f * n2 * Math.pow(x - 6f / 11f, 2) + 1 - n2;
        Double2DoubleFunction three = (Double2DoubleFunction & AcGeckoPrimitive) x -> 121 * n2 * n2 * Math.pow(x - 9f / 11f, 2) + 1 - n2 * n2;
        Double2DoubleFunction four = (Double2DoubleFunction & AcGeckoPrimitive) x -> 484 * n2 * n2 * n2 * Math.pow(x - 10.5f / 11f, 2) + 1 - n2 * n2 * n2;

        return (Double2DoubleFunction & AcGeckoPrimitive) t -> Math.min(
                Math.min(PrimitiveEasing.evaluate(one, t), PrimitiveEasing.evaluate(two, t)),
                Math.min(PrimitiveEasing.evaluate(three, t), PrimitiveEasing.evaluate(four, t)));
    }

    /**
     * @author BonsUnleashed
     * @reason Mark the returned function.
     */
    @Overwrite
    static Double2DoubleFunction back(Double n) {
        final double n2 = n == null ? 1.70158d : n * 1.70158d;

        return (Double2DoubleFunction & AcGeckoPrimitive) t -> t * t * ((n2 + 1) * t - n2);
    }

    /**
     * @author BonsUnleashed
     * @reason Mark the returned function.
     */
    @Overwrite
    static Double2DoubleFunction pow(double n) {
        return (Double2DoubleFunction & AcGeckoPrimitive) t -> Math.pow(t, n);
    }

    /**
     * @author BonsUnleashed
     * @reason Mark the returned function.
     */
    @Overwrite
    static Double2DoubleFunction step(Double n) {
        double n2 = n == null ? 2 : n;

        if (n2 < 2)
            throw new IllegalArgumentException("Steps must be >= 2, got: " + n2);

        final int steps = (int) n2;

        return (Double2DoubleFunction & AcGeckoPrimitive) t -> {
            double result = 0;

            if (t < 0)
                return result;

            double stepLength = (1 / (double) steps);

            if (t > (result = (steps - 1) * stepLength))
                return result;

            int testIndex;
            int leftBorderIndex = 0;
            int rightBorderIndex = steps - 1;

            while (rightBorderIndex - leftBorderIndex != 1) {
                testIndex = leftBorderIndex + (rightBorderIndex - leftBorderIndex) / 2;

                if (t >= testIndex * stepLength) {
                    leftBorderIndex = testIndex;
                } else {
                    rightBorderIndex = testIndex;
                }
            }

            return leftBorderIndex * stepLength;
        };
    }

    // ---> Registered constants: the static-initializer lambdas that wrap a plain method reference.
    //      (STEP, QUART, QUINT, BACK, ELASTIC and BOUNCE call the builders above and need no change.)

    /**
     * LINEAR / "none": value -> easeIn(EasingType::linear)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$0")
    static Double2DoubleFunction buildLinear(Double value) {
        return EasingType.easeIn((Double2DoubleFunction & AcGeckoPrimitive) EasingType::linear);
    }

    /**
     * EASE_IN_SINE: value -> easeIn(EasingType::sine)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$2")
    static Double2DoubleFunction buildEaseInSine(Double value) {
        return EasingType.easeIn((Double2DoubleFunction & AcGeckoPrimitive) EasingType::sine);
    }

    /**
     * EASE_OUT_SINE: value -> easeOut(EasingType::sine)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$3")
    static Double2DoubleFunction buildEaseOutSine(Double value) {
        return EasingType.easeOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::sine);
    }

    /**
     * EASE_IN_OUT_SINE: value -> easeInOut(EasingType::sine)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$4")
    static Double2DoubleFunction buildEaseInOutSine(Double value) {
        return EasingType.easeInOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::sine);
    }

    /**
     * EASE_IN_QUAD: value -> easeIn(EasingType::quadratic)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$5")
    static Double2DoubleFunction buildEaseInQuad(Double value) {
        return EasingType.easeIn((Double2DoubleFunction & AcGeckoPrimitive) EasingType::quadratic);
    }

    /**
     * EASE_OUT_QUAD: value -> easeOut(EasingType::quadratic)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$6")
    static Double2DoubleFunction buildEaseOutQuad(Double value) {
        return EasingType.easeOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::quadratic);
    }

    /**
     * EASE_IN_OUT_QUAD: value -> easeInOut(EasingType::quadratic)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$7")
    static Double2DoubleFunction buildEaseInOutQuad(Double value) {
        return EasingType.easeInOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::quadratic);
    }

    /**
     * EASE_IN_CUBIC: value -> easeIn(EasingType::cubic)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$8")
    static Double2DoubleFunction buildEaseInCubic(Double value) {
        return EasingType.easeIn((Double2DoubleFunction & AcGeckoPrimitive) EasingType::cubic);
    }

    /**
     * EASE_OUT_CUBIC: value -> easeOut(EasingType::cubic)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$9")
    static Double2DoubleFunction buildEaseOutCubic(Double value) {
        return EasingType.easeOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::cubic);
    }

    /**
     * EASE_IN_OUT_CUBIC: value -> easeInOut(EasingType::cubic)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$10")
    static Double2DoubleFunction buildEaseInOutCubic(Double value) {
        return EasingType.easeInOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::cubic);
    }

    /**
     * EASE_IN_EXPO: value -> easeIn(EasingType::exp)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$17")
    static Double2DoubleFunction buildEaseInExpo(Double value) {
        return EasingType.easeIn((Double2DoubleFunction & AcGeckoPrimitive) EasingType::exp);
    }

    /**
     * EASE_OUT_EXPO: value -> easeOut(EasingType::exp)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$18")
    static Double2DoubleFunction buildEaseOutExpo(Double value) {
        return EasingType.easeOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::exp);
    }

    /**
     * EASE_IN_OUT_EXPO: value -> easeInOut(EasingType::exp)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$19")
    static Double2DoubleFunction buildEaseInOutExpo(Double value) {
        return EasingType.easeInOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::exp);
    }

    /**
     * EASE_IN_CIRC: value -> easeIn(EasingType::circle)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$20")
    static Double2DoubleFunction buildEaseInCirc(Double value) {
        return EasingType.easeIn((Double2DoubleFunction & AcGeckoPrimitive) EasingType::circle);
    }

    /**
     * EASE_OUT_CIRC: value -> easeOut(EasingType::circle)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$21")
    static Double2DoubleFunction buildEaseOutCirc(Double value) {
        return EasingType.easeOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::circle);
    }

    /**
     * EASE_IN_OUT_CIRC: value -> easeInOut(EasingType::circle)
     *
     * @author BonsUnleashed
     * @reason Mark the method-reference function.
     */
    @Overwrite(aliases = "lambda$static$22")
    static Double2DoubleFunction buildEaseInOutCirc(Double value) {
        return EasingType.easeInOut((Double2DoubleFunction & AcGeckoPrimitive) EasingType::circle);
    }
}
