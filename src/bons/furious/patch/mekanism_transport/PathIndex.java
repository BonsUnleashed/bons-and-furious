package bons.furious.patch.mekanism_transport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

/**
 * Bons and Furious switch mekanism_transporter_path_index (Mekanism 1.20.1-10.4.13.69; logical server).
 *
 * What Mekanism does. A logistical-transporter item (TransporterStack) carries its route as pathToTarget, an ArrayList of
 * BlockPos from the destination back to where it entered. Every step asks where the transporter it is in sits on that
 * route: isFinal, getNext, getPrev, write (packet) and writeToUpdateTag each call pathToTarget.indexOf(tilePos), a linear
 * scan with BlockPos.equals, several times per item per tick, so a long route costs O(length) per call and O(length^2)
 * per trip.
 *
 * What the switch does. Those five indexOf calls are answered from a first-occurrence index (position -> smallest index,
 * built with putIfAbsent in list order, so duplicates answer like indexOf) kept per TransporterStack for the list it was
 * built from; it is rebuilt when pathToTarget is another list object or the list's size differs. Lookups use the
 * argument's own equals/hashCode as indexOf does (o.equals(element)) and are served only when the argument is exactly a
 * BlockPos or MutableBlockPos (vanilla Vec3i equals/hashCode) and every element is null or exactly a BlockPos (immutable);
 * short routes (< MIN_SIZE), other list classes, a null argument and anything else run the original indexOf. Why exact:
 * Mekanism 10.4.13 never changes a route list in place (audit: pathToTarget is private and only replaced by setPath; the
 * lists come from Destination's own copy or a new pathfinder list; the only outside reader, LogisticalTransporterBase.
 * onUpdateServer, only reads; no other mod in the pack references TransporterStack); every method involved is guarded.
 *
 * -Dbons_and_furious.mekanismTransporterPathIndex=false switches it off at run time.
 * -Dbons_and_furious.mekanismTransporterPathIndex.shadow=true (verification runs only): indexOf runs as before and its
 * answer is compared with the index (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class PathIndex {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mekanismTransporterPathIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mekanismTransporterPathIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /**
     * Routes shorter than this are scanned: per trip the index loses below ~12 entries (4: 0.65x, 8: 0.74x), breaks even
     * at 12 and wins from 16 (1.16x; 20: 1.35x; 50: 3.2x) - harness, Core Ultra 9 285K.
     */
    public static int MIN_SIZE = 16;
    private static volatile boolean announced;

    /** The index of one TransporterStack (a field its mixin adds). Server thread only. */
    public static final class Slot {
        List<?> list;
        int size;
        boolean usable;
        Object2IntOpenHashMap<Object> index;
    }

    /** Implemented by the TransporterStack mixin. */
    public interface Holder {
        Slot bons$pathIndexSlot();
    }

    private PathIndex() {
    }

    /** TransporterStack's pathToTarget.indexOf(tilePos) calls. */
    public static int indexOf(Object stack, List<?> list, Object o, Operation<Integer> original) {
        if (!enabled || o == null || list == null || list.getClass() != ArrayList.class || list.size() < MIN_SIZE
                || o.getClass() != BlockPos.class && o.getClass() != BlockPos.MutableBlockPos.class) return original.call(list, o);
        Slot s = ((Holder) stack).bons$pathIndexSlot();
        if (s.list != list || s.size != list.size()) build(s, list);
        if (!s.usable) return original.call(list, o);
        int i = s.index.getInt(o);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: mekanism_transporter_path_index applies (transporter items find their place on long routes by index){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            int v = original.call(list, o);
            SHADOW_CHECKS.incrementAndGet();
            if (v != i) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: mekanism_transporter_path_index shadow mismatch #{}: indexOf {} index {} ({} entries)", m, v, i, list.size());
            }
            return v;
        }
        return i;
    }

    private static void build(Slot s, List<?> list) {
        int n = list.size();
        s.list = list;
        s.size = n;
        s.usable = false;
        s.index = null;
        Object2IntOpenHashMap<Object> m = new Object2IntOpenHashMap<>(n);
        m.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            Object e = list.get(i);
            if (e == null) continue;                    // indexOf(non-null) never matches null
            if (e.getClass() != BlockPos.class) return;  // only immutable vanilla positions are indexed
            m.putIfAbsent(e, i);
        }
        s.index = m;
        s.usable = true;
    }
}
