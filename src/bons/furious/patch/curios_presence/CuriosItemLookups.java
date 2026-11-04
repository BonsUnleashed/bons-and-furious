package bons.furious.patch.curios_presence;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.CuriosHelper;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * Bons and Furious switch curios_equipped_presence (Curios API 5.14.1+1.20.1, LGPL-3.0; both sides; since 1.0.49): the
 * Item-keyed lookups of Curios answered by the lean twins in CuriosLean. Idea: Collections Of Optimizations
 * (enigmaticlegacy.cachedCurioLookup, summonity.fastCurioMiss, curios.fastEquippedItemMiss, castercuriosbonus
 * .cachedCurioLookup, ironsspellbooks.leanCurioEquipCheck, meetyourfight.cachedCurioLookup, oddaccessories.fastCurioMiss,
 * lethality.skipUnequippedCharmScan), idea text only. COO answers these from a per-tick set of worn items, which reads a
 * tick stale for a curio swapped without a Curios event; ours keeps no set: every answer walks the live stacks.
 *
 * Wrapped (CurioInventoryWrapperPresenceMixin, CuriosHelperPresenceMixin, MixinExtras @WrapMethod):
 *  - CurioInventoryWrapper.findFirstCurio(Item) / findCurios(Item) (Iron's Spellbooks' CurioBaseItem.isEquippedBy, the
 *    interface default ICuriosItemHandler.isEquipped(Item) used by Caster Curios Bonus and Meet Your Fight, Summonity's
 *    three isEquipped): for Curios' own wrapper class the walk runs in CuriosLean over the wrapper's map;
 *  - CuriosHelper.findEquippedCurio(Item, entity) (Enigmatic Legacy's SuperpositionHandler.hasCurio / getCurioStack and
 *    everything built on them), findFirstCurio(entity, Item) (Lethality), findCurios(entity, Item): for Curios' own helper
 *    class the inventory comes from CuriosApi.getCuriosInventory (as Curios' getCuriosHandler does) and is resolved the way
 *    LazyOptional.map resolves it (isPresent, then the value; a present optional whose value is null runs Curios' own
 *    method, which throws), then the same walk (by entry over Curios' own wrapper map; for another handler class Curios' own
 *    call on that handler, or Curios' own key-set walk over its getCurios() map), without the LazyOptional.map Optional,
 *    the Item lambda and the miss triples.
 * Every handler and stack call, argument and order is Curios' own (see CuriosLean), so pending slot updates, events and
 * exceptions happen exactly as before; answers are equal (the same stack objects in records built the same way). Any
 * subclass of Curios' wrapper or helper runs the original.
 *
 * The same switch carries CuriosPresence (worn / wornAny / quiet for callers in other groups).
 * -Dbons_and_furious.curiosEquippedPresence=false runs Curios' own code. -Dbons_and_furious.curiosEquippedPresence.shadow=true
 * (verification runs only) runs the original first, then ours, returns the original's answer and counts unequal answers in
 * SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class CuriosItemLookups {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.curiosEquippedPresence", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.curiosEquippedPresence.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final Class<?> WRAPPER = CurioInventoryCapability.CurioInventoryWrapper.class;
    private static volatile boolean announced;

    private CuriosItemLookups() {
    }

    static boolean own(ICuriosItemHandler handler) {
        return handler.getClass() == WRAPPER && handler instanceof CuriosPresenceWrapperDuck;
    }

    // ------------------------------------------------------------------ CurioInventoryWrapper

    public static Optional<SlotResult> wrapperFirst(Object wrapper, Map<String, ICurioStacksHandler> curios, LivingEntity wearer, Item item,
                                                    Operation<Optional<SlotResult>> original) {
        if (!enabled || wrapper.getClass() != WRAPPER) return original.call(item);
        announce();
        if (SHADOW) return check("findFirstCurio(Item)", original.call(item), CuriosLean.firstItem(curios, wearer, item));
        return CuriosLean.firstItem(curios, wearer, item);
    }

    public static List<SlotResult> wrapperAll(Object wrapper, Map<String, ICurioStacksHandler> curios, LivingEntity wearer, Item item,
                                              Operation<List<SlotResult>> original) {
        if (!enabled || wrapper.getClass() != WRAPPER) return original.call(item);
        announce();
        if (SHADOW) return check("findCurios(Item)", original.call(item), CuriosLean.allItem(curios, wearer, item));
        return CuriosLean.allItem(curios, wearer, item);
    }

    // ------------------------------------------------------------------ CuriosHelper

    public static Optional<ImmutableTriple<String, Integer, ItemStack>> helperEquipped(Object helper, Item item, LivingEntity entity,
                                                                                      Operation<Optional<ImmutableTriple<String, Integer, ItemStack>>> original) {
        if (!enabled || helper.getClass() != CuriosHelper.class) return original.call(item, entity);
        announce();
        if (SHADOW) return check("findEquippedCurio(Item)", original.call(item, entity), equipped(item, entity, original));
        return equipped(item, entity, original);
    }

    static Optional<ImmutableTriple<String, Integer, ItemStack>> equipped(Item item, LivingEntity entity,
                                                                         Operation<Optional<ImmutableTriple<String, Integer, ItemStack>>> original) {
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (!inventory.isPresent()) return Optional.empty();
        ICuriosItemHandler handler = inventory.orElse(null);
        if (handler == null) return original.call(item, entity);
        boolean own = own(handler);
        Map<String, ICurioStacksHandler> curios = own ? ((CuriosPresenceWrapperDuck) handler).bons$presenceCurios() : handler.getCurios();
        return CuriosLean.equippedAnswer(CuriosLean.equipped(curios, own, item, null));
    }

    public static Optional<SlotResult> helperFirst(Object helper, LivingEntity entity, Item item, Operation<Optional<SlotResult>> original) {
        if (!enabled || helper.getClass() != CuriosHelper.class) return original.call(entity, item);
        announce();
        if (SHADOW) return check("CuriosHelper.findFirstCurio(Item)", original.call(entity, item), first(entity, item, original));
        return first(entity, item, original);
    }

    static Optional<SlotResult> first(LivingEntity entity, Item item, Operation<Optional<SlotResult>> original) {
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (!inventory.isPresent()) return Optional.empty();
        ICuriosItemHandler handler = inventory.orElse(null);
        if (handler == null) return original.call(entity, item);
        if (own(handler)) {
            CuriosPresenceWrapperDuck w = (CuriosPresenceWrapperDuck) handler;
            return CuriosLean.firstItem(w.bons$presenceCurios(), w.bons$presenceWearer(), item);
        }
        return Objects.requireNonNull(handler.findFirstCurio((ItemStack stack) -> stack.m_41720_() == item));
    }

    public static List<SlotResult> helperAll(Object helper, LivingEntity entity, Item item, Operation<List<SlotResult>> original) {
        if (!enabled || helper.getClass() != CuriosHelper.class) return original.call(entity, item);
        announce();
        if (SHADOW) return check("CuriosHelper.findCurios(Item)", original.call(entity, item), all(entity, item, original));
        return all(entity, item, original);
    }

    static List<SlotResult> all(LivingEntity entity, Item item, Operation<List<SlotResult>> original) {
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (!inventory.isPresent()) return Collections.emptyList();
        ICuriosItemHandler handler = inventory.orElse(null);
        if (handler == null) return original.call(entity, item);
        if (own(handler)) {
            CuriosPresenceWrapperDuck w = (CuriosPresenceWrapperDuck) handler;
            return CuriosLean.allItem(w.bons$presenceCurios(), w.bons$presenceWearer(), item);
        }
        return Objects.requireNonNull(handler.findCurios((ItemStack stack) -> stack.m_41720_() == item));
    }

    // ------------------------------------------------------------------ shadow / log

    static <T> T check(String what, T asked, T ours) {
        SHADOW_CHECKS.incrementAndGet();
        if (!Objects.equals(asked, ours) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: curios_equipped_presence shadow mismatch in {}: Curios {}, ours {}", what, asked, ours);
        return asked;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: curios_equipped_presence applies (Curios' item lookups walk the slots without the view, lambda and Optional allocations){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }
}
