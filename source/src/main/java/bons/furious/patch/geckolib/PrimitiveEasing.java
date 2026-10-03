package bons.furious.patch.geckolib;

import agentcraft.pure.AcGeckoPrimitive;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;

/**
 * Evaluates a GeckoLib easing function (geckolib_primitive_easing).
 *
 * GeckoLib's own easing lambdas are marked with AcGeckoPrimitive by EasingTypeMixin and are called through the
 * primitive get(double). Any other function, such as one supplied by another mod, is called through the boxed
 * apply(Object) exactly as GeckoLib did, so a subclass that only overrides apply keeps working.
 *
 * In 1.0.19 this was the static method EasingType.ac$primitiveApply added by the coremod. Mixin cannot add a static
 * method to an interface (an interface mixin may only declare public methods, and a public static method is only
 * accepted as an @Overwrite of an existing one), so it lives here; the body is unchanged.
 */
public final class PrimitiveEasing {
    private PrimitiveEasing() {}

    public static double evaluate(Double2DoubleFunction function, double value) {
        if (function instanceof AcGeckoPrimitive) {
            return function.get(value);
        }
        return function.apply(value);
    }
}
