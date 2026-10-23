package bons.furious.patch.vanilla_climate_distance;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switches vanilla_climate_search_distance_reuse and terrablender_climate_search_distance_reuse
 * (Minecraft 1.20.1, every multi-noise biome lookup; both sides; since 1.0.46). Idea: Moonrise's "Optimise Climate RTree
 * lookup" commit text (idea text only); the walk is ours.
 *
 * Climate.RTree.search asks the root node for the nearest leaf, starting from the leaf this thread found last.
 * SubTree.search starts by computing the distance of the leaf it was handed, and after each child's search computes the
 * distance of the leaf that child returned. Both values are already known to its caller: the leaf it hands down is the
 * caller's best, whose distance the caller holds, and the returned leaf's distance is the best the child ended with. The
 * root call (only it, so vanilla_climate_search_repeat's guards on SubTree and Leaf keep describing the code that runs)
 * now runs a walk that visits the same children in the same order with the same strict comparisons, carries the caller's
 * best distance down and the found leaf's distance back up, and computes every other distance exactly where vanilla
 * does. Same leaf, same ties, and RTree.search stores the same leaf as this thread's warm start.
 *
 * The distances skipped are calls of the search's metric with arguments it was already called with in the same search,
 * so the walk is used only for metric objects known to be the vanilla node distance (the method reference
 * Climate.RTree.Node::distance, distance, a pure computation over the node's final ranges). Those objects are registered
 * where they are made: in vanilla's Climate.ParameterList.findValueIndex (findValueIndex), whose guarded bytecode makes exactly
 * that method reference, and in TerraBlender's ParameterList.findValuePositional (switch
 * terrablender_climate_search_distance_reuse; TerraBlender 3.0.1.10's guarded mixin bytecode makes two). A non-capturing
 * method reference is one object per call site, so at most three objects are ever registered. Any other metric keeps
 * vanilla's search. Distances are asked from Node.distance directly, which is what the method reference does.
 *
 * Runtime switch: -Dbons_and_furious.vanillaClimateSearchDistanceReuse=false. Shadow mode (verification runs):
 * -Dbons_and_furious.vanillaClimateSearchDistanceReuse.shadow=true also runs vanilla's search for every walked search
 * (SHADOW_CHECKS) and compares the leaves by identity (SHADOW_MISMATCHES, first 20 logged as WARN; expect 0).
 */
public final class ClimateDistanceReuse {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaClimateSearchDistanceReuse", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaClimateSearchDistanceReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final String BIOME = "net.minecraft.world.level.biome.";
    static final Class<?> NODE, SUBTREE, LEAF, METRIC;
    /** (Object node, long[] target, Object candidate, Object metric) -> Object leaf: vanilla's Node.search. */
    static final MethodHandle SEARCH;
    /** (Object subtree) -> Object[]: SubTree.children. */
    static final MethodHandle CHILDREN;
    /** (Object node, long[] target) -> long: Node.distance (distance), virtual as the method reference calls it. */
    static final MethodHandle DISTANCE;
    /** (Object metric, Object node, long[] target) -> long: DistanceMetric.distance, for the impossible branch only. */
    static final MethodHandle METRIC_DISTANCE;
    private static final ThreadLocal<long[]> OUT = ThreadLocal.withInitial(() -> new long[1]);
    private static final AtomicBoolean ANNOUNCE = new AtomicBoolean();
    /** The registered metric objects (copy on write; one per creating call site). */
    private static volatile Object[] known = new Object[0];

    static {
        try {
            NODE = Class.forName(BIOME + "Climate$RTree$Node");
            SUBTREE = Class.forName(BIOME + "Climate$RTree$SubTree");
            LEAF = Class.forName(BIOME + "Climate$RTree$Leaf");
            METRIC = Class.forName(BIOME + "Climate$DistanceMetric");
            MethodHandles.Lookup me = MethodHandles.lookup();
            MethodHandles.Lookup node = MethodHandles.privateLookupIn(NODE, me);
            SEARCH = node.findVirtual(NODE, "search", MethodType.methodType(LEAF, long[].class, LEAF, METRIC))
                    .asType(MethodType.methodType(Object.class, Object.class, long[].class, Object.class, Object.class));
            CHILDREN = MethodHandles.privateLookupIn(SUBTREE, me).findGetter(SUBTREE, "children", NODE.arrayType())
                    .asType(MethodType.methodType(Object[].class, Object.class));
            DISTANCE = node.findVirtual(NODE, "distance", MethodType.methodType(long.class, long[].class))
                    .asType(MethodType.methodType(long.class, Object.class, long[].class));
            METRIC_DISTANCE = MethodHandles.privateLookupIn(METRIC, me).findVirtual(METRIC, "distance", MethodType.methodType(long.class, NODE, long[].class))
                    .asType(MethodType.methodType(long.class, Object.class, Object.class, long[].class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ClimateDistanceReuse() {
    }

    /**
     * Called by the site mixins with the metric object their guarded invokedynamic made (Node::distance): from then on
     * searches with that object take the walk. More than a few objects would mean a site makes new ones per call, which a
     * method reference never does: the table stops growing and the others keep vanilla's search.
     */
    public static void know(Object metric) {
        Object[] k = known;
        for (Object x : k) if (x == metric) return;
        if (metric == null) return;
        synchronized (ClimateDistanceReuse.class) {
            k = known;
            for (Object x : k) if (x == metric) return;
            if (k.length >= 8) return;
            Object[] n = Arrays.copyOf(k, k.length + 1);
            n[k.length] = metric;
            known = n;
        }
    }

    /** How many metric objects are registered (tests and the qualification probe). */
    public static int knownCount() {
        return known.length;
    }

    static boolean isKnown(Object metric) {
        for (Object x : known) if (x == metric) return true;
        return false;
    }

    /** The redirected root call of Climate.RTree.search: root.search(target, candidate, metric). */
    public static Object search(Object root, long[] target, Object candidate, Object metric) {
        try {
            if (enabled && root.getClass() == SUBTREE && isKnown(metric)) {
                if (!ANNOUNCE.get()) announce();
                Object leaf = walkRoot(root, target, candidate, metric);
                if (SHADOW) shadow(root, target, candidate, metric, leaf);
                return leaf;
            }
            return (Object) SEARCH.invokeExact(root, target, candidate, metric);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** The root (a SubTree): vanilla computes the warm-start leaf's distance first. */
    static Object walkRoot(Object root, long[] target, Object candidate, Object metric) throws Throwable {
        long best = candidate == null ? Long.MAX_VALUE : (long) DISTANCE.invokeExact(candidate, target);
        return walk(root, target, candidate, best, OUT.get(), metric);
    }

    /**
     * One SubTree, handed the caller's (leaf, best) where best is leaf's distance (MAX when leaf is null). Returns the leaf
     * vanilla's SubTree.search returns, with its distance in out[0].
     */
    static Object walk(Object subtree, long[] target, Object leaf, long best, long[] out, Object metric) throws Throwable {
        for (Object child : (Object[]) CHILDREN.invokeExact(subtree)) {
            long cd = (long) DISTANCE.invokeExact(child, target);
            if (best <= cd) continue;
            Object found;
            long fd;
            Class<?> c = child.getClass();
            if (c == LEAF) {                      // Leaf.search returns the leaf itself: its distance is cd
                found = child;
                fd = cd;
            } else if (c == SUBTREE) {
                found = walk(child, target, leaf, best, out, metric);
                // vanilla asks the metric about the returned leaf; a null leaf fails there, and does here the same way
                fd = found == null ? (long) DISTANCE.invokeExact((Object) null, target) : out[0];
            } else {                              // not a Minecraft 1.20.1 node class: vanilla's own code for this child
                found = (Object) SEARCH.invokeExact(child, target, leaf, metric);
                fd = child == found ? cd : (long) METRIC_DISTANCE.invokeExact(metric, found, target);
            }
            if (best <= fd) continue;
            best = fd;
            leaf = found;
        }
        out[0] = best;
        return leaf;
    }

    private static void shadow(Object root, long[] target, Object candidate, Object metric, Object leaf) throws Throwable {
        Object vanilla = (Object) SEARCH.invokeExact(root, target, candidate, metric);
        SHADOW_CHECKS.incrementAndGet();
        if (vanilla != leaf) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: vanilla_climate_search_distance_reuse shadow mismatch #{}: {} instead of {}", n, leaf, vanilla);
        }
    }

    private static void announce() {
        if (ANNOUNCE.compareAndSet(false, true))
            LOGGER.info("Bons and Furious: vanilla_climate_search_distance_reuse applies (climate tree searches reuse the distances they know){}",
                    SHADOW ? " - shadow verification on" : "");
    }
}
