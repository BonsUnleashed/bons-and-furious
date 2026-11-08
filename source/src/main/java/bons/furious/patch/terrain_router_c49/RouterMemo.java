package bons.furious.patch.terrain_router_c49;

import bons.pure.terrain.DensityAudit;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch terrain_router_shared_node_memo (Minecraft 1.21.1 world generation, both sides). Mojang code is
 * no-copy: injectors only. Ours, idea from Collections Of Optimizations (vanilla.reuseNoiseChunkDensityGraph), idea text
 * only.
 *
 * What vanilla does: every NoiseChunk (one per generated chunk, and one per single-column height or column query: structure
 * placement, surface estimates, spawn searches) starts by mapping its RandomState's noise router with its wrap visitor
 * (router.mapAll(this::wrap)): NoiseRouter.mapAll maps each of its 15 fields with the same visitor, every node is rebuilt
 * from its mapped children and handed to wrap, which looks it up in the chunk's wrap table by deep equality
 * (computeIfAbsent: records hash and compare their whole subtree). Registered density functions that the router uses more
 * than once (the overworld's continents, erosion, depth, factor, jaggedness, sloped cheese and their splines are referenced
 * from several router fields and from each other) sit behind HolderHolder nodes, and RandomState's own wiring already made
 * each of them one shared object; the first pass still maps such a shared subgraph again, node by node with its deep
 * lookups, every time it is referenced.
 *
 * What the switch does: during that first pass only (our visitor wraps NoiseChunk's wrap visitor and is passed on
 * unchanged to every node), the result of a HolderHolder node is remembered by the node's identity and handed back when
 * the same node is reached again. Why identical: vanilla's repeated mapping of the same subgraph rebuilds equal nodes,
 * which every lookup in the wrap table resolves to the objects stored the first time (the table only grows, and the
 * HolderHolder's own lookup returns the first result's value), so the second answer is the very object of the first and
 * the repeated walk inserts nothing; leaving it out changes no object, cache or table entry. This holds for
 * the vanilla density function types (records of final fields over wired noises) and the reviewed ones that DensityAudit
 * accepts; a router holding any other type (another mod's function, which may build fresh objects or carry state) is
 * audited once per RandomState and keeps the full first pass. A router in which no HolderHolder is reached twice (the
 * nether and the end: found by one counting walk per RandomState with a visitor that changes nothing) keeps the plain
 * visitor, so it pays nothing. Later passes, other visitors and RandomState's own wiring are not touched.
 *
 * Flag: -Dbons_and_furious.terrainRouterSharedNodeMemo=false maps every reference again at runtime.
 */
public final class RouterMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.terrainRouterSharedNodeMemo", "true"));
    /** Counters for probes and the harness. */
    public static final LongAdder PASSES = new LongAdder(), HITS = new LongAdder(), REFUSED = new LongAdder();
    /** Verdicts per RandomState: the router is refused, holds no shared node (nothing to remember), or shares nodes. */
    public static final byte REFUSED_ROUTER = 0, NOT_SHARED = 1, SHARED = 2;
    /** One verdict per RandomState (identity: RandomState keeps Object's equals). */
    private static final Map<RandomState, Byte> VERDICT = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean announced;

    private RouterMemo() {
    }

    /** In place of the first router.mapAll visitor of NoiseChunk's constructor (@ModifyArg). */
    public static DensityFunction.Visitor firstPass(DensityFunction.Visitor wrap, RandomState random) {
        if (!enabled || wrap == null || random == null || wrap instanceof Memo) return wrap;
        byte v = verdictOf(random);
        if (v == REFUSED_ROUTER) {
            REFUSED.increment();
            return wrap;
        }
        if (v == NOT_SHARED) return wrap;                 // no node is reached twice: nothing to remember, no memo object
        PASSES.increment();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: terrain_router_shared_node_memo applies (a noise router's shared density functions are mapped once per new NoiseChunk)");
        }
        return new Memo(wrap);
    }

    /** The router's verdict, computed once per RandomState: the type audit, then one counting walk for shared nodes. */
    public static byte verdictOf(RandomState random) {
        Byte v = VERDICT.get(random);
        if (v != null) return v;
        NoiseRouter router = random.router();
        String refusal = audit(router);
        byte verdict;
        if (refusal != null) {
            verdict = REFUSED_ROUTER;
            LOGGER.info("Bons and Furious: terrain_router_shared_node_memo keeps the full first density pass for a noise router: {}", refusal);
        } else {
            // a visitor that changes nothing: the audited types rebuild equal nodes without side effects, and every
            // HolderHolder reached reports itself to holder() below
            Counter c = new Counter();
            router.mapAll(c);
            verdict = c.shared ? SHARED : NOT_SHARED;
        }
        VERDICT.put(random, verdict);
        return verdict;
    }

    /** The counting walk's visitor: applies nothing; holder() counts the HolderHolder nodes by identity. */
    static final class Counter implements DensityFunction.Visitor {
        final Map<Object, Integer> seen = new IdentityHashMap<>();
        boolean shared;

        @Override
        public DensityFunction apply(DensityFunction f) {
            return f;
        }
    }

    /** Null when every function reachable from the router's fields is a type DensityAudit accepts, else the reason. */
    static String audit(NoiseRouter router) {
        try {
            List<Field> fields = DensityAudit.fields(NoiseRouter.class);
            Object[] roots = new Object[fields.size()];
            for (int i = 0; i < roots.length; i++) roots[i] = fields.get(i).get(router);
            return DensityAudit.refusal(roots);
        } catch (Throwable t) {
            return "audit failed: " + t;
        }
    }

    /** NoiseChunk's wrap visitor plus the pass's memo: applies and visits exactly as the wrapped visitor does. */
    public static final class Memo implements DensityFunction.Visitor {
        final DensityFunction.Visitor wrap;
        final Map<Object, DensityFunction> done = new IdentityHashMap<>();

        Memo(DensityFunction.Visitor wrap) {
            this.wrap = wrap;
        }

        @Override
        public DensityFunction apply(DensityFunction f) {
            return this.wrap.apply(f);
        }

        @Override
        public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
            return this.wrap.visitNoise(noise);
        }
    }

    /** In place of HolderHolder.mapAll (MixinExtras @WrapMethod): only our first-pass visitor remembers. */
    public static DensityFunction holder(Object self, DensityFunction.Visitor visitor, Operation<DensityFunction> original) {
        if (visitor instanceof Counter c) {
            if (c.seen.merge(self, 1, Integer::sum) > 1) c.shared = true;
            return original.call(visitor);
        }
        if (!(visitor instanceof Memo m)) return original.call(visitor);
        DensityFunction kept = m.done.get(self);
        if (kept != null) {
            HITS.increment();
            return kept;
        }
        DensityFunction result = original.call(visitor);
        m.done.put(self, result);
        return result;
    }

    /** For the harness: forget the verdicts. */
    public static void reset() {
        VERDICT.clear();
    }
}
