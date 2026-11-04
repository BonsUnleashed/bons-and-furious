package bons.furious.patch.curios_presence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * The lean twins of Curios 5.14.1's item lookups, shared by curios_equipped_presence (Item-keyed) and
 * curios_first_match_lean (Predicate-keyed); since 1.0.49, both sides. Curios is LGPL-3.0: these bodies follow Curios'
 * own CurioInventoryWrapper.findFirstCurio / findCurios and CuriosHelper.findEquippedCurio (modified, in our GPL-3.0
 * source).
 *
 * Every handler and stack call of the original is kept, with the same arguments and in the same order: per slot type
 * getStacks(), then getSlots() before every slot, getStackInSlot(i), isEmpty() and the item test (getItem() == item, or the
 * caller's predicate), and getRenders() on a hit. What is left out: Curios' unmodifiable map view, its key-set view and
 * iterator wrapper and the per-type map.get(id) (the wrapper's own LinkedHashMap is walked by entry instead: the same keys
 * and values in the same order; a map changed during the walk throws the same ConcurrentModificationException at the same
 * next step), the Item lambda, LazyOptional.map's Optional, and in findEquippedCurio the two miss triples (the lambda's and
 * the eagerly built orElse argument). The SlotContext / SlotResult records and triples on a hit are built exactly as in
 * Curios.
 */
public final class CuriosLean {
    private CuriosLean() {
    }

    private static SlotResult result(String id, LivingEntity wearer, int i, ICurioStacksHandler handler, ItemStack stack) {
        NonNullList<Boolean> renderStates = handler.getRenders();
        return new SlotResult(new SlotContext(id, wearer, i, false, renderStates.size() > i && renderStates.get(i)), stack);
    }

    /** CurioInventoryWrapper.findFirstCurio(Item) over the wrapper's own map. */
    public static Optional<SlotResult> firstItem(Map<String, ICurioStacksHandler> curios, LivingEntity wearer, Item item) {
        for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
            String id = entry.getKey();
            ICurioStacksHandler handler = entry.getValue();
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); ++i) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.m_41619_() || stack.m_41720_() != item) continue;
                return Optional.of(result(id, wearer, i, handler, stack));
            }
        }
        return Optional.empty();
    }

    /** CurioInventoryWrapper.findFirstCurio(Predicate) over the wrapper's own map. */
    public static Optional<SlotResult> firstMatch(Map<String, ICurioStacksHandler> curios, LivingEntity wearer, Predicate<ItemStack> filter) {
        for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
            String id = entry.getKey();
            ICurioStacksHandler handler = entry.getValue();
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); ++i) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.m_41619_() || !filter.test(stack)) continue;
                return Optional.of(result(id, wearer, i, handler, stack));
            }
        }
        return Optional.empty();
    }

    /** CurioInventoryWrapper.findCurios(Item) over the wrapper's own map (a new list, as Curios returns). */
    public static List<SlotResult> allItem(Map<String, ICurioStacksHandler> curios, LivingEntity wearer, Item item) {
        ArrayList<SlotResult> found = new ArrayList<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
            String id = entry.getKey();
            ICurioStacksHandler handler = entry.getValue();
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); ++i) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.m_41619_() || stack.m_41720_() != item) continue;
                found.add(result(id, wearer, i, handler, stack));
            }
        }
        return found;
    }

    /** CurioInventoryWrapper.findCurios(Predicate) over the wrapper's own map. */
    public static List<SlotResult> allMatch(Map<String, ICurioStacksHandler> curios, LivingEntity wearer, Predicate<ItemStack> filter) {
        ArrayList<SlotResult> found = new ArrayList<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
            String id = entry.getKey();
            ICurioStacksHandler handler = entry.getValue();
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); ++i) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.m_41619_() || !filter.test(stack)) continue;
                found.add(result(id, wearer, i, handler, stack));
            }
        }
        return found;
    }

    /**
     * The walk inside CuriosHelper.findEquippedCurio(Predicate, entity) over one handler's map: the hit's triple, or null
     * when nothing matched. {@code byEntry} walks the map by entry (Curios' own wrapper map, a LinkedHashMap); otherwise
     * exactly as Curios does (key set, then get(id): any other handler's map).
     */
    public static ImmutableTriple<String, Integer, ItemStack> equipped(Map<String, ICurioStacksHandler> curios, boolean byEntry,
                                                                     Item item, Predicate<ItemStack> filter) {
        if (byEntry) {
            for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
                ImmutableTriple<String, Integer, ItemStack> hit = equippedIn(entry.getKey(), entry.getValue(), item, filter);
                if (hit != null) return hit;
            }
            return null;
        }
        for (String id : curios.keySet()) {
            ImmutableTriple<String, Integer, ItemStack> hit = equippedIn(id, curios.get(id), item, filter);
            if (hit != null) return hit;
        }
        return null;
    }

    private static ImmutableTriple<String, Integer, ItemStack> equippedIn(String id, ICurioStacksHandler handler, Item item, Predicate<ItemStack> filter) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int i = 0; i < stacks.getSlots(); ++i) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (stack.m_41619_() || (filter != null ? !filter.test(stack) : stack.m_41720_() != item)) continue;
            return new ImmutableTriple<>(id, i, stack);
        }
        return null;
    }

    /** CuriosHelper.findEquippedCurio's last line on a hit: an empty slot id answers Optional.empty() (null throws, as in Curios). */
    public static Optional<ImmutableTriple<String, Integer, ItemStack>> equippedAnswer(ImmutableTriple<String, Integer, ItemStack> hit) {
        if (hit == null) return Optional.empty();
        return hit.getLeft().isEmpty() ? Optional.empty() : Optional.of(hit);
    }
}
