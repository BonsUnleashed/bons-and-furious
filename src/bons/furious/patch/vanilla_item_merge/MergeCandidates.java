package bons.furious.patch.vanilla_item_merge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.extensions.IForgeItem;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_item_merge_candidates (Minecraft 1.20.1 on Forge 47.4.16; server side, including the
 * integrated server; merges never run on a client level). SRG member names.
 *
 * What vanilla does. ItemEntity.mergeWithNeighbours (m_32069_; every 40 ticks for a resting item entity, every 2 ticks
 * for one that moved to another block, and once after a dimension change) collects every item entity in its box
 * inflated by 0.5 horizontally whose predicate `e != this && e.isMergable()` holds (Level.getEntitiesOfClass, m_6443_),
 * in the entity sections' order, and then walks that list: for each entry that is still isMergable it calls
 * tryToMerge(entry), which merges only when Objects.equals(targets) && areMergable(thisStack, entryStack), and it stops
 * as soon as this entity is removed. In a pile, every merge attempt runs isMergable twice (collection and loop) and
 * tryToMerge once for every item entity of every other item around it.
 *
 * What the switch does. The predicate handed to getEntitiesOfClass is wrapped by a Filter that rejects a candidate when
 * tryToMerge would reject it anyway (areMergable(thisStack, entryStack) is false) by one of its first two tests:
 *  1. other item: entryStack.getItem() != thisStack.getItem(); areMergable's first test is entryStack.is(thisStack
 *     .getItem()), the same comparison (AIR for empty stacks on either side);
 *  2. too many items: entryStack.getCount() + thisStack.getCount() > entryStack.getMaxStackSize(), areMergable's second
 *     test, used only while it provably still holds when the loop reaches the entry (below).
 * Everything else goes through vanilla's predicate unchanged, so the list holds vanilla's entries minus rejected ones,
 * in vanilla's order, and the loop (unchanged) runs isMergable and tryToMerge on them as before.
 *
 * Which calls are skipped. For a rejected candidate vanilla runs isMergable (getItem, isAlive, pickupDelay, age,
 * getCount, getMaxStackSize) at collection, and isMergable, Objects.equals(targets) and areMergable's first tests in the
 * loop. All are reads, except that ItemStack.getMaxStackSize calls Item.getMaxStackSize(ItemStack), which an item class
 * may override with its own code (it could, for example, initialise lazy capabilities or count calls). The rules
 * therefore only ever leave out candidates whose item uses Forge's default getMaxStackSize(ItemStack) (the item's
 * field); a candidate whose item has its own method is never filtered (vanilla's predicate decides, and vanilla's loop
 * runs on it), and rule 2 is used only when this entity's item uses the default (so do all same-item candidates).
 * areMergable's third and fourth tests (tags, Forge areCapsCompatible, which can initialise lazy capabilities) are never
 * called by the switch, and they run for exactly the pairs vanilla runs them for: a left-out pair fails areMergable's
 * first or second test, before them.
 *
 * Why the merges are identical. A candidate left out can never be merged by vanilla's loop:
 *  - The loop changes only this entity and the entry it is merging with (each entity appears once in the list), so a
 *    later entry's stack is the one seen at collection time.
 *  - This entity's item never changes during the loop: as destination it gets thisStack.copyWithCount(count + i), never
 *    an empty copy (i >= -count + 64 > -count even with a maximum above 64); as origin it is shrunk in place and, once
 *    empty, discarded, which ends the loop. Rule 1 therefore still holds when the loop reaches the entry.
 *  - Rule 2 needs this entity's count to be at least its count at collection time when the loop reaches the entry. With
 *    every stack involved at most 64 in maximum size this holds: as destination this grows by
 *    min(min(max, 64) - count, entryCount) = min(max - count, entryCount) >= 0; as origin areMergable already guaranteed
 *    entryCount + count <= entryMax, so merge moves min(min(entryMax, 64) - entryCount, count) = count items and this
 *    entity empties and is discarded (loop ends). Rule 2 is therefore used only while this stack's maximum is <= 64 and
 *    every candidate admitted before the entry (the only ones the loop can merge with earlier) has a maximum <= 64; an
 *    admitted candidate with a larger maximum (stack-size mods) switches rule 2 off for the rest of that query.
 * Stated assumption, as for any merge: code run by the merges themselves (the stack copy, setItem, the discard and the
 * Forge events they fire) does not change a third item entity's stack or this entity's item.
 *
 * -Dbons_and_furious.itemMergeCandidates=false switches it off at run time (vanilla predicate).
 * -Dbons_and_furious.itemMergeCandidates.shadow=true (verification runs only): every query runs both ways, the vanilla
 * list is used, and the switch checks that the filtered list is the vanilla list minus the rejected entries in order and
 * that the loop never merges with a rejected entry (SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 mismatches logged;
 * SHADOW_SKIPPED counts the entries the filter leaves out).
 */
public final class MergeCandidates {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.itemMergeCandidates", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.itemMergeCandidates.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPED = new AtomicLong();
    private static volatile boolean announced;

    /** True for item classes whose getMaxStackSize(ItemStack) is Forge's default (IForgeItem: the item's field). */
    private static final ClassValue<Boolean> PLAIN_MAX = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getMaxStackSize", ItemStack.class).getDeclaringClass() == IForgeItem.class;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
        }
    };

    private MergeCandidates() {
    }

    public static boolean plainMax(Item item) {
        return PLAIN_MAX.get(item.getClass());
    }

    /** ItemEntity.mergeWithNeighbours: its Level.getEntitiesOfClass(ItemEntity.class, box, vanilla predicate) call. */
    public static List<ItemEntity> query(ItemEntity self, Level level, Class<ItemEntity> type, AABB box, Predicate<? super ItemEntity> vanilla,
                                         Operation<List<ItemEntity>> original, ShadowHolder holder) {
        if (!enabled) {
            return original.call(level, type, box, vanilla);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_item_merge_candidates applies (item merges leave out neighbours of another item or with too many items before the merge loop){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            return shadow(self, level, type, box, vanilla, original, holder);
        }
        return original.call(level, type, box, new Filter(self, vanilla));
    }

    /** The candidate filter of one mergeWithNeighbours query (see the class javadoc for rules 1 and 2). */
    public static final class Filter implements Predicate<ItemEntity> {
        private final ItemEntity self;
        private final Predicate<? super ItemEntity> vanilla;
        private final Item item;
        private final int count;
        private boolean countRule;
        private Class<?> lastClass;          // the last other item class seen, and whether its getMaxStackSize is the default
        private boolean lastPlain;

        public Filter(ItemEntity self, Predicate<? super ItemEntity> vanilla) {
            this.self = self;
            this.vanilla = vanilla;
            ItemStack stack = self.m_32055_();
            this.item = stack.m_41720_();
            this.count = stack.m_41613_();
            // vanilla's isMergable() on this entity has just read the same maximum (mergeWithNeighbours' first test)
            this.countRule = plainMax(this.item) && stack.m_41741_() <= 64;
        }

        @Override
        public boolean test(ItemEntity entity) {
            if (entity == this.self) {
                return this.vanilla.test(entity);
            }
            ItemStack stack = entity.m_32055_();
            Item other = stack.m_41720_();
            if (other == this.item) {
                if (this.countRule) {
                    int max = stack.m_41741_();
                    if (stack.m_41613_() + this.count > max) {
                        return false;                                   // rule 2: areMergable's count test rejects it
                    }
                    if (!this.vanilla.test(entity)) {
                        return false;
                    }
                    if (max > 64) {
                        this.countRule = false;   // an admitted stack above 64 could shrink this one before later entries
                    }
                    return true;
                }
                return this.vanilla.test(entity);
            }
            Class<?> c = other.getClass();
            if (c != this.lastClass) {
                this.lastClass = c;
                this.lastPlain = PLAIN_MAX.get(c);
            }
            if (this.lastPlain) {
                return false;                                           // rule 1: areMergable's item test rejects it
            }
            return this.vanilla.test(entity);                           // an item with its own getMaxStackSize: never filtered
        }
    }

    /** Implemented by the mixin: the rejected entries of the entity's current shadow query. */
    public interface ShadowHolder {
        Set<ItemEntity> bons$shadowRejected();

        void bons$shadowRejected(Set<ItemEntity> rejected);
    }

    private static List<ItemEntity> shadow(ItemEntity self, Level level, Class<ItemEntity> type, AABB box, Predicate<? super ItemEntity> vanilla,
                                           Operation<List<ItemEntity>> original, ShadowHolder holder) {
        List<ItemEntity> plain = original.call(level, type, box, vanilla);
        List<ItemEntity> filtered = original.call(level, type, box, new Filter(self, vanilla));
        Set<ItemEntity> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
        int j = 0;
        for (ItemEntity e : plain) {
            if (j < filtered.size() && filtered.get(j) == e) {
                j++;
            } else {
                rejected.add(e);
            }
        }
        SHADOW_CHECKS.incrementAndGet();
        if (j != filtered.size()) {
            mismatch("the filtered candidate list of " + self + " is not the vanilla list minus rejected entries (" + filtered.size() + " vs " + plain.size() + ")");
        }
        SHADOW_SKIPPED.addAndGet(rejected.size());
        holder.bons$shadowRejected(rejected.isEmpty() ? null : rejected);
        return plain;
    }

    /** Shadow mode: tryToMerge reached ItemEntity.merge(dest, destStack, origin, originStack). */
    public static void shadowMerge(ShadowHolder holder, ItemEntity self, ItemEntity dest, ItemEntity origin) {
        Set<ItemEntity> rejected = holder.bons$shadowRejected();
        if (rejected == null) return;
        ItemEntity partner = dest == self ? origin : dest;
        SHADOW_CHECKS.incrementAndGet();
        if (rejected.contains(partner)) mismatch(self + " merged with " + partner + ", which the filter had left out");
    }

    private static void mismatch(String what) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: item merge shadow mismatch #{}: {}", m, what);
    }
}
