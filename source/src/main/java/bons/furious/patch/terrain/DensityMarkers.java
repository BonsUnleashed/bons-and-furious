package bons.furious.patch.terrain;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Creates DensityFunctions.Marker records for {@code bons.furious.mixin.terrain.MarkerOrMarkedReuseMixin}.
 *
 * Marker (and its Type enum) are non-public nested types of DensityFunctions, so Java source outside
 * net.minecraft.world.level.levelgen cannot write {@code new Marker(type, input)}. The record's canonical constructor is
 * looked up once and called through a method handle; the result is the same record the vanilla expression creates.
 */
public final class DensityMarkers {
    private static final MethodHandle NEW_MARKER;

    static {
        try {
            ClassLoader loader = DensityFunction.class.getClassLoader();
            Class<?> marker = Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$Marker", false, loader);
            Class<?> type = Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$Marker$Type", false, loader);
            Constructor<?> constructor = marker.getDeclaredConstructor(type, DensityFunction.class);
            constructor.setAccessible(true);
            NEW_MARKER = MethodHandles.lookup().unreflectConstructor(constructor)
                    .asType(MethodType.methodType(DensityFunction.class, Object.class, DensityFunction.class));
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DensityMarkers() {
    }

    /** {@code new DensityFunctions.Marker(type, input)}; {@code type} is the Marker.Type returned by MarkerOrMarked.type(). */
    public static DensityFunction create(Object type, DensityFunction input) {
        try {
            return (DensityFunction) NEW_MARKER.invokeExact(type, input);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);   // unreachable: the record constructor throws no checked exception
        }
    }
}
