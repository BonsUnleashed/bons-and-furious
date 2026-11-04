package bons.furious.patch.curios_presence;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
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
 * Bons and Furious switch curios_first_match_lean (Curios API 5.14.1+1.20.1, LGPL-3.0; both sides; since 1.0.49): the
 * Predicate-keyed lookups of Curios answered by the lean twins in CuriosLean. Idea: Collections Of Optimizations
 * (goety.fastCurioItemMiss, goety.memoCurioFilter, goetyrevelation.cacheHaloLookup), idea text only. COO memoises predicate
 * results per entity per tick, which is a tick late after an equipment change and wrong for predicates that read NBT; ours
 * runs the caller's predicate on the same stacks in the same order every time and only drops Curios' own allocations.
 *
 * Wrapped (CurioInventoryWrapperFirstMatchMixin, CuriosHelperFirstMatchMixin): CurioInventoryWrapper.findFirstCurio(
 * Predicate) / findCurios(Predicate) (Goety's CuriosFinder, Odd Accessories' CuriosUtil, the isEquipped(Predicate) default,
 * Terra Curio, ...), CuriosHelper.findEquippedCurio(Predicate, entity), findFirstCurio(entity, Predicate) and
 * findCurios(entity, Predicate): for Curios' own classes the same walk as Curios (CuriosLean), else the original. Why the
 * answer and every call are unchanged: see CuriosLean and CuriosItemLookups.
 *
 * -Dbons_and_furious.curiosFirstMatchLean=false runs Curios' own code; .shadow=true (verification runs only) runs the
 * original first, then ours (so the predicate runs twice per call), returns the original's answer and counts unequal
 * answers in SHADOW_CHECKS / SHADOW_MISMATCHES.
 */
public final class CuriosPredicateLookups {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.curiosFirstMatchLean", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.curiosFirstMatchLean.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final Class<?> WRAPPER = CurioInventoryCapability.CurioInventoryWrapper.class;
    private static volatile boolean announced;

    private CuriosPredicateLookups() {
    }

    static boolean own(ICuriosItemHandler handler) {
        return handler.getClass() == WRAPPER && handler instanceof CuriosFirstMatchWrapperDuck;
    }

    public static Optional<SlotResult> wrapperFirst(Object wrapper, Map<String, ICurioStacksHandler> curios, LivingEntity wearer,
                                                    Predicate<ItemStack> filter, Operation<Optional<SlotResult>> original) {
        if (!enabled || wrapper.getClass() != WRAPPER) return original.call(filter);
        announce();
        if (SHADOW) return check("findFirstCurio(Predicate)", original.call(filter), CuriosLean.firstMatch(curios, wearer, filter));
        return CuriosLean.firstMatch(curios, wearer, filter);
    }

    public static List<SlotResult> wrapperAll(Object wrapper, Map<String, ICurioStacksHandler> curios, LivingEntity wearer,
                                              Predicate<ItemStack> filter, Operation<List<SlotResult>> original) {
        if (!enabled || wrapper.getClass() != WRAPPER) return original.call(filter);
        announce();
        if (SHADOW) return check("findCurios(Predicate)", original.call(filter), CuriosLean.allMatch(curios, wearer, filter));
        return CuriosLean.allMatch(curios, wearer, filter);
    }

    public static Optional<ImmutableTriple<String, Integer, ItemStack>> helperEquipped(Object helper, Predicate<ItemStack> filter, LivingEntity entity,
                                                                                      Operation<Optional<ImmutableTriple<String, Integer, ItemStack>>> original) {
        if (!enabled || helper.getClass() != CuriosHelper.class) return original.call(filter, entity);
        announce();
        if (SHADOW) return check("findEquippedCurio(Predicate)", original.call(filter, entity), equipped(filter, entity, original));
        return equipped(filter, entity, original);
    }

    static Optional<ImmutableTriple<String, Integer, ItemStack>> equipped(Predicate<ItemStack> filter, LivingEntity entity,
                                                                         Operation<Optional<ImmutableTriple<String, Integer, ItemStack>>> original) {
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (!inventory.isPresent()) return Optional.empty();
        ICuriosItemHandler handler = inventory.orElse(null);
        if (handler == null) return original.call(filter, entity);
        boolean own = own(handler);
        Map<String, ICurioStacksHandler> curios = own ? ((CuriosFirstMatchWrapperDuck) handler).bons$firstMatchCurios() : handler.getCurios();
        return CuriosLean.equippedAnswer(CuriosLean.equipped(curios, own, null, filter));
    }

    public static Optional<SlotResult> helperFirst(Object helper, LivingEntity entity, Predicate<ItemStack> filter, Operation<Optional<SlotResult>> original) {
        if (!enabled || helper.getClass() != CuriosHelper.class) return original.call(entity, filter);
        announce();
        if (SHADOW) return check("CuriosHelper.findFirstCurio(Predicate)", original.call(entity, filter), first(entity, filter, original));
        return first(entity, filter, original);
    }

    static Optional<SlotResult> first(LivingEntity entity, Predicate<ItemStack> filter, Operation<Optional<SlotResult>> original) {
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (!inventory.isPresent()) return Optional.empty();
        ICuriosItemHandler handler = inventory.orElse(null);
        if (handler == null) return original.call(entity, filter);
        if (own(handler)) {
            CuriosFirstMatchWrapperDuck w = (CuriosFirstMatchWrapperDuck) handler;
            return CuriosLean.firstMatch(w.bons$firstMatchCurios(), w.bons$firstMatchWearer(), filter);
        }
        return Objects.requireNonNull(handler.findFirstCurio(filter));
    }

    public static List<SlotResult> helperAll(Object helper, LivingEntity entity, Predicate<ItemStack> filter, Operation<List<SlotResult>> original) {
        if (!enabled || helper.getClass() != CuriosHelper.class) return original.call(entity, filter);
        announce();
        if (SHADOW) return check("CuriosHelper.findCurios(Predicate)", original.call(entity, filter), all(entity, filter, original));
        return all(entity, filter, original);
    }

    static List<SlotResult> all(LivingEntity entity, Predicate<ItemStack> filter, Operation<List<SlotResult>> original) {
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (!inventory.isPresent()) return Collections.emptyList();
        ICuriosItemHandler handler = inventory.orElse(null);
        if (handler == null) return original.call(entity, filter);
        if (own(handler)) {
            CuriosFirstMatchWrapperDuck w = (CuriosFirstMatchWrapperDuck) handler;
            return CuriosLean.allMatch(w.bons$firstMatchCurios(), w.bons$firstMatchWearer(), filter);
        }
        return Objects.requireNonNull(handler.findCurios(filter));
    }

    static <T> T check(String what, T asked, T ours) {
        SHADOW_CHECKS.incrementAndGet();
        if (!Objects.equals(asked, ours) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: curios_first_match_lean shadow mismatch in {}: Curios {}, ours {}", what, asked, ours);
        return asked;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: curios_first_match_lean applies (Curios' predicate lookups walk the slots without the view, lambda and Optional allocations){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }
}
