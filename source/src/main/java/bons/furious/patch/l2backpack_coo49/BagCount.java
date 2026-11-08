package bons.furious.patch.l2backpack_coo49;

import dev.xkmc.l2backpack.init.registrate.LBItems;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2backpack_bag_count_from_nbt (L2 Backpack, LGPL-2.1; 1.21.1 tested build 3.1.5; both sides;
 * exact variant). None of L2 Backpack's code is carried. Ours, idea from Collections Of Optimizations
 * (l2backpack.leanBagCount), idea text only.
 *
 * What L2 Backpack does: AbstractBag.getOccupied(stack) counts the used slots of a bag by building its whole content
 * (getContent: a new list of getInvSize(stack) = 64 x upgrade-factor EMPTY stacks, then the stack's bag_content component,
 * an ItemContainerContents, copies every stored stack into it: copyInto sets slot i to a copy of stored stack i, or EMPTY
 * past the stored ones) and counting the non-empty stacks. It runs twice per bag icon per frame (the count decoration
 * BagCountDeco and the open / closed model predicate isFilled), for the tooltip and on every insert check, so a full bag on
 * screen copies hundreds of stacks per frame.
 *
 * What the switch does (BagOccupiedMixin, a cancellable HEAD inject): the same getInvSize call, then the same component
 * read (stack.get(LBItems.BAG_CONTENT)); no component answers 0, as the list of EMPTY stacks counts 0; otherwise the
 * stored stacks are counted in place (ItemContainerContents.nonEmptyItems). Same count: a copy is empty exactly when the
 * stored stack is (ItemStack.copy answers EMPTY for an empty stack and keeps item and count otherwise), and when the
 * stored contents have no more slots than the bag, the list holds a copy of every stored stack and EMPTY elsewhere. Stored
 * contents with more slots than the bag (copyInto drops the ones past the list), a negative size (the list constructor
 * throws) and a bag class that overrides getContent run the shipped method.
 *
 * Exact variant (the one internal difference): the stored stacks are no longer copied just to be counted, so what making a
 * copy does besides answering the count waits until the bag is really used (opened, emptied, inserted into): every copy
 * runs its item's verifyComponentsAfterLoad (vanilla: a player head whose profile is not resolved yet starts a profile
 * lookup for its throwaway copy; a mod item may override the method too).
 *
 * Ported to 1.21.1: the bag's items are a data component of decoded stacks, so the 1.20.1 entry parse (ids, the remembered
 * parsed ids, Count bytes, Slot bytes) has nothing left to do and is gone; the 1.20.1 side effects of building a stack
 * (Forge capability attach, the Damage tag normalisation) no longer exist, the copy's verifyComponentsAfterLoad is the
 * one left.
 *
 * Flag: -Dbons_and_furious.l2backpackBagCountFromNbt=false runs the shipped count. SHADOW MODE (rigs):
 * -Dbons_and_furious.l2backpackBagCountFromNbt.shadow=true also runs the shipped count (getContent) on every call, answers
 * with it, counts SHADOW_CHECKS / SHADOW_MISMATCHES and logs the first 20 mismatches.
 */
public final class BagCount {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2backpackBagCountFromNbt", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.l2backpackBagCountFromNbt.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** "Run the shipped method". */
    public static final int UNKNOWN = Integer.MIN_VALUE;
    private static final String ABSTRACT_BAG = "dev.xkmc.l2backpack.content.bag.AbstractBag";
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return c.getMethod("getContent", ItemStack.class).getDeclaringClass().getName().equals(ABSTRACT_BAG);
            } catch (Throwable t) {
                return false;
            }
        }
    };
    private static volatile boolean announced;

    private BagCount() {
    }

    /** The bag class keeps AbstractBag's getContent (so getOccupied counts what that method would build). The last class
     *  seen each way is kept for an identity check before the ClassValue lookup. */
    public static boolean plain(Class<?> bagClass) {
        if (bagClass == lastPlain) return true;
        if (bagClass == lastOwn) return false;
        boolean p = PLAIN.get(bagClass);
        if (p) lastPlain = bagClass;
        else lastOwn = bagClass;
        return p;
    }

    private static Class<?> lastPlain, lastOwn;

    /** The shipped getOccupied's answer for a bag whose getInvSize answered size (called after that), or UNKNOWN. */
    public static int count(int size, ItemStack stack) {
        if (size < 0) return UNKNOWN;
        Object stored = stack.get(LBItems.BAG_CONTENT);
        if (stored == null) return 0;
        if (!(stored instanceof ItemContainerContents contents) || contents.getSlots() > size) return UNKNOWN;
        int used = 0;
        for (ItemStack s : contents.nonEmptyItems()) used++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2backpack_bag_count_from_nbt applies (L2 Backpack counts a bag's used slots from its stored contents without copying them)");
        }
        return used;
    }

    /** Shadow mode: the shipped count from the shipped content list; answers it. */
    public static int shadow(int ours, List<ItemStack> shipped) {
        int n = 0;
        for (ItemStack s : shipped) if (!s.isEmpty()) n++;
        SHADOW_CHECKS.incrementAndGet();
        if (n != ours) {
            long k = SHADOW_MISMATCHES.incrementAndGet();
            if (k <= 20) LOGGER.warn("Bons and Furious: l2backpack_bag_count_from_nbt shadow mismatch #{}: {} vs {}", k, ours, n);
        }
        return n;
    }
}
