package bons.furious.patch.climate_last_result;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_climate_last_result_weak (Minecraft 1.20.1, Forge 47.4.16; both sides: world generation
 * and every biome lookup). [FIX] A climate search tree no longer keeps a closed world's registries in memory.
 *
 * Climate.RTree (the nearest-climate-point search behind every multi-noise biome lookup, also used by TerraBlender's
 * regions) remembers, per tree and per thread, the leaf the thread found last: the ThreadLocal in its field f_186911_.
 * RTree.search (m_186930_) starts from that leaf (it seeds the tie-breaking) and stores the new one. A ThreadLocal's value
 * lives in each thread's own map and is held strongly there; when the tree and its ThreadLocal become unreachable the
 * entry goes stale but keeps its value until later ThreadLocal use of that same thread happens to clean that slot. The
 * game's worker threads live as long as the JVM (Worker-Main, the common pool, C2ME's and Distant Horizons' pools), and a
 * leaf's value is a biome Holder whose registry reaches the world's whole worldgen registry set: after a singleplayer
 * world is closed, every worker that searched one of its trees keeps those registries in memory.
 *
 * Each tree's slot is now this ThreadLocal, which keeps the leaf through a weak reference. A tree holds every one of its
 * leaves strongly (they are its nodes), so while the tree lives the reference is never cleared and every search starts
 * from exactly the leaf vanilla's slot would hold: the same results, ties included. Once the tree is unreachable nobody
 * can search it, and the leaf, its biome and the registries can be collected; the stale entry keeps only an empty
 * reference. Storing the leaf that is already referenced keeps the existing reference, so repeated answers allocate nothing.
 *
 * Runtime switch -Dbons_and_furious.climateLastResultWeak=false: new leaves are stored as vanilla stores them (strongly).
 * -Dbons_and_furious.climateLastResultWeak.shadow=true (verification runs): every reference also keeps its leaf strongly
 * and every read compares the two (SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 mismatches logged; expect 0).
 */
public final class WeakLastResult extends ThreadLocal<Object> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.climateLastResultWeak", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.climateLastResultWeak.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** The only kind of reference stored in a thread's map by this class. */
    static final class Ref extends WeakReference<Object> {
        /** SHADOW only: the leaf as vanilla's slot holds it. */
        final Object strong;

        Ref(Object leaf) {
            super(leaf);
            this.strong = SHADOW ? leaf : null;
        }
    }

    /** For RTree's constructor (the mixin): a weak slot while the switch is on, else the slot vanilla made. */
    public static ThreadLocal<?> slot(ThreadLocal<?> vanilla) {
        if (!enabled) return vanilla;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_climate_last_result_weak applies (climate search trees keep each thread's last leaf weakly, so a closed world's biomes can be freed){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return new WeakLastResult();
    }

    @Override
    public Object get() {
        Object v = super.get();
        if (!(v instanceof Ref r)) return v;             // nothing stored yet (null), or a leaf stored with the switch off
        Object leaf = r.get();
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (leaf != r.strong) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: vanilla_climate_last_result_weak shadow mismatch #{}: {} instead of {}", n, leaf, r.strong);
            }
        }
        return leaf;
    }

    @Override
    public void set(Object leaf) {
        if (!enabled || leaf == null) {
            super.set(leaf);
            return;
        }
        Object v = super.get();
        if (v instanceof Ref r && r.get() == leaf) return;  // the same leaf again: the reference already points at it
        super.set(new Ref(leaf));
    }
}
