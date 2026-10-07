package bons.furious.patch.embeddium_search;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.lists.VisibleChunkCollector;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch embeddium_search_replay (tested build: Embeddium 1.0.15+mc1.21.1 on Minecraft 1.21.1 /
 * NeoForge 21.1.252; client only; first written for Embeddium 0.3.31+mc1.20.1 with or without Oculus 1.8.0).
 * Helper of bons.furious.mixin.embeddium_search.OcclusionCullerReplayMixin, RenderSectionManagerEpochMixin and
 * RenderSectionInfoEpochMixin.
 *
 * Ported to 1.21.1: Embeddium 1.0.15's OcclusionCuller, VisibleChunkCollector, Viewport, CameraTransform, SimpleFrustum,
 * GraphDirection(Set), VisibilityEncoding and DoubleBufferedQueue are the 0.3.31 code under org.embeddedt.embeddium.impl
 * (CFR decompiles equal after the package move and Mojang names); RenderSection now keeps per-pass translucency sort
 * states, but its flags and visibility data are still written only by setRenderState / clearRenderState, and section
 * links only by RenderSectionManager.connect/disconnectNeighborNodes, so the key and both epochs cover the same inputs.
 * The "Embeddium's own frustum" test now checks the relocated package prefix (the 1.20.1 me.jellysquid prefix would
 * have refused SimpleFrustum, i.e. every main-view search). There is no shader pass: Oculus has no 1.21.1 build and
 * Iris 1.8.12 declares Embeddium incompatible (its frusta implement Sodium's Frustum interface, never Embeddium's), so
 * oculus_shadow_search_replay is not ported, no class carries PureShadowFrustum and only SimpleFrustum searches are
 * keyed. The Oculus/Iris/DH/Valkyrien Skies/EBE names in the allow-lists below are inert on 1.21.1; a betterfpsdist build
 * other than the tested 1.20.1 6.0 (class sha256 below) makes the switch stand down.
 *
 * Embeddium finds the chunk sections to draw with a breadth-first search (OcclusionCuller.findVisible) from the camera's
 * section: every dequeued section gets a distance + frustum test (isSectionVisible), is reported to the visitor
 * (VisibleChunkCollector.visit: region render lists, rebuild queues) and, when visible, enqueues its neighbours
 * (stamping lastVisibleFrame). With Oculus shadows the search runs twice per frame (shadow pass + main view); with a still
 * camera it repeats the same walk: 14.3% of the render thread in the 1.0.26 client recording (still camera), 10.6% of it
 * in the shadow pass.
 *
 * A search is a deterministic function of: the section graph (which sections exist and how they are linked), each visible
 * section's visibility data (only with occlusion culling), the camera's section, the occlusion flag, and the inputs of the
 * two tests it applies to every dequeued section: the distance test (camera transform, search distance, Embeddium's
 * distance filter, and betterfpsdist's hook when installed: player position, its view-angle and stretch values) and the
 * frustum test (the frustum object's own fields). Every search records the sections it reported to its visitor, in order,
 * with the answers, and a key of all those inputs: the frustum's fields bit for bit (Embeddium's SimpleFrustum over a JOML
 * FrustumIntersection, or one of Oculus's shadow frusta), the transform, the distance, betterfpsdist's inputs, the start
 * and camera section, the occlusion flag and two epochs bumped by every graph link change (RenderSectionManager.connect /
 * disconnectNeighborNodes) and every change of section build info (RenderSection.setRenderState / clearRenderState, the
 * only writers of visibility data). When the next search of the same kind has exactly the same key, the real search would
 * dequeue exactly the recorded sections in the recorded order and give them the recorded answers, so the switch stamps
 * each one's lastVisibleFrame with the new frame and hands the same (section, answer) pairs to the same visitor instead.
 * The visitor reads each section's flags and rebuild state live, so render lists and rebuild queues are built exactly as
 * by the search; translucency checks, Oculus's list swap and Valkyrien Skies' ship lists run after it as shipped.
 * The search's incomingDirections scratch field is not replayed: it is read only inside a search, after that search reset
 * it (every first visit and the start section set it to 0), so the values a search leaves behind are never read
 * (class-file scan of all 486 client jars: only RenderSection and OcclusionCuller reference it).
 *
 * Only searches that start inside the world from a loaded camera section, with a frame id newer than any this culler saw
 * (the search's "already visited" test compares stamps with the frame), with Embeddium's own VisibleChunkCollector, a
 * tested frustum and Embeddium's default distance filter, are recorded or replayed; OcclusionCuller, RenderSection,
 * VisibleChunkCollector, Viewport and CameraTransform may carry only the tested mixins. Everything else runs unchanged.
 */
public final class SearchReplay {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.embeddiumSearchReplay=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.embeddiumSearchReplay", "true"));
    /**
     * Rig self-check: -Dbons_and_furious.embeddiumSearchReplay.shadow=true runs the real search every time; whenever the
     * key matched (the replay would have been used) the real search's visit sequence is compared with the recorded one.
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.embeddiumSearchReplay.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Rig counters: searches seen, searches answered by a replay, recorded searches, replayed sections, searches left unkeyed (backoff). */
    public static final AtomicLong SEARCHES = new AtomicLong(), REPLAYS = new AtomicLong(), RECORDS = new AtomicLong(), REPLAYED_SECTIONS = new AtomicLong(),
            UNKEYED = new AtomicLong();

    /** Bumped by every link or unlink of a section (RenderSectionManager.connect/disconnectNeighborNodes). Render thread only. */
    public static int graphEpoch;
    /** Bumped by every change of a section's build info, which holds its visibility data (RenderSection.setRenderState/clearRenderState). */
    public static int infoEpoch;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String MERGED = "org.spongepowered.asm.mixin.transformer.meta.MixinMerged";
    static final sun.misc.Unsafe U;

    /** Frustum implementations whose testAab only reads the frustum's own fields (no side effects, no section state). */
    static final Set<String> PURE_FRUSTA = Set.of(
            "org.embeddedt.embeddium.impl.render.viewport.frustum.SimpleFrustum",
            "net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum",
            "net.irisshaders.iris.shadows.frustum.advanced.ReversedAdvancedShadowCullingFrustum",
            "net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum",
            "net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum",
            "net.irisshaders.iris.shadows.frustum.CullEverythingFrustum");

    /** Every mixin that may be merged into the classes the search uses (offline neighbour check of 1.0.30, client pack). */
    static final Set<String> TESTED_MIXINS = Set.of(
            "bons.furious.mixin.embeddium_search.OcclusionCullerReplayMixin",
            "bons.furious.mixin.embeddium_search.ViewportAccessMixin",
            "bons.furious.mixin.embeddium_search.RenderSectionInfoEpochMixin",
            "com.betterfpsdist.mixin.Sodiummixin",                                                   // OcclusionCuller.isWithinRenderDistance
            "org.valkyrienskies.mod.mixin.mod_compat.sodium.MixinRenderSection",                    // RenderSection.getSquaredDistance
            "foundationgames.enhancedblockentities.core.mixin.embeddium.RenderSectionMixin",        // RenderSection: an added field pair
            "net.irisshaders.iris.compat.sodium.mixin.shadow_map.frustum.MixinAdvancedShadowCullingFrustum",
            "net.irisshaders.iris.compat.sodium.mixin.shadow_map.frustum.MixinBoxCullingFrustum",
            "net.irisshaders.iris.compat.sodium.mixin.shadow_map.frustum.MixinCullEverythingFrustum",
            "net.irisshaders.iris.compat.sodium.mixin.shadow_map.frustum.MixinNonCullingFrustum",
            "net.irisshaders.iris.compat.dh.mixin.AdvancedShadowCullingFrustumMixin",
            "net.irisshaders.iris.compat.dh.mixin.BoxCullingFrustumMixin",
            "net.irisshaders.iris.compat.dh.mixin.CullEverythingFrustumMixin",
            "net.irisshaders.iris.compat.dh.mixin.NonCullingFrustumMixin",
            "bons.furious.mixin.oculus.ShadowEdgePlaneMixin",                                       // constructor-time plane math (1.0.20)
            "bons.furious.mixin.embeddium_search.ShadowFrustumMarkerMixin");

    private static volatile Boolean coreReady;
    private static final Map<Class<?>, Plan> PLANS = new ConcurrentHashMap<>();
    private static final Plan NO_PLAN = new Plan(new long[0], new char[0], new long[0], new Plan[0], new long[0], new Plan[0], null);
    private static volatile boolean announced;
    /** betterfpsdist's hook inputs, read without initialising any class (statics through Unsafe). */
    private static boolean betterHook;
    private static Object cehBase, modBase, mcBase;
    private static long cosOff, sinOff, xOff, yOff, maxOff, configOff, mcOff;
    private static Method betterCommon, getX, getY, getZ;
    private static Field betterDebug, mcPlayer;
    /** Embeddium's distance filter (OcclusionCuller$DistanceFilterHolder.INSTANCE), read without initialising the holder. */
    private static Object filterDefault, filterHolderBase;
    private static long filterOffset;

    static {
        sun.misc.Unsafe u = null;
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (sun.misc.Unsafe) f.get(null);
        } catch (Throwable t) {
            enabled = false;
        }
        U = u;
    }

    private SearchReplay() {
    }

    /**
     * True when the search's own classes carry only tested mixins and, with betterfpsdist installed, its hook is the tested
     * one (checked once; otherwise the switch stands down). Initialises one class only, Embeddium's API interface
     * RenderSectionDistanceFilter, whose static initialiser just builds its DEFAULT lambda (1.0.34, see initDistanceFilter):
     * the distance-filter holder and betterfpsdist's classes are looked up without initialisation and their statics read
     * through Unsafe, so their static initialisers still run when the search first needs them, exactly as without the switch.
     */
    public static boolean coreReady() {
        Boolean r = coreReady;
        if (r == null) {
            Set<String> unknown = new TreeSet<>();
            boolean ok = U != null && inventory(OcclusionCuller.class, unknown) & inventory(RenderSection.class, unknown)
                    & inventory(VisibleChunkCollector.class, unknown) & inventory(Viewport.class, unknown) & inventory(CameraTransform.class, unknown);
            String why = null;
            if (!ok || !unknown.isEmpty()) why = "the section search's classes carry mixins it was not tested with ("
                    + (unknown.isEmpty() ? "could not list them" : String.join(", ", unknown)) + ")";
            if (why == null && !initDistanceFilter()) why = "Embeddium's section distance filter could not be inspected";
            if (why == null && !initBetterfpsdist()) why = "betterfpsdist's distance hook is not the tested 6.0 build";
            r = why == null;
            if (!r) {
                enabled = false;
                LOGGER.warn("Bons and Furious: embeddium_search_replay stands down: {}; every search runs unchanged", why);
            }
            coreReady = r;
        }
        return r;
    }

    private static boolean initDistanceFilter() {
        try {
            ClassLoader cl = OcclusionCuller.class.getClassLoader();
            Class<?> holder = Class.forName("org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller$DistanceFilterHolder", false, cl);
            Field inst = holder.getDeclaredField("INSTANCE");
            filterHolderBase = U.staticFieldBase(inst);
            filterOffset = U.staticFieldOffset(inst);
            // 1.0.34: the interface is initialised (its static initialiser only builds the DEFAULT lambda). Read uninitialised,
            // DEFAULT was null whenever no search had run a distance test yet (e.g. a camera section not yet built at world
            // join), and every key after the holder's first use failed for the rest of the session: no replay at all
            Class<?> api = Class.forName("org.embeddedt.embeddium.api.render.chunk.RenderSectionDistanceFilter", true, cl);
            Field def = api.getField("DEFAULT");
            filterDefault = U.getObject(U.staticFieldBase(def), U.staticFieldOffset(def));
            return filterDefault != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The hook's inputs: player position, view angle (cos/sin), stretch factors, squared distance; debug mode records sections. */
    private static boolean initBetterfpsdist() {
        boolean present = false;
        try {
            for (Method m : OcclusionCuller.class.getDeclaredMethods()) {
                for (Annotation a : m.getDeclaredAnnotations()) {
                    if (a.annotationType().getName().equals(MERGED)
                            && "com.betterfpsdist.mixin.Sodiummixin".equals(String.valueOf(a.annotationType().getMethod("mixin").invoke(a)))) present = true;
                }
            }
            if (!present) return true;
            ClassLoader cl = OcclusionCuller.class.getClassLoader();
            for (Map.Entry<String, String> e : BETTERFPSDIST_HASHES.entrySet()) {
                try (InputStream in = cl.getResourceAsStream(e.getKey())) {
                    if (in == null) return false;
                    String h = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                    if (!h.equals(e.getValue())) return false;
                }
            }
            Class<?> ceh = Class.forName("com.betterfpsdist.event.ClientEventHandler", false, cl);
            Field cos = ceh.getField("cosAngle"), sin = ceh.getField("sinAngle"), xs = ceh.getField("xStretch"), ys = ceh.getField("yStretch"),
                    max = ceh.getField("maxSqDist");
            cehBase = U.staticFieldBase(cos);
            cosOff = U.staticFieldOffset(cos);
            sinOff = U.staticFieldOffset(sin);
            xOff = U.staticFieldOffset(xs);
            yOff = U.staticFieldOffset(ys);
            maxOff = U.staticFieldOffset(max);
            Class<?> mod = Class.forName("com.betterfpsdist.BetterfpsdistMod", false, cl);
            Field config = mod.getField("config");
            modBase = U.staticFieldBase(config);
            configOff = U.staticFieldOffset(config);
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, cl);
            Field inst = mc.getDeclaredField("instance");
            mcBase = U.staticFieldBase(inst);
            mcOff = U.staticFieldOffset(inst);
            mcPlayer = mc.getField("player");
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity", false, cl);
            getX = entity.getMethod("getX");
            getY = entity.getMethod("getY");
            getZ = entity.getMethod("getZ");
            betterHook = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** sha256 of the tested betterfpsdist 6.0 class files (filled from the installed jar; see notes/embeddium_search.md). */
    static final Map<String, String> BETTERFPSDIST_HASHES = Map.of(
            "com/betterfpsdist/mixin/Sodiummixin.class", "3a8e5ba2716462bef812af2c99d8a76790fce412050400193cad41ebf6c8b28b",
            "com/betterfpsdist/event/ClientEventHandler.class", "856960e3e947c9f299786eb9158cc0deafa0ed71f067c13a381cc439a6f6cf59");

    private static boolean inventory(Class<?> c, Set<String> unknown) {
        try {
            for (Method m : c.getDeclaredMethods()) {
                for (Annotation a : m.getDeclaredAnnotations()) {
                    if (!a.annotationType().getName().equals(MERGED)) continue;
                    String mixin = String.valueOf(a.annotationType().getMethod("mixin").invoke(a));
                    if (!TESTED_MIXINS.contains(mixin)) unknown.add(mixin);
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: embeddium_search_replay: a section search whose every input equals the previous search's replays it"
                    + "{}", SHADOW ? " (SHADOW mode: the real search always runs; the replay is only compared)" : "");
        }
    }

    public static void mismatch(String what) {
        if (SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: embeddium_search_replay SHADOW mismatch: {}", what);
        }
    }

    // ------------------------------------------------------------------------------------------------ key

    /**
     * How to copy a frustum's state, bit for bit: its primitive fields and those of the JOML / Oculus value objects it
     * holds (vectors, matrices, the JOML FrustumIntersection, Oculus's BoxCuller, arrays of them), walking the class up to
     * (not into) vanilla's Frustum, whose fields Embeddium's frustum test never reads. Built once per class from its
     * declared fields; a field of any other object type makes the class unsupported (no plan: never replayed).
     */
    public static final class Plan {
        final long[] prim;
        final char[] kind;
        final long[] obj;
        final Plan[] objPlan;
        final long[] arr;
        final Plan[] arrPlan;
        final Class<?> type;

        Plan(long[] prim, char[] kind, long[] obj, Plan[] objPlan, long[] arr, Plan[] arrPlan, Class<?> type) {
            this.prim = prim;
            this.kind = kind;
            this.obj = obj;
            this.objPlan = objPlan;
            this.arr = arr;
            this.arrPlan = arrPlan;
            this.type = type;
        }
    }

    static boolean valueType(Class<?> t) {
        String n = t.getName();
        return n.startsWith("org.joml.") || n.equals("net.irisshaders.iris.shadows.frustum.BoxCuller");
    }

    static Plan plan(Class<?> c, int depth) {
        if (depth > 3) return null;
        List<Long> prim = new ArrayList<>(), obj = new ArrayList<>(), arr = new ArrayList<>();
        StringBuilder kinds = new StringBuilder();
        List<Plan> objPlans = new ArrayList<>(), arrPlans = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class && !k.getName().startsWith("net.minecraft."); k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                long off = U.objectFieldOffset(f);
                if (t.isPrimitive()) {
                    prim.add(off);
                    kinds.append(t == int.class ? 'I' : t == float.class ? 'F' : t == long.class ? 'J' : t == double.class ? 'D'
                            : t == boolean.class ? 'Z' : t == byte.class ? 'B' : t == short.class ? 'S' : 'C');
                } else if (t.isArray() && !t.getComponentType().isPrimitive() && valueType(t.getComponentType())) {
                    Plan p = plan(t.getComponentType(), depth + 1);
                    if (p == null) return null;
                    arr.add(off);
                    arrPlans.add(p);
                } else if (valueType(t)) {
                    Plan p = plan(t, depth + 1);
                    if (p == null) return null;
                    obj.add(off);
                    objPlans.add(p);
                } else {
                    return null;      // an input the key would not see
                }
            }
        }
        return new Plan(prim.stream().mapToLong(Long::longValue).toArray(), kinds.toString().toCharArray(),
                obj.stream().mapToLong(Long::longValue).toArray(), objPlans.toArray(new Plan[0]),
                arr.stream().mapToLong(Long::longValue).toArray(), arrPlans.toArray(new Plan[0]), c);
    }

    /**
     * The frustum class's plan when its test is a tested pure implementation: Embeddium's SimpleFrustum, or one of Oculus's
     * shadow frusta carrying the PureShadowFrustum marker (added only while oculus_shadow_search_replay's guard matches),
     * carrying only tested mixins up to vanilla's Frustum. Null when searches with it are never replayed.
     */
    public static Plan frustumPlan(Class<?> c) {
        Plan p = PLANS.get(c);
        if (p == null) {
            String why = null;
            // 1.21.1: Embeddium's own classes live under org.embeddedt.embeddium.impl (were me.jellysquid.mods.sodium.client)
            if (!PURE_FRUSTA.contains(c.getName()) || !(c.getName().startsWith("org.embeddedt.embeddium.impl.") || PureShadowFrustum.class.isAssignableFrom(c))) {
                why = "not a tested implementation";
            } else {
                Set<String> unknown = new TreeSet<>();
                boolean listed = true;
                for (Class<?> k = c; k != null && k != Object.class && !k.getName().startsWith("net.minecraft."); k = k.getSuperclass()) {
                    listed &= inventory(k, unknown);
                }
                if (!listed || !unknown.isEmpty()) why = "untested mixins: " + String.join(", ", unknown);
                else {
                    try {
                        p = plan(c, 0);
                    } catch (Throwable t) {
                        p = null;
                    }
                    if (p == null) why = "a field the key cannot copy";
                }
            }
            if (p == null) {
                p = NO_PLAN;
                // DEBUG: one INFO line per switch (announce); the rig sees refused frusta in SEARCHES (they are not counted)
                LOGGER.debug("Bons and Furious: embeddium_search_replay leaves searches with the frustum {} unchanged ({})", c.getName(), why);
            }
            PLANS.put(c, p);
        }
        return p == NO_PLAN ? null : p;
    }

    /** A growable key: the search's inputs as raw bits. */
    public static final class Key {
        long[] v = new long[128];
        int n;

        void put(long x) {
            if (this.n == this.v.length) this.v = Arrays.copyOf(this.v, this.n * 2);
            this.v[this.n++] = x;
        }

        boolean same(Key o) {
            return o.n == this.n && Arrays.equals(this.v, 0, this.n, o.v, 0, o.n);
        }

        void copyFrom(Key o) {
            if (this.v.length < o.n) this.v = new long[o.v.length];
            System.arraycopy(o.v, 0, this.v, 0, o.n);
            this.n = o.n;
        }
    }

    /** Appends o's state to the key; false when o, or an object it holds, is of another class than its plan. */
    private static boolean snapshot(Object o, Plan p, Key k) {
        if (o == null) {
            k.put(0x6e756c6cL);
            return true;
        }
        // 1.0.34: another class than planned: the plan does not list its fields, so two of its states could give equal keys
        // (this wrote a class marker and went on); now no key, and the search runs unchanged
        if (o.getClass() != p.type) return false;
        for (int i = 0; i < p.prim.length; i++) {
            long off = p.prim[i];
            switch (p.kind[i]) {
                case 'I' -> k.put(U.getInt(o, off));
                case 'F' -> k.put(Float.floatToRawIntBits(U.getFloat(o, off)));
                case 'J' -> k.put(U.getLong(o, off));
                case 'D' -> k.put(Double.doubleToRawLongBits(U.getDouble(o, off)));
                case 'Z' -> k.put(U.getBoolean(o, off) ? 1 : 0);
                case 'B' -> k.put(U.getByte(o, off));
                case 'S' -> k.put(U.getShort(o, off));
                default -> k.put(U.getChar(o, off));
            }
        }
        for (int i = 0; i < p.obj.length; i++) {
            if (!snapshot(U.getObject(o, p.obj[i]), p.objPlan[i], k)) return false;
        }
        for (int i = 0; i < p.arr.length; i++) {
            Object[] a = (Object[]) U.getObject(o, p.arr[i]);
            if (a == null) {
                k.put(-1);
                continue;
            }
            k.put(a.length);
            for (Object e : a) {
                if (!snapshot(e, p.arrPlan[i], k)) return false;
            }
        }
        return true;
    }

    /**
     * Fills the key with everything the two per-section tests read: the frustum's state, the camera transform, the search
     * distance, and betterfpsdist's inputs when its hook is installed. False when an input cannot be keyed (betterfpsdist
     * debug mode, which records sections as a side effect; since 1.0.34 also a frustum object of a class its plan does not
     * cover): then the search runs unchanged.
     */
    public static boolean key(Key k, Object frustum, Plan plan, CameraTransform t, float searchDistance) {
        k.n = 0;
        k.put(System.identityHashCode(frustum.getClass()));
        if (!snapshot(frustum, plan, k)) return false;
        k.put(t.intX);
        k.put(t.intY);
        k.put(t.intZ);
        k.put(Float.floatToRawIntBits(t.fracX));
        k.put(Float.floatToRawIntBits(t.fracY));
        k.put(Float.floatToRawIntBits(t.fracZ));
        k.put(Float.floatToRawIntBits(searchDistance));
        // the holder is initialised by the first real search's first distance test; until then (null) no key
        if (U.getObject(filterHolderBase, filterOffset) != filterDefault) return false;
        if (betterHook) {
            try {
                // the hook returns at once without a player; with one it reads the position, its tick-updated angle and
                // stretch values, and (only past the distance check) its config's debug flag
                Object mc = U.getObject(mcBase, mcOff);
                Object player = mc == null ? null : mcPlayer.get(mc);
                if (player == null) {
                    k.put(0x706c6179L);
                } else {
                    Object config = U.getObject(modBase, configOff);
                    if (config == null) return false;
                    if (betterCommon == null) {
                        betterCommon = config.getClass().getMethod("getCommonConfig");
                        betterDebug = betterCommon.invoke(config).getClass().getField("debugMode");
                    }
                    if (betterDebug.getBoolean(betterCommon.invoke(config))) return false;   // debug mode records culled sections
                    k.put(Float.floatToRawIntBits(U.getFloat(cehBase, cosOff)));
                    k.put(Float.floatToRawIntBits(U.getFloat(cehBase, sinOff)));
                    k.put(Float.floatToRawIntBits(U.getFloat(cehBase, xOff)));
                    k.put(Float.floatToRawIntBits(U.getFloat(cehBase, yOff)));
                    k.put(U.getInt(cehBase, maxOff));
                    k.put(Double.doubleToRawLongBits((Double) getX.invoke(player)));
                    k.put(Double.doubleToRawLongBits((Double) getY.invoke(player)));
                    k.put(Double.doubleToRawLongBits((Double) getZ.invoke(player)));
                }
            } catch (Throwable e) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ recording

    /**
     * One recorded search: the sections in the order the search reported them to its visitor and the answers it reported,
     * plus its key, epochs, start section and camera section.
     */
    public static final class Recording {
        RenderSection[] sections = new RenderSection[1024];
        boolean[] visible = new boolean[1024];
        int size, prevSize;
        boolean valid;
        Class<?> frustumClass;
        boolean occlusion;
        int graph, info;
        RenderSection start;
        int ox, oy, oz;
        final Key key = new Key();
        long lastUse;

        public RenderSection[] sections() {
            return this.sections;
        }

        public boolean[] visible() {
            return this.visible;
        }

        public int size() {
            return this.size;
        }

        public boolean occlusion() {
            return this.occlusion;
        }

        public boolean valid() {
            return this.valid;
        }

        void add(RenderSection s, boolean v) {
            int n = this.size;
            if (n == this.sections.length) {
                this.sections = Arrays.copyOf(this.sections, n * 2);
                this.visible = Arrays.copyOf(this.visible, n * 2);
            }
            this.sections[n] = s;
            this.visible[n] = v;
            this.size = n + 1;
        }

        /** Every input equal: graph and (with occlusion) build-info epochs, start and camera section, occlusion flag, the key. */
        public boolean matches(RenderSection start, int ox, int oy, int oz, boolean occlusion, int graph, int info, Key key) {
            return this.valid && this.graph == graph && this.info == info && this.start == start && this.ox == ox && this.oy == oy && this.oz == oz
                    && this.occlusion == occlusion && this.key.same(key);
        }

        public void begin() {
            this.prevSize = this.size;
            this.valid = false;
            this.size = 0;
        }

        /** End of a recorded real search: valid only when it returned normally and its inputs could be keyed. */
        public void finish(boolean ok, RenderSection start, int ox, int oy, int oz, boolean occlusion, int graph, int info, Key key) {
            if (this.prevSize > this.size) Arrays.fill(this.sections, this.size, this.prevSize, null);   // drop references no longer listed
            this.valid = ok && this.size > 0 && this.sections[0] == start;
            this.start = start;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.occlusion = occlusion;
            this.graph = graph;
            this.info = info;
            this.key.copyFrom(key);
            if (this.valid) RECORDS.incrementAndGet();
        }

        public void invalidate() {
            if (this.size > 0) Arrays.fill(this.sections, 0, this.size, null);
            this.size = 0;
            this.valid = false;
            this.start = null;
            this.seenValid = false;
            this.seenStart = null;
            this.quiet = 0;
            this.skip = 0;
        }

        /** The key of the recorded search, read right after it (the inputs cannot change during a search: render thread). */
        public void setKey(Key key) {
            this.key.copyFrom(key);
        }

        /** Checked searches in a row that neither replayed nor repeated, and searches left to run unchecked (performance rule). */
        int quiet, skip;

        /** True when this search runs unchecked: the view kept changing, so only one search in four is keyed. */
        public boolean skipCheck() {
            if (this.skip > 0) {
                this.skip--;
                UNKEYED.incrementAndGet();
                return true;
            }
            return false;
        }

        /** A checked search whose inputs changed: after 16 in a row, the next three searches run unchecked. */
        public void changed() {
            if (++this.quiet >= 16) this.skip = 3;
        }

        /** A replayed or recorded search: check every search again. */
        public void still() {
            this.quiet = 0;
            this.skip = 0;
        }

        /** The inputs of the last search of this kind that was not replayed (performance rule for recording only). */
        final Key seenKey = new Key();
        boolean seenValid;
        RenderSection seenStart;
        int seenOx, seenOy, seenOz, seenGraph, seenInfo;

        /**
         * After a search that cannot be replayed: true when the previous such search of this kind had exactly these inputs
         * (a still view), so recording this one lets the next identical search replay; a moving view never repeats its
         * predecessor and is never recorded (it pays only the key). Remembers these inputs for the next call.
         */
        public boolean repeats(RenderSection start, int ox, int oy, int oz, int graph, int info, Key key) {
            boolean same = this.seenValid && this.seenStart == start && this.seenOx == ox && this.seenOy == oy && this.seenOz == oz
                    && this.seenGraph == graph && this.seenInfo == info && this.seenKey.same(key);
            if (!same) {
                this.seenValid = true;
                this.seenStart = start;
                this.seenOx = ox;
                this.seenOy = oy;
                this.seenOz = oz;
                this.seenGraph = graph;
                this.seenInfo = info;
                this.seenKey.copyFrom(key);
            }
            return same;
        }

        /** Slot choice (a performance rule): the slot that last recorded searches with this frustum class and occlusion flag. */
        public boolean holds(Class<?> frustumClass, boolean occlusion) {
            return this.frustumClass == frustumClass && this.occlusion == occlusion;
        }

        public void assign(Class<?> frustumClass, boolean occlusion) {
            this.invalidate();
            this.frustumClass = frustumClass;
            this.occlusion = occlusion;
        }

        public long lastUse() {
            return this.lastUse;
        }

        public void touch(long stamp) {
            this.lastUse = stamp;
        }

        /** Same sections, same order, same answers (SHADOW mode). */
        public boolean sameSequence(Recording o) {
            if (o.size != this.size) return false;
            for (int i = 0; i < this.size; i++) {
                if (o.sections[i] != this.sections[i] || o.visible[i] != this.visible[i]) return false;
            }
            return true;
        }

        public void copyFrom(Recording o) {
            int old = this.size;
            this.size = 0;
            for (int i = 0; i < o.size; i++) this.add(o.sections[i], o.visible[i]);
            if (old > this.size) Arrays.fill(this.sections, this.size, old, null);
            this.valid = o.valid;
            // 1.0.34: the slot keeps its own kind (frustum class, occlusion flag; o recorded a search of that same kind).
            // Copying them from the SHADOW-mode check recording, which is never assign()ed a kind, left the slot with no
            // frustum class, so the next search of that kind evicted a slot and only every other search was checked
            this.graph = o.graph;
            this.info = o.info;
            this.start = o.start;
            this.ox = o.ox;
            this.oy = o.oy;
            this.oz = o.oz;
            this.key.copyFrom(o.key);
        }
    }

    /** The visitor handed to a recorded real search: records each call, then forwards it unchanged. */
    public static final class Recorder implements OcclusionCuller.Visitor {
        private OcclusionCuller.Visitor delegate;
        private Recording recording;

        public Recorder begin(OcclusionCuller.Visitor delegate, Recording recording) {
            this.delegate = delegate;
            this.recording = recording;
            recording.begin();
            return this;
        }

        public void end() {
            this.delegate = null;
            this.recording = null;
        }

        @Override
        public void visit(RenderSection section, boolean visible) {
            this.recording.add(section, visible);
            this.delegate.visit(section, visible);
        }
    }
}
