package bons.furious.patch.pipez_logistics;

import com.mojang.logging.LogUtils;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.PipeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import org.slf4j.Logger;

/**
 * Bons and Furious switch pipez_sorted_connections_memo (Pipez 1.20.1-1.2.26, All Rights Reserved: no Pipez code is
 * carried; server side, incl. the integrated server). Not in our pack: public value.
 *
 * What Pipez does. UpgradeTileEntity.getSortedConnections(side, type) returns the pipe network's destinations in the
 * order the side's distribution wants: a new list sorted by distance through a stream (nearest first for NEAREST and
 * ROUND_ROBIN, furthest first for FURTHEST), or a shuffled copy for RANDOM. Every item transfer, every fluid and gas pipe
 * tick of an extracting side and every energy push into a pipe (EnergyPipeType.receive) asks again, although the
 * network's connection list only changes when pipes change (PipeTileEntity.connectionCache is then replaced or cleared).
 *
 * What the switch does. Per tile it keeps the last list it sorted: the list object, its elements in their order, the
 * distribution and the sorted order. When the tile's current list is that object with the same elements in the same order
 * and the distribution is the same, the call returns a new ArrayList of the kept order instead of sorting again. The
 * result is the original's: Java's object sort and the stream's sorted() are both stable, both order by
 * Connection.getDistance (a final field), so equal distances keep the list order; the caller gets a new mutable ArrayList
 * as before. The call still asks getDistribution and getConnections once each, as the original does (getConnections may
 * rebuild the network list; the new list is a new object, so it is sorted again). RANDOM, and any list holding null or a
 * Connection subclass (which could override getDistance), run the original.
 *
 * -Dbons_and_furious.pipezSortedConnectionsMemo=false switches it off at run time.
 * -Dbons_and_furious.pipezSortedConnectionsMemo.shadow=true (verification runs only): the original runs as well (a nested
 * call that this helper leaves alone) and must give an equal list; SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN.
 */
public final class SortedConnections {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.pipezSortedConnectionsMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.pipezSortedConnectionsMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;
    private static final Comparator<PipeTileEntity.Connection> NEAR_FIRST = Comparator.comparingInt(PipeTileEntity.Connection::getDistance);
    private static final Comparator<PipeTileEntity.Connection> FAR_FIRST = (a, b) -> Integer.compare(b.getDistance(), a.getDistance());

    private SortedConnections() {
    }

    /** HEAD of UpgradeTileEntity.getSortedConnections: the sorted list, or null to run the original. */
    public static List<PipeTileEntity.Connection> sorted(UpgradeTileEntity tile, SortedConnectionsState state, Direction side, PipeType<?> type) {
        if (!enabled) {
            return null;
        }
        UpgradeTileEntity.Distribution distribution = tile.getDistribution(side, type);
        if (distribution == UpgradeTileEntity.Distribution.RANDOM) {
            return null;
        }
        List<PipeTileEntity.Connection> source = tile.getConnections();
        PipeTileEntity.Connection[] order = state.bons$sortResult();
        if (order == null || state.bons$sortSource() != source || state.bons$sortDistribution() != distribution
                || !sameElements(state.bons$sortSourceElements(), source)) {
            PipeTileEntity.Connection[] elements = plainElements(source);
            if (elements == null) {
                state.bons$sortRemember(null, null, null, null);
                return null;
            }
            order = elements.clone();
            Arrays.sort(order, distribution == UpgradeTileEntity.Distribution.FURTHEST ? FAR_FIRST : NEAR_FIRST);
            state.bons$sortRemember(source, elements, distribution, order);
        } else if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: pipez_sorted_connections_memo applies (an unchanged pipe network is not sorted again){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        ArrayList<PipeTileEntity.Connection> out = new ArrayList<>(order.length);
        for (PipeTileEntity.Connection c : order) {
            out.add(c);
        }
        return out;
    }

    /** Shadow mode: the original's list (from a nested call) against ours. */
    public static void compare(List<PipeTileEntity.Connection> original, List<PipeTileEntity.Connection> ours) {
        SHADOW_CHECKS.incrementAndGet();
        if (!original.equals(ours) || original.getClass() != ours.getClass()) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) {
                LOGGER.warn("Bons and Furious: pipez_sorted_connections_memo shadow mismatch #{}: original {} vs kept {}", m, original, ours);
            }
        }
    }

    private static boolean sameElements(PipeTileEntity.Connection[] elements, List<PipeTileEntity.Connection> source) {
        if (elements == null || elements.length != source.size()) {
            return false;
        }
        for (int i = 0; i < elements.length; i++) {
            if (source.get(i) != elements[i]) {
                return false;
            }
        }
        return true;
    }

    /** The list's elements, or null when one is null or not exactly a Connection. */
    private static PipeTileEntity.Connection[] plainElements(List<PipeTileEntity.Connection> source) {
        int n = source.size();
        PipeTileEntity.Connection[] a = new PipeTileEntity.Connection[n];
        for (int i = 0; i < n; i++) {
            PipeTileEntity.Connection c = source.get(i);
            if (c == null || c.getClass() != PipeTileEntity.Connection.class) {
                return null;
            }
            a[i] = c;
        }
        return a;
    }
}
