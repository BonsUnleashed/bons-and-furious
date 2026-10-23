package bons.furious.patch.noise;
/** Set once by the mixin plugin after inspecting the actual transformed method. */
public final class NoiseCompatibility {
    public static volatile boolean allowed;
    public static boolean standAside;
    private NoiseCompatibility() {}
}
