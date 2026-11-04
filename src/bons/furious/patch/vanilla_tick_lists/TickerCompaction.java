package bons.furious.patch.vanilla_tick_lists;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_block_entity_ticker_compaction (Minecraft 1.20.1 on Forge 47.4.28, both sides: every
 * level ticks its block entities). Kind: exact variant. Idea: Collections Of Optimizations (vanilla.scheduleClientBlockEntities,
 * the vanilla half), idea text only; the one-pass removal by position is ours. No Mojang code here.
 *
 * What the game does. Level.tickBlockEntities (m_46463_) walks its ticker list (f_151512_, an ArrayList) with an
 * iterator; a ticker whose block entity is gone (isRemoved, e.g. every ticker of a chunk that unloaded: its tickers are
 * rebound to the removed NULL ticker) is taken out with Iterator.remove(). ArrayList's remove moves the whole tail of the
 * list down by one, so a pass that removes k of n tickers moves about k * n / 2 references: after a teleport or a
 * dimension change, when hundreds of chunks unload at once, thousands of removals from a list of 10-20 thousand.
 *
 * What the switch does. When the list's class is exactly java.util.ArrayList, the walk uses the list's own list
 * iterator (ArrayList.listIterator(): the same next / hasNext and the same ConcurrentModificationException check as its
 * iterator(), plus the index of the element it returned last). The loop's Iterator.remove() keeps that index and the
 * ticker instead of removing it, and when the method returns the kept positions leave the list in one pass: the
 * survivors move down in their order and the tail is cut once. That is exactly the list stock leaves (the same
 * positions removed, the survivors in the same order, duplicates included), every ticker is asked and ticked exactly as
 * before, and a pass without removals runs the original loop. Before the cut every kept position is checked to still
 * hold its ticker (code that only appends to the list cannot move them; if anything else changed the list in between,
 * the kept tickers are removed by identity instead and FALLBACKS counts it). The one internal difference (exact
 * variant): a removed ticker leaves the list when the method returns instead of the moment the loop finds it, so code
 * reading the list in the middle of the pass would still see it (nothing in vanilla or Forge does). A pass cut short by
 * an exception: its kept tickers leave the list when the next pass starts. Any other list class, the switch off, or an
 * iterator the walk did not get from us removes at once as before.
 *
 * -Dbons_and_furious.blockEntityTickerCompaction=false switches it off at run time (from the next pass).
 */
public final class TickerCompaction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.blockEntityTickerCompaction", "true"));
    /** Cuts whose kept positions no longer held their tickers (the list changed between the walk and the cut). */
    public static final AtomicLong FALLBACKS = new AtomicLong();
    private static volatile boolean announced;

    private TickerCompaction() {
    }

    /** The removals of one level's current pass. Used on that level's ticking thread only. */
    public static final class Marks {
        private List<Object> list;
        private Iterator<?> walk;
        private int count;
        private int[] positions = new int[16];
        private Object[] tickers = new Object[16];

        /**
         * The walk's List.iterator() call. Kept removals of a pass that was cut short go first; then this pass defers
         * its removals (the list's list iterator) when the switch is on and the list is exactly an ArrayList, else the
         * list's own iterator().
         */
        @SuppressWarnings("unchecked")
        public <T> Iterator<T> start(List<T> list) {
            if (this.count > 0) {
                this.finish();
            }
            if (enabled && list != null && list.getClass() == ArrayList.class) {
                Iterator<T> it = list.listIterator();
                this.list = (List<Object>) list;
                this.walk = it;
                return it;
            }
            this.list = null;
            this.walk = null;
            return list.iterator();
        }

        /** The walk's Iterator.remove(), for the ticker the iterator returned last. */
        public void remove(Iterator<?> iterator) {
            if (iterator != this.walk || this.walk == null) {
                iterator.remove();
                return;
            }
            int at = ((ListIterator<?>) iterator).previousIndex();
            if (this.count == this.positions.length) {
                this.positions = Arrays.copyOf(this.positions, this.count * 2);
                this.tickers = Arrays.copyOf(this.tickers, this.count * 2);
            }
            this.positions[this.count] = at;
            this.tickers[this.count++] = this.list.get(at);
        }

        /** The end of tickBlockEntities: every kept position leaves the list in one pass. */
        public void finish() {
            this.walk = null;
            int n = this.count;
            if (n == 0) {
                return;
            }
            List<Object> l = this.list;
            int size = l.size();
            boolean exact = true;
            for (int k = 0; k < n && exact; k++) {
                int p = this.positions[k];
                exact = p < size && (k == 0 || p > this.positions[k - 1]) && l.get(p) == this.tickers[k];
            }
            if (exact) {
                int w = this.positions[0];
                int k = 1;
                for (int r = w + 1; r < size; r++) {
                    if (k < n && this.positions[k] == r) {
                        k++;
                    } else {
                        l.set(w++, l.get(r));
                    }
                }
                l.subList(w, size).clear();
            } else {
                Set<Object> gone = Collections.newSetFromMap(new IdentityHashMap<>());
                gone.addAll(Arrays.asList(this.tickers).subList(0, n));
                l.removeIf(gone::contains);
                if (FALLBACKS.getAndIncrement() == 0) {
                    LOGGER.info("Bons and Furious: vanilla_block_entity_ticker_compaction found the ticker list changed between the walk and the cut; the {} removed tickers were taken out by identity", n);
                }
            }
            Arrays.fill(this.tickers, 0, n, null);
            this.count = 0;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_block_entity_ticker_compaction applies ({} removed block entity tickers left the list in one pass)", n);
            }
        }

        /** For the proof harness: removals kept and not yet cut. */
        public int kept() {
            return this.count;
        }
    }
}
