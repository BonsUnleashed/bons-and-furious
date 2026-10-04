package bons.furious.patch.distanthorizons;

import bons.pure.terrain.DensityAudit;
import bons.pure.terrain.XzCache;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.Random;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.slf4j.Logger;

/**
 * Bons and Furious switch distanthorizons_rough_surface_xz_cache (Distant Horizons 3.3.2). SRG member names.
 *
 * Distant Horizons' rough-surface generator (the client's surface-then-chunks plan; the server's plan is chunks-only)
 * finds each LOD column's surface by probing RandomState.router().finalDensity() at about 35 heights: an 8-block march
 * and a binary search, each probe a full evaluation at a SinglePointContext. The graph's flat_cache and cache_2d markers
 * compute directly there, so the two-dimensional parts (continents, erosion, ridges and the splines over them) are
 * recomputed at every probe height.
 *
 * The generator's density is mapped once (when DH builds the parameters for a level) into a copy in which each
 * flat_cache / cache_2d marker whose subtree reads only x and z (and contains only known pure types) becomes an
 * {@link XzCache}: the same value at the same x and z, remembered per thread. The copy is used only when the whole graph
 * contains only known pure types (mapping another mod's type might not reproduce it), and only after 256 probes of the
 * copy and the original at the same positions returned the same bits; otherwise DH keeps the original density.
 *
 * The parameters get a {@link Deferred} density, which makes that copy on its first evaluation instead of when the
 * parameters are built: DH builds them for every level on both sides, but only the client's surface plan ever evaluates
 * them, and the overworld copy holds tens of thousands of objects (each cached part with its per-thread slot). Every
 * evaluation still goes to the same copy, made by the same prepare() and its self-check, so the values are unchanged.
 *
 * 1.0.29 heap fix ported to 1.21.1: Deferred implements the six abstract methods DensityFunction declares on 1.21.1, the
 * same six as on 1.20.1 (compute, fillArray, mapAll, minValue, maxValue, codec; Mojang names, which NeoForge runs in
 * production), and DH 3.3.3 still reads GenParams_neoforge.density only in isNoiseSolidAtBlockPos, through compute at a
 * SinglePointContext. prepare(), flatOrCache2dInput() and selfCheck() are unchanged by the fix.
 */
public final class RoughSurfaceDensity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MARKER = "net.minecraft.world.level.levelgen.DensityFunctions$Marker";
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.dhRoughSurfaceCache=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhRoughSurfaceCache", "true"));

    private RoughSurfaceDensity() {
    }

    /** What the parameters keep: a density that runs prepare() on its first evaluation. Never throws. */
    public static DensityFunction deferred(DensityFunction original) {
        if (!enabled || original == null) return original;
        return new Deferred(original);
    }

    /**
     * Stands for prepare(original): the first call of any method makes it (once, whichever thread comes first) and every
     * call goes to it. DH only calls compute (isNoiseSolidAtBlockPos); the other methods are delegated the same way.
     */
    public static final class Deferred implements DensityFunction {
        private final DensityFunction original;
        private volatile DensityFunction resolved;

        Deferred(DensityFunction original) {
            this.original = original;
        }

        /** prepare(original), made on the first call. */
        public DensityFunction resolved() {
            DensityFunction d = resolved;
            if (d == null) {
                synchronized (this) {
                    d = resolved;
                    if (d == null) resolved = d = prepare(original);
                }
            }
            return d;
        }

        /** True once the copy (or the original, when it could not be proven exact) has been chosen. */
        public boolean isResolved() {
            return resolved != null;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return resolved().compute(context);
        }

        @Override
        public void fillArray(double[] values, DensityFunction.ContextProvider provider) {
            resolved().fillArray(values, provider);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return resolved().mapAll(visitor);
        }

        @Override
        public double minValue() {
            return resolved().minValue();
        }

        @Override
        public double maxValue() {
            return resolved().maxValue();
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return resolved().codec();
        }
    }

    /** The density DH should use: the cached copy when it is exact, otherwise the original. Never throws. */
    public static DensityFunction prepare(DensityFunction original) {
        if (!enabled || original == null) return original;
        try {
            DensityAudit.Walk audit = new DensityAudit.Walk();
            audit.dependency(original);
            if (audit.refusal != null) {
                LOGGER.info("Bons and Furious: distanthorizons_rough_surface_xz_cache keeps the original density ({})", audit.refusal);
                return original;
            }
            DensityAudit.Walk mapped = new DensityAudit.Walk();
            int[] replaced = {0};
            DensityFunction copy = original.mapAll(node -> {
                try {
                    DensityFunction inner = flatOrCache2dInput(node);
                    if (inner != null && mapped.dependency(inner) == DensityAudit.XZ && mapped.clean(inner)) {
                        replaced[0]++;
                        return new XzCache(inner);
                    }
                    return node;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            if (replaced[0] == 0) return original;
            String mismatch = selfCheck(original, copy);
            if (mismatch != null) {
                LOGGER.warn("Bons and Furious: distanthorizons_rough_surface_xz_cache keeps the original density: {}", mismatch);
                return original;
            }
            LOGGER.info("Bons and Furious: distanthorizons_rough_surface_xz_cache caches {} two-dimensional parts of the rough-surface density", replaced[0]);
            return copy;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: distanthorizons_rough_surface_xz_cache keeps the original density ({})", t.toString());
            return original;
        }
    }

    /** The wrapped function of a flat_cache or cache_2d marker, otherwise null. */
    static DensityFunction flatOrCache2dInput(DensityFunction node) throws ReflectiveOperationException {
        if (!node.getClass().getName().equals(MARKER)) return null;
        String type = null;
        DensityFunction wrapped = null;
        for (Field f : DensityAudit.fields(node.getClass())) {
            Object v = f.get(node);
            if (v instanceof Enum<?> e) type = e.name();
            else if (v instanceof DensityFunction d) wrapped = d;
        }
        return "FlatCache".equals(type) || "Cache2D".equals(type) ? wrapped : null;
    }

    /** 256 probes at DH-like positions (columns probed at several heights); the first differing bits, or null. */
    static String selfCheck(DensityFunction original, DensityFunction copy) {
        Random r = new Random(0x8f3a1L);
        for (int i = 0; i < 64; i++) {
            int x = r.nextInt(60000) - 30000, z = r.nextInt(60000) - 30000;
            for (int k = 0; k < 4; k++) {
                int y = r.nextInt(400) - 64;
                DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(x, y, z);
                double a = original.compute(ctx), b = copy.compute(ctx);
                if (Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(b)) return "probe " + x + "," + y + "," + z + ": " + a + " vs " + b;
            }
        }
        return null;
    }
}
