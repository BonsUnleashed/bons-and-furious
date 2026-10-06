package bons.furious.patch.climate_columns;

import bons.pure.terrain.DensityAudit;
import bons.pure.terrain.XzCache;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_climate_sample_xz_parts (Minecraft 1.21.1 world generation, NeoForge; Mojang names).
 *
 * Climate.Sampler.sample (sample) evaluates six density functions (temperature, vegetation, continentalness,
 * erosion, depth, weirdness) at one position. The sampler a RandomState keeps for lookups outside chunk generation
 * (structure biome checks, Distant Horizons' level-of-detail generation, ModernFix's surface biome lookup, /locate, the
 * spawn search) is built from the noise router with every flat_cache / cache_2d marker removed (RandomState.<init>), so
 * nothing is remembered between the six functions or between samples: in the vanilla overworld one sample evaluates
 * the two shift noises eight times each and continentalness, erosion and ridges twice (once more inside depth's offset
 * spline), and a sample one block higher in the same column repeats all of it.
 *
 * For such a sampler the six functions are mapped once from the same router into copies that are identical except that
 * each flat_cache / cache_2d marker whose input reads only x and z (DensityAudit) becomes an {@link XzCache}: the same
 * value at the same x and z, remembered per thread, shared by every function that reads it. sample() then evaluates the
 * copies. Used only when every node of the router's six functions and of the sampler's own six functions is a known
 * pure type, when the copies differ from the plain mapping only by those caches, and after 256 positions (64 columns
 * at four heights) returned the same bits from the copies and the sampler's own functions; a sampler that fails any of
 * this keeps its original evaluation. The first 64 real samples of every sampler are also computed both ways and
 * compared: a difference switches that sampler back to the original for good.
 */
public final class ClimateParts {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MARKER = "net.minecraft.world.level.levelgen.DensityFunctions$Marker";
    private static final String HOLDER = "net.minecraft.world.level.levelgen.DensityFunctions$HolderHolder";
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.climateParts=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.climateParts", "true"));
    static final boolean METRICS = Boolean.getBoolean("bons_and_furious.climateParts.metrics");
    /** Samples served from the copies / compared with the original (only with -Dbons_and_furious.climateParts.metrics=true). */
    public static final LongAdder SERVED = new LongAdder(), COMPARED = new LongAdder();
    static final int CANARY = 64;
    private static final Object REFUSED = new Object();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private ClimateParts() {
    }

    /** A qualified sampler: its own six functions (by position in sample()) and their cached copies. */
    static final class Prepared {
        final DensityFunction[] own, copies;

        Prepared(DensityFunction[] own, DensityFunction[] copies) {
            this.own = own;
            this.copies = copies;
        }
    }

    /** Implemented by Climate.Sampler through the mixin. */
    public interface PartsSampler {
        /** The noise router of the RandomState that built this sampler, or null. */
        Object bons$partsRouter();

        void bons$partsRouter(Object router);

        /** Null = not prepared yet, a Prepared = qualified, otherwise refused. */
        Object bons$parts();

        void bons$parts(Object parts);

        /** Samples still to be compared with the original evaluation. */
        int bons$canary();

        void bons$canary(int left);
    }

    /** End of RandomState's constructor: the router its climate sampler was made from. */
    public static void attach(Object sampler, Object router) {
        if (sampler instanceof PartsSampler s && router instanceof NoiseRouter && s.bons$partsRouter() == null) {
            s.bons$partsRouter(router);
            s.bons$canary(CANARY);
        }
    }

    /**
     * One of sample()'s six evaluations: index = its position in sample() (0 temperature ... 5 weirdness), fn = the
     * function sample() was about to evaluate (also evaluated, and compared, while the canary runs). Returns the value
     * sample() uses.
     */
    public static double compute(Object sampler, int index, DensityFunction fn, DensityFunction.FunctionContext context) {
        if (!enabled || index < 0 || !(sampler instanceof PartsSampler s)) return fn.compute(context);
        Object parts = s.bons$parts();
        if (parts == null) {
            // a sampler no RandomState made (every chunk's cached sampler, other mods' samplers) is refused without a lock:
            // its router is null from construction on, because attach() runs inside RandomState's constructor
            if (s.bons$partsRouter() == null) {
                s.bons$parts(REFUSED);
                return fn.compute(context);
            }
            parts = prepare(s, sampler);
        }
        if (!(parts instanceof Prepared p)) return fn.compute(context);
        // the call at this position must evaluate the function sample() evaluates there in the guarded method; another
        // mod that rewrote the method body (an @Overwrite with other calls) gets the original evaluation
        if (p.own[index] != fn) return fn.compute(context);
        double v = p.copies[index].compute(context);
        if (s.bons$canary() > 0) {
            double o = fn.compute(context);
            if (METRICS) COMPARED.increment();
            if (Double.doubleToRawLongBits(o) != Double.doubleToRawLongBits(v)) {
                refuse(s, "a sample differed from the original evaluation (function " + index + " at " + context.blockX() + ","
                        + context.blockY() + "," + context.blockZ() + ": " + o + " vs " + v + ")", true);
                return o;
            }
            if (index == 5) s.bons$canary(s.bons$canary() - 1);
        } else if (METRICS && index == 5) {
            SERVED.increment();
        }
        return v;
    }

    private static void refuse(PartsSampler s, String why, boolean warn) {
        s.bons$parts(REFUSED);
        if (!LOGGED.add(why)) return;
        if (warn) LOGGER.warn("Bons and Furious: vanilla_climate_sample_xz_parts switched off for one climate sampler: {}", why);
        else LOGGER.info("Bons and Furious: vanilla_climate_sample_xz_parts keeps the original evaluation for one climate sampler ({})", why);
    }

    /** Builds (once per sampler, under that sampler's lock) the six copies, or marks the sampler refused. */
    static Object prepare(PartsSampler s, Object sampler) {
        synchronized (sampler) {
            Object parts = s.bons$parts();
            if (parts != null) return parts;
            return build(s, sampler);
        }
    }

    private static Object build(PartsSampler s, Object sampler) {
        try {
            Object r = s.bons$partsRouter();
            if (!(r instanceof NoiseRouter router)) {
                s.bons$parts(REFUSED);       // not made by a RandomState (another mod's sampler): nothing is known about it
                return REFUSED;
            }
            DensityFunction[] own = samplerFunctions(sampler);
            DensityFunction[] fromRouter = {router.temperature(), router.vegetation(), router.continents(), router.erosion(), router.depth(),
                    router.ridges()};
            DensityAudit.Walk audit = new DensityAudit.Walk();
            for (DensityFunction f : fromRouter) audit.dependency(f);
            for (DensityFunction f : own) audit.dependency(f);
            if (audit.refusal != null) {
                refuse(s, audit.refusal, false);
                return REFUSED;
            }
            // the plain mapping (markers and holders removed, as RandomState does) and the cached one, with one shared
            // memo each so a part read by several functions becomes one node (one remembered value)
            Map<DensityFunction, DensityFunction> plainMemo = new HashMap<>(), cachedMemo = new HashMap<>();
            DensityAudit.Walk mapped = new DensityAudit.Walk();
            int[] cached = {0};
            DensityFunction[] plain = new DensityFunction[6], copies = new DensityFunction[6];
            for (int i = 0; i < 6; i++) {
                plain[i] = fromRouter[i].mapAll(node -> plainMemo.computeIfAbsent(node, n -> strip(n, null, null)));
                copies[i] = fromRouter[i].mapAll(node -> cachedMemo.computeIfAbsent(node, n -> strip(n, mapped, cached)));
            }
            if (cached[0] == 0) {
                refuse(s, "no two-dimensional cache marker in its functions", false);
                return REFUSED;
            }
            String mismatch = selfCheck(own, plain, copies);
            if (mismatch != null) {
                refuse(s, mismatch, true);
                return REFUSED;
            }
            Prepared prepared = new Prepared(own, copies);
            s.bons$parts(prepared);
            if (LOGGED.add("ok|" + cached[0]))
                LOGGER.info("Bons and Furious: vanilla_climate_sample_xz_parts remembers {} two-dimensional parts of a climate sampler per column", cached[0]);
            return prepared;
        } catch (Throwable t) {
            refuse(s, "could not be prepared: " + t, false);
            return REFUSED;
        }
    }

    /** RandomState's sampler visitor (holders and markers removed); with a walk, eligible 2-D markers become caches. */
    private static DensityFunction strip(DensityFunction node, DensityAudit.Walk mapped, int[] cached) {
        try {
            String cn = node.getClass().getName();
            if (cn.equals(HOLDER)) {
                for (Field f : DensityAudit.fields(node.getClass())) {
                    Object v = f.get(node);
                    if (v instanceof net.minecraft.core.Holder<?> h && h.value() instanceof DensityFunction d) return d;
                }
                throw new IllegalStateException("holder without a density function");
            }
            if (!cn.equals(MARKER)) return node;
            String type = null;
            DensityFunction wrapped = null;
            for (Field f : DensityAudit.fields(node.getClass())) {
                Object v = f.get(node);
                if (v instanceof DensityFunction d) wrapped = d;            // first: some density functions are enums (BlendOffset)
                else if (v instanceof Enum<?> e) type = e.name();
            }
            if (wrapped == null) throw new IllegalStateException("marker without a density function");
            if (mapped != null && ("FlatCache".equals(type) || "Cache2D".equals(type)) && mapped.dependency(wrapped) == DensityAudit.XZ
                    && mapped.clean(wrapped)) {
                cached[0]++;
                return new XzCache(wrapped);
            }
            return wrapped;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The sampler's six functions, in record order (temperature, humidity, continentalness, erosion, depth, weirdness). */
    static DensityFunction[] samplerFunctions(Object sampler) throws ReflectiveOperationException {
        DensityFunction[] out = new DensityFunction[6];
        int i = 0;
        for (Field f : DensityAudit.fields(sampler.getClass())) {
            if (i < 6 && DensityFunction.class.isAssignableFrom(f.getType())) out[i++] = (DensityFunction) f.get(sampler);
        }
        if (i != 6) throw new IllegalStateException("climate sampler with " + i + " density functions");
        return out;
    }

    /**
     * 64 columns at four heights each (quart positions as sample() gets them): the sampler's own functions, the plain
     * mapping and the cached copies must give the same bits; the first difference, or null.
     */
    static String selfCheck(DensityFunction[] own, DensityFunction[] plain, DensityFunction[] copies) {
        Random r = new Random(0x5a3c1e2dL);
        for (int c = 0; c < 64; c++) {
            int qx = r.nextInt(30000) - 15000, qz = r.nextInt(30000) - 15000;
            for (int k = 0; k < 4; k++) {
                int qy = r.nextInt(200) - 16;
                DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(qx << 2, qy << 2, qz << 2);
                for (int i = 0; i < 6; i++) {
                    long a = Double.doubleToRawLongBits(own[i].compute(ctx));
                    long b = Double.doubleToRawLongBits(plain[i].compute(ctx));
                    long d = Double.doubleToRawLongBits(copies[i].compute(ctx));
                    if (a != b || a != d) return "self-check at quart " + qx + "," + qy + "," + qz + " function " + i + " differs";
                }
            }
        }
        return null;
    }
}
