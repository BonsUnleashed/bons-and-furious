package bons.furious.patch.uniqueaccessories_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Predicate;
import net.genzyuro.uniqueaccessories.registry.UAItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * Bons and Furious switch uniqueaccessories_waist_warmer_frozen_first (Unique Accessories 1.3.3, All Rights Reserved;
 * server side). Ours; no Unique Accessories code is carried. Idea: Collections Of Optimizations
 * (uniqueaccessories.leanWaistWarmerScan), idea text only.
 *
 * <p>WaistWarmerEvents.onLivingTick runs for every living entity on every server tick: hasWaistWarmer (the entity's Curios
 * inventory, then a findCurios walk with a result list) and, only when that says yes, "if ticksFrozen > 0, set it to 0".
 * The walk is asked first, for every entity, although its answer matters only for a frozen one.
 *
 * <p>In place of that one hasWaistWarmer call (the mod's other callers, onFreezeDamage and onWake, are untouched), with the
 * same single inventory lookup the method makes: no Curios inventory -> false (the method's answer); an entity whose
 * ticksFrozen is not above zero and whose curio walk the shared Curios layer says would only read
 * (CuriosPresence.quiet(ICuriosItemHandler) on this very handler: Curios' own wrapper and stacks handlers, no pending slot
 * update) -> false (the handler then returns, and with either answer it would have ended there: nothing is set); a frozen
 * entity under the same test -> CuriosPresence.worn(entity, WAIST_WARMER) (the same answer as the method's walk over the
 * equipped stacks, scanned without the result list); anything else -> the method's own question on the same handler,
 * findCurios(stack is a Waist Warmer) not empty (the same call, with the same per-stack test, as the method's walk). The
 * layer is reached by name (MethodHandle): when it cannot be reached, nothing is left out.
 * Runtime flag: -Dbons_and_furious.uniqueaccessoriesWaistWarmerFrozenFirst=false.
 */
public final class UaWaistWarmer {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.uniqueaccessoriesWaistWarmerFrozenFirst", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Curios' own inventory class (the layer can only say "read-only" for it). */
    private static final Class<?> WRAPPER = CurioInventoryCapability.CurioInventoryWrapper.class;
    /** CuriosPresence.quiet(ICuriosItemHandler): a walk over this handler now applies no pending update and only reads. */
    private static final MethodHandle QUIET = findQuiet();
    /** CuriosPresence.worn(LivingEntity, Item): 1 / 0 / -1 (a read-only scan of the equipped stacks, no result list). */
    private static final MethodHandle WORN = findWorn();
    /** The method's per-stack test: the stack is a Waist Warmer (read per stack, as the method reads it). */
    private static final Predicate<ItemStack> IS_WAIST_WARMER = stack -> stack.m_150930_((Item) UAItems.WAIST_WARMER.get());
    private static volatile boolean announced;
    /** Statistics (plain, racy): walks left out for an entity that is not frozen / frozen ones answered by the layer's scan. */
    public static long skipped, answered;

    private UaWaistWarmer() {
    }

    private static MethodHandle findQuiet() {
        String owner = "bons.furious.patch.curios_presence.CuriosPresence";
        try {
            return MethodHandles.lookup().findStatic(Class.forName(owner, false, UaWaistWarmer.class.getClassLoader()), "quiet",
                    MethodType.methodType(boolean.class, ICuriosItemHandler.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: uniqueaccessories_waist_warmer_frozen_first cannot reach {}.quiet ({}); every Waist Warmer check walks the slots", owner, e.toString());
            return null;
        }
    }

    private static MethodHandle findWorn() {
        try {
            return MethodHandles.lookup().findStatic(Class.forName("bons.furious.patch.curios_presence.CuriosPresence", false, UaWaistWarmer.class.getClassLoader()),
                    "worn", MethodType.methodType(int.class, LivingEntity.class, Item.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    static boolean readOnly(ICuriosItemHandler handler) {
        if (QUIET == null) return false;
        try {
            return (boolean) QUIET.invokeExact(handler);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 1 = a Waist Warmer is in an equipped slot, 0 = none (read-only scan), -1 = unknown. */
    static int worn(LivingEntity entity) {
        if (WORN == null) return -1;
        try {
            return (int) WORN.invokeExact(entity, (Item) UAItems.WAIST_WARMER.get());
        } catch (Throwable t) {
            return -1;
        }
    }

    /** In place of onLivingTick's hasWaistWarmer(entity) call. */
    public static boolean hasWaistWarmer(LivingEntity entity, Operation<Boolean> original) {
        if (!enabled) return original.call(entity);
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);    // the method's one lookup
        if (!inventory.isPresent()) return false;                                            // the method's answer then
        ICuriosItemHandler handler = inventory.orElse(null);
        if (handler == null) return original.call(entity);
        // cheapest test first, so another inventory implementation walks at once
        if (handler.getClass() == WRAPPER && readOnly(handler)) {
            if (entity.m_146888_() <= 0) {                                                  // getTicksFrozen: answer unused
                skipped++;
                if (!announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: uniqueaccessories_waist_warmer_frozen_first applies (the Waist Warmer check walks curio slots only for frozen entities)");
                }
                return false;
            }
            int w = worn(entity);                // frozen: the answer is used; the layer's scan, without the result list
            if (w >= 0) {
                answered++;
                return w == 1;
            }
        }
        return !handler.findCurios(IS_WAIST_WARMER).isEmpty();                              // the method's walk, same handler
    }
}
