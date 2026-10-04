package bons.furious.patch.modernfix_c2;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.slf4j.Logger;

/**
 * Bons and Furious switch modernfix_represented_tabs_index (ModernFix 5.27.77 with JEI 15.59, client only). No ModernFix
 * code is carried here.
 *
 * ModernFix's JEI-backed creative search (blast_search_trees, on by default) decides once per JEI session which creative
 * tabs JEI represents: JEIRuntimeCapturer.getRepresentedTabs asks, for EVERY JEI item stack and EVERY non-search tab,
 * tab.getSearchTabDisplayItems().contains(stack), and counts the hits per tab. Every one of those N x T probes hashes the
 * stack's item and NBT. Here the same counts are made with a hash index: one pass over the tabs records, per stack hash,
 * which tabs hold an element with that hash; then, for every JEI stack in the same order and every tab in the same order,
 * the original contains() runs exactly where it can be true, and is skipped where it cannot.
 *
 * Why identical: a tab's search-tab display items are the set vanilla (and Forge's buildContents) creates with
 * ItemStackLinkedSet.createTypeAndTagSet(): an ObjectLinkedOpenCustomHashSet with the TYPE_AND_TAG strategy, whose equals
 * (a == b, or both non-null, the same emptiness and ItemStack.isSameItemSameTags: same item, equal tags, compatible caps)
 * implies, for non-empty stacks, the same TYPE_AND_TAG hash (31 * (31 + item.hashCode()) + tag hash; equal tags have equal
 * hashes). So contains(stack) can only be true when some element has the stack's hash: the skipped calls are exactly ones
 * that answer false, and contains is a pure read when it answers false without reaching an equal element (its equals calls
 * stop at the item or tag comparison before Forge's capability check). Every call that can be true is made, in the
 * original order, so the counts, the order of addTo calls and every side effect of a successful comparison are the same.
 * Only tabs whose collection is that exact set class with that exact strategy and holds no empty stack are indexed; any
 * other tab, any empty JEI stack and any stack whose hash cannot be computed get the original contains() for every tab.
 * The getter is still called for every stack and tab, and a tab whose getter returns another object than the one indexed
 * gets the original contains(). If indexing fails, the original loop runs unchanged. After counting, the wrapped call returns
 * an empty list so ModernFix's own loop has nothing left to do; the rest of getRepresentedTabs (thresholds, result set)
 * is ModernFix's. Assumption, as in the original: nothing changes a display item or a JEI stack while the counts are made.
 *
 * SHADOW MODE for rigs: -Dbons_and_furious.modernfixRepresentedTabsIndex.shadow=true computes the indexed counts AND the
 * original N x T counts, compares them tab by tab (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN at most 20), and then lets
 * ModernFix's own loop run (shadow mode costs twice the original).
 */
public final class RepresentedTabsIndex {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.modernfixRepresentedTabsIndex=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modernfixRepresentedTabsIndex", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.modernfixRepresentedTabsIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Vanilla's TYPE_AND_TAG strategy (private in ItemStackLinkedSet): taken from a set createTypeAndTagSet() makes. */
    private static final Hash.Strategy<? super ItemStack> TYPE_AND_TAG = typeAndTag();
    private static volatile boolean announced, warned;
    /** Statistics of the last count (read by probes): contains calls made / skipped. */
    public static long lastCalls, lastSkipped;

    private RepresentedTabsIndex() {
    }

    private static Hash.Strategy<? super ItemStack> typeAndTag() {
        try {
            if (ItemStackLinkedSet.m_261170_() instanceof ObjectLinkedOpenCustomHashSet<ItemStack> s && s.getClass() == ObjectLinkedOpenCustomHashSet.class)
                return s.strategy();
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: modernfix_represented_tabs_index is inactive ({})", t.toString());
        }
        return null;
    }

    /**
     * In place of {@code runtimeHandle.getIngredientManager().getAllItemStacks()} inside getRepresentedTabs: the original
     * collection is passed in; the counts the original loop would make go into {@code counts}; the return value is what the
     * original loop then iterates (an empty list once counted, or the original collection to let it count itself).
     */
    public static Collection<ItemStack> count(Collection<ItemStack> all, List<CreativeModeTab> tabs, Reference2IntOpenHashMap<CreativeModeTab> counts) {
        if (!enabled || TYPE_AND_TAG == null || all == null || tabs == null || counts == null) return all;
        int n = tabs.size();
        Collection<?>[] indexed = new Collection<?>[n];
        Int2ObjectOpenHashMap<IntArrayList> byHash = new Int2ObjectOpenHashMap<>();
        try {
            for (int i = 0; i < n; i++) {
                Collection<ItemStack> c = tabs.get(i).m_261235_();
                if (c == null || c.getClass() != ObjectLinkedOpenCustomHashSet.class || ((ObjectLinkedOpenCustomHashSet<ItemStack>) c).strategy() != TYPE_AND_TAG) continue;
                boolean usable = true;
                for (ItemStack e : c) {
                    if (e == null || e.m_41619_()) {     // an empty stack equals every empty stack whatever its hash
                        usable = false;
                        break;
                    }
                }
                if (!usable) continue;
                for (ItemStack e : c) {
                    int h = TYPE_AND_TAG.hashCode(e);
                    IntArrayList list = byHash.get(h);
                    if (list == null) byHash.put(h, list = new IntArrayList(2));
                    if (list.isEmpty() || list.getInt(list.size() - 1) != i) list.add(i);
                }
                indexed[i] = c;
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: modernfix_represented_tabs_index could not index the creative tabs ({}); ModernFix's own count runs", t.toString());
            }
            return all;
        }
        if (SHADOW) {
            shadow(all, tabs, indexed, byHash);
            return all;
        }
        countInto(all, tabs, indexed, byHash, counts);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: modernfix_represented_tabs_index: JEI's represented creative tabs were counted with a hash index ({} of {} checks needed)",
                    lastCalls, lastCalls + lastSkipped);
        }
        return List.of();
    }

    private static void countInto(Collection<ItemStack> all, List<CreativeModeTab> tabs, Collection<?>[] indexed, Int2ObjectOpenHashMap<IntArrayList> byHash,
                                  Reference2IntOpenHashMap<CreativeModeTab> counts) {
        int n = tabs.size();
        long calls = 0, skipped = 0;
        for (ItemStack stack : all) {
            IntArrayList candidates = null;
            boolean hashed = false;
            if (stack != null && !stack.m_41619_()) {
                try {
                    candidates = byHash.get(TYPE_AND_TAG.hashCode(stack));
                    hashed = true;
                } catch (Throwable t) {
                    hashed = false;              // every tab gets the original contains(), which fails as it did
                }
            }
            int next = 0;
            for (int i = 0; i < n; i++) {
                CreativeModeTab tab = tabs.get(i);
                Collection<ItemStack> c = tab.m_261235_();
                if (hashed && c != null && c == indexed[i]) {
                    while (candidates != null && next < candidates.size() && candidates.getInt(next) < i) next++;
                    if (candidates == null || next >= candidates.size() || candidates.getInt(next) != i) {
                        skipped++;
                        continue;
                    }
                }
                calls++;
                if (c.contains(stack)) counts.addTo(tab, 1);
            }
        }
        lastCalls = calls;
        lastSkipped = skipped;
    }

    private static void shadow(Collection<ItemStack> all, List<CreativeModeTab> tabs, Collection<?>[] indexed, Int2ObjectOpenHashMap<IntArrayList> byHash) {
        Reference2IntOpenHashMap<CreativeModeTab> mine = new Reference2IntOpenHashMap<>(), reference = new Reference2IntOpenHashMap<>();
        countInto(all, tabs, indexed, byHash, mine);
        for (ItemStack stack : all) {
            for (int i = 0; i < tabs.size(); i++) {
                CreativeModeTab tab = tabs.get(i);
                if (tab.m_261235_().contains(stack)) reference.addTo(tab, 1);
            }
        }
        for (CreativeModeTab tab : tabs) {
            SHADOW_CHECKS.incrementAndGet();
            if (mine.getInt(tab) != reference.getInt(tab)) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: modernfix_represented_tabs_index SHADOW MISMATCH {}: tab {} counted {} by the index, {} by the original",
                        m, tab.m_40786_().getString(), mine.getInt(tab), reference.getInt(tab));
            }
        }
        LOGGER.info("Bons and Furious: modernfix_represented_tabs_index SHADOW: {} tabs compared, {} mismatches so far", SHADOW_CHECKS.get(), SHADOW_MISMATCHES.get());
    }
}
