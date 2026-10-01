package bons.pure.terrain;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density-graph audits for the 1.0.23 terrain switches (vanilla 1.20.1 world generation). SRG member names.
 *
 * A cache or memo over density values is exact only when the cached function is a function of its inputs alone. Every
 * vanilla density function type is: records of final fields, wired noises and immutable lists. Another mod's type may
 * carry state (Bumblezone binds its biome noise to each NoiseChunk, for example), so the switches that cache density
 * results refuse any graph that contains a type not listed here, and keep the original code for it.
 *
 * {@link #refusal} checks types only. {@link Walk#dependency} also tells whether a subtree reads only x and z (the
 * coordinate test the DH rough-surface cache needs) and {@link Walk#clean} whether it holds only known types. Records
 * are read through their fields: record accessors do not resolve on the SRG runtime.
 */
public final class DensityAudit {
    public static final int XZ = 0, Y = 1;
    static final String DFS = "net.minecraft.world.level.levelgen.DensityFunctions$", NC = "net.minecraft.world.level.levelgen.NoiseChunk$";
    /** Types whose value is a function of their children (and constants). */
    static final Set<String> COMPOSITE = Set.of(DFS + "Ap2", DFS + "MulOrAdd", DFS + "Mapped", DFS + "Clamp", DFS + "RangeChoice", DFS + "Spline",
            DFS + "Spline$Coordinate", DFS + "BlendDensity", DFS + "HolderHolder", DFS + "Marker", NC + "NoiseInterpolator", NC + "FlatCache",
            NC + "Cache2D", NC + "CacheOnce", NC + "CacheAllInCell", "net.minecraft.util.CubicSpline$Multipoint",
            "bons.pure.terrain.XzCache");
    /** Leaves (or leaf-like types) that read only x and z. */
    static final Set<String> XZ_LEAF = Set.of(DFS + "ShiftA", DFS + "ShiftB", DFS + "EndIslandDensityFunction", DFS + "Constant", DFS + "BlendAlpha",
            DFS + "BlendOffset", NC + "BlendAlpha", NC + "BlendOffset", "net.minecraft.util.CubicSpline$Constant");
    /** Types that read y. WeirdScaledSampler is listed here for its own noise and still has its input walked. */
    static final Set<String> Y_LEAF = Set.of(DFS + "YClampedGradient", DFS + "WeirdScaledSampler", DFS + "Shift", DFS + "BeardifierMarker",
            "net.minecraft.world.level.levelgen.synth.BlendedNoise", "net.minecraft.world.level.levelgen.Beardifier");
    /**
     * Other mods' density function types reviewed as pure, each bound to the class file that was reviewed (sha256 of the
     * .class bytes in the mod jar): another build of the type counts as unknown. Children are walked like a composite's.
     */
    static final Map<String, String> REVIEWED = Map.of(
            // Tectonic 3.0.17 (MIT), reviewed 2026-10-01: record (input, min, max); compute is 1 / input, fillArray the same per
            // point, mapAll rebuilds it from the mapped input, min/max come from the input. Tectonic's ConfigConstant,
            // ConfigClamp and ConfigNoise rewrite themselves into vanilla types when a RandomState wires the router, so this
            // is the only Tectonic type a wired overworld router contains.
            "dev.worldgen.tectonic.worldgen.densityfunction.Invert", "ccc043b6553d65dee32da07248af321986ccac4f24022cbf0442961d15967e8c");
    private static final ClassValue<Boolean> REVIEWED_BUILD = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            String want = REVIEWED.get(c.getName());
            if (want == null) return false;
            try (java.io.InputStream in = c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
                if (in == null) return false;
                byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
                return want.equals(java.util.HexFormat.of().formatHex(d));
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private DensityAudit() {
    }

    /** Null when every node reachable from the roots is a known pure type; otherwise the first reason found. */
    public static String refusal(Object... roots) {
        Walk w = new Walk();
        try {
            for (Object r : roots) w.dependency(r);
        } catch (Throwable t) {
            return "audit failed: " + t;
        }
        return w.refusal;
    }

    /** One traversal: per-node coordinate dependency and type check, memoised by identity (density graphs are DAGs). */
    public static final class Walk {
        private final Map<Object, Integer> dependency = new IdentityHashMap<>();
        private final Map<Object, Boolean> clean = new IdentityHashMap<>();
        /** First unsupported type found anywhere in this walk, or null. */
        public String refusal;

        /** XZ or Y for a density function, spline or spline coordinate; unknown types count as Y. */
        public int dependency(Object o) throws ReflectiveOperationException {
            if (o == null) return XZ;
            Integer m = dependency.get(o);
            if (m != null) return m;
            dependency.put(o, XZ);                            // cycle guard; vanilla graphs have no cycles
            clean.put(o, Boolean.TRUE);
            String cn = o.getClass().getName();
            boolean known = XZ_LEAF.contains(cn) || Y_LEAF.contains(cn) || COMPOSITE.contains(cn) || REVIEWED_BUILD.get(o.getClass())
                    || cn.equals(DFS + "Noise") || cn.equals(DFS + "ShiftedNoise");
            if (!known && refusal == null) refusal = (REVIEWED.containsKey(cn) ? "density function type " + cn + " differs from the reviewed build"
                    : "unsupported density function type " + cn);
            boolean ok = known;
            int children = XZ;
            for (Object c : children(o)) {
                children = Math.max(children, dependency(c));
                ok &= Boolean.TRUE.equals(clean.get(c));
            }
            int d;
            if (XZ_LEAF.contains(cn)) d = XZ;
            else if (Y_LEAF.contains(cn) || !known) d = Y;
            else if (cn.equals(DFS + "Noise")) d = yScaleZero(o) ? XZ : Y;
            else if (cn.equals(DFS + "ShiftedNoise")) d = yScaleZero(o) ? children : Y;
            else d = children;
            dependency.put(o, d);
            clean.put(o, ok);
            return d;
        }

        /** True when the node (already walked) and every node below it are known types. */
        public boolean clean(Object o) {
            return Boolean.TRUE.equals(clean.get(o));
        }

        private static boolean yScaleZero(Object o) throws ReflectiveOperationException {
            double[] d = doubles(o);                           // xzScale, yScale
            return d.length == 2 && d[1] == 0.0;
        }
    }

    /** Density functions, splines and spline coordinates directly below a node (holders are looked through). */
    static List<Object> children(Object o) throws ReflectiveOperationException {
        List<Object> out = new ArrayList<>(4);
        for (Field f : fields(o.getClass())) {
            if (f.getType().isPrimitive()) continue;
            add(out, f.get(o));
        }
        return out;
    }

    private static void add(List<Object> out, Object v) {
        if (v == null) return;
        if (v instanceof DensityFunction || v instanceof CubicSpline<?, ?>) {
            out.add(v);
        } else if (v instanceof Holder<?> h) {
            Object x = h.m_203334_();
            if (x instanceof DensityFunction) out.add(x);
        } else if (v instanceof List<?> l) {
            for (Object e : l) add(out, e);
        } else if (v.getClass().getName().equals(DFS + "Spline$Coordinate")) {
            out.add(v);
        }
        // enums, noise holders, arrays and the owning NoiseChunk carry no density function
    }

    static double[] doubles(Object o) throws ReflectiveOperationException {
        List<Double> out = new ArrayList<>();
        for (Field f : fields(o.getClass())) if (f.getType() == double.class) out.add(f.getDouble(o));
        double[] r = new double[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> c) {
            List<Class<?>> chain = new ArrayList<>();
            // JDK superclasses (Object, Record, Enum for the enum density functions) hold no density function and are
            // not open to reflection
            for (Class<?> k = c; k != null && !k.getName().startsWith("java."); k = k.getSuperclass()) chain.add(0, k);
            List<Field> out = new ArrayList<>();
            for (Class<?> k : chain) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    out.add(f);
                }
            }
            return List.copyOf(out);
        }
    };

    public static List<Field> fields(Class<?> c) {
        return FIELDS.get(c);
    }
}
