package bons.furious.patch.l2backpack_coo49;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2backpack_bag_count_from_nbt (L2 Backpack 2.6.0, both sides; exact variant). L2 Backpack is
 * LGPL-2.1; none of its code is carried. Ours, idea from Collections Of Optimizations (l2backpack.leanBagCount), idea text
 * only.
 *
 * What L2 Backpack does: AbstractBag.getOccupied(stack) counts the used slots of a bag by building its whole content
 * (getContent: a list of 64 x upgrade-factor stacks, then ContainerHelper.loadAllItems, which makes a real ItemStack from
 * every stored entry whose "Slot" byte & 255 is inside the list; a later entry for the same slot replaces an earlier one)
 * and counting the non-empty stacks. It runs twice per bag icon per frame (the count decoration and the open / closed model
 * predicate isFilled) and on every insert check, so a full bag on screen rebuilds hundreds of stacks per frame.
 *
 * What the switch does (BagOccupiedMixin, a cancellable HEAD inject): the same reads in the same order (getInvSize, which
 * creates the root tag on a tagless bag as shipped; getOrCreateTagElement("BlockEntityTag"), also as shipped; the "Items"
 * list of compounds), then per entry the slot with the same rule and "occupied" exactly as an ItemStack made from the entry
 * would be non-empty: its "id" parses as a resource location (an id that does not parse makes ItemStack.of answer EMPTY),
 * names a registered item other than air (an unknown id gives air), and its "Count" byte is above 0; the last entry per slot
 * decides. Same count.
 * Exact variant (the one internal difference): the stored stacks are no longer built just to be counted, so what building
 * them does besides answering the count waits until the bag is really used (opened, emptied, inserted into, its tooltip
 * shown): Forge's capability attach event for each throwaway stack, the item's own tag check after loading (vanilla: player
 * heads resolve their owner), the damage-value normalisation of stored damageable items (it writes Damage:0 into a stored
 * item's tag that has none), the debug log line for an unloadable entry, and an entry whose construction would throw inside
 * another mod's capability code counts as used instead of empty. A bag class that overrides getContent, or a list larger
 * than 4,096 slots (or an overflowing upgrade factor), runs the shipped method.
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
    private static final int MAX_SLOTS = 4096;
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
        if (size <= 0 || size > MAX_SLOTS) return UNKNOWN;
        CompoundTag holder = stack.m_41698_("BlockEntityTag");
        if (!holder.m_128441_("Items")) return 0;
        ListTag items = holder.m_128437_("Items", 10);
        long w0 = 0, w1 = 0, w2 = 0, w3 = 0;
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.m_128728_(i);
            int slot = entry.m_128445_("Slot") & 255;
            if (slot >= size) continue;
            long bit = 1L << (slot & 63);
            boolean used = occupied(entry);
            switch (slot >>> 6) {
                case 0 -> w0 = used ? w0 | bit : w0 & ~bit;
                case 1 -> w1 = used ? w1 | bit : w1 & ~bit;
                case 2 -> w2 = used ? w2 | bit : w2 & ~bit;
                default -> w3 = used ? w3 | bit : w3 & ~bit;
            }
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2backpack_bag_count_from_nbt applies (L2 Backpack counts a bag's used slots from its stored list)");
        }
        return Long.bitCount(w0) + Long.bitCount(w1) + Long.bitCount(w2) + Long.bitCount(w3);
    }

    /** An ItemStack made from this entry would be non-empty (same id parse, same registry lookup, same Count byte). */
    static boolean occupied(CompoundTag entry) {
        Object id = parsed(entry.m_128461_("id"));
        if (id == UNPARSABLE) return false;
        Item item = BuiltInRegistries.f_257033_.m_7745_((ResourceLocation) id);
        return item != Items.f_41852_ && entry.m_128445_("Count") > 0;
    }

    /** ResourceLocation.tryParse(id), kept per id text (the parse is a pure function of the text and ResourceLocation is
     *  immutable; stored bags repeat the same few ids every frame). Bounded: the table is emptied when it reaches MAX_IDS
     *  entries. The registry lookup is not kept: Forge can remap a name when a world loads. */
    static Object parsed(String id) {
        Object p = IDS.get(id);
        if (p == null) {
            ResourceLocation rl = ResourceLocation.m_135820_(id);
            p = rl == null ? UNPARSABLE : rl;
            if (IDS.size() >= MAX_IDS) IDS.clear();
            IDS.put(id, p);
        }
        return p;
    }

    private static final Object UNPARSABLE = new Object();
    private static final int MAX_IDS = 4096;
    private static final ConcurrentHashMap<String, Object> IDS = new ConcurrentHashMap<>();

    /** Shadow mode: the shipped count from the shipped content list; answers it. */
    public static int shadow(int ours, List<ItemStack> shipped) {
        int n = 0;
        for (ItemStack s : shipped) if (!s.m_41619_()) n++;
        SHADOW_CHECKS.incrementAndGet();
        if (n != ours) {
            long k = SHADOW_MISMATCHES.incrementAndGet();
            if (k <= 20) LOGGER.warn("Bons and Furious: l2backpack_bag_count_from_nbt shadow mismatch #{}: {} vs {}", k, ours, n);
        }
        return n;
    }
}
