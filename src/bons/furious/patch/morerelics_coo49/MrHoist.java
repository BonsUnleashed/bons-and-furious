package bons.furious.patch.morerelics_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * Bons and Furious switch morerelics_equipped_curios_hoist (More Relics 1.7.7, same bytes in 1.7.6, with Curios API
 * 5.14.1+1.20.1; All Rights Reserved; both sides; since 1.0.49). Ours; no More Relics code is carried. Idea: Collections Of
 * Optimizations (morerelics.hoistEquippedCurios), idea text only.
 *
 * actuallyGetEquippedRelics (3 HUD overlays per frame, about 25 hurt / attack handlers) runs, for an inventory with N
 * equipped slots, "for (i < handler.getEquippedCurios().getSlots()) { stack = handler.getEquippedCurios().getStackInSlot(i);
 * if (stack.getItem() instanceof IRelicItem) items.add(stack); }": getEquippedCurios() 2N + 1 times. Curios'
 * CurioInventoryWrapper.getEquippedCurios builds the read-only slot map view, an array of every slot type's getStacks()
 * (which applies a pending slot-count update first) and a new CombinedInvWrapper (whose constructor asks every stack
 * handler's getSlots()). The walk only reads (the item test and items.add touch nothing of the inventory), always on its
 * own handler argument, which actuallyGetEquippedRelics takes from CuriosApi.getCuriosInventory(player).
 *
 * scan, before the walk: when the Curios presence layer's CuriosPresence.quiet(ICuriosItemHandler) holds for the handler
 * (reached by name; the layer's mixins applied and its switch on, the handler Curios' own CurioInventoryWrapper, every slot
 * type Curios' own stacks handler with no pending update and Curios' own stack handler), every getEquippedCurios() of the
 * walk would only read and build a wrapper over the same stack handler objects with the same slot counts, so the walk
 * runs once over one wrapper: the same stacks in the same order through the same item test (Relics' IRelicItem, by name),
 * and the original is skipped. Otherwise (layer absent or off, a foreign implementation, a pending update) the original
 * walk runs as shipped. Nothing is kept beyond the call. Runtime flag -Dbons_and_furious.morerelicsEquippedCuriosHoist=false.
 */
public final class MrHoist {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.morerelicsEquippedCuriosHoist", "true"));
    /** Walks run over one wrapper (statistics; plain, racy). */
    public static long scans;
    /** CuriosPresence.quiet(ICuriosItemHandler): the layer in the game and on, and the handler read-only; null when the
     *  layer cannot be reached. */
    private static final MethodHandle QUIET = quietHandle();
    /** Relics' IRelicItem, the walk's item test; null when it cannot be found. */
    private static final Class<?> RELIC = relicItem();
    private static volatile boolean announced;

    private static MethodHandle quietHandle() {
        try {
            Class<?> presence = Class.forName("bons.furious.patch.curios_presence.CuriosPresence", false, MrHoist.class.getClassLoader());
            return MethodHandles.lookup().findStatic(presence, "quiet", MethodType.methodType(boolean.class, ICuriosItemHandler.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: morerelics_equipped_curios_hoist cannot reach the Curios presence layer ({}); More Relics' curio walk runs as shipped", e.toString());
            return null;
        }
    }

    private static Class<?> relicItem() {
        try {
            return Class.forName("it.hurts.sskirillss.relics.items.relics.base.IRelicItem", false, MrHoist.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: morerelics_equipped_curios_hoist cannot find Relics' IRelicItem ({}); More Relics' curio walk runs as shipped", e.toString());
            return null;
        }
    }

    private MrHoist() {
    }

    /** The walk of MoreRelicsUtil.lambda$actuallyGetEquippedRelics$0 over one wrapper when the handler is quiet (true: the
     *  caller skips the original); false = run the original. */
    public static boolean scan(List<Object> items, ICuriosItemHandler handler) {
        if (!enabled || QUIET == null || RELIC == null || !quiet(handler)) return false;
        IItemHandlerModifiable curios = handler.getEquippedCurios();
        for (int i = 0, n = curios.getSlots(); i < n; i++) {
            ItemStack stack = curios.getStackInSlot(i);
            if (RELIC.isInstance(stack.m_41720_())) items.add(stack);
        }
        scans++;
        if (!announced) announce();
        return true;
    }

    private static boolean quiet(ICuriosItemHandler handler) {
        try {
            return (boolean) QUIET.invokeExact(handler);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: morerelics_equipped_curios_hoist applies (More Relics builds its combined curio inventory once per relic scan)");
    }
}
