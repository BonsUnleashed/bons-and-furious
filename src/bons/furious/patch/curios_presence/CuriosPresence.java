package bons.furious.patch.curios_presence;

import bons.furious.mixin.curios_presence.CurioStacksHandlerPresenceAccessor;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;
import top.theillusivec4.curios.common.inventory.DynamicStackHandler;

/**
 * Public API of the Curios layer (switch curios_equipped_presence, Curios API 5.14.1+1.20.1, both sides, since 1.0.49) for
 * caller switches in OTHER groups, reached through a MethodHandle looked up by name. Ours entirely. No cache: every answer reads the live stacks of the entity's Curios inventory.
 *
 * A Curios walk over an inventory only reads when the inventory is Curios' own CurioInventoryWrapper, every slot type in
 * it is Curios' own CurioStacksHandler with no pending slot-count update (CurioStacksHandler.update false: then getStacks,
 * getSlots and getRenders do not run update(), which would resize the type, post SlotModifiersUpdatedEvent and reset an
 * open curios menu) and its stacks are Curios' own DynamicStackHandler (ItemStackHandler's getSlots / getStackInSlot only
 * read). That is the l2hostility_curio_check_lean rule. Under it a caller may skip a walk of its own whose result this API
 * tells it; anything else answers "unknown" (-1, or false from quiet) and the caller runs its stock code.
 */
public final class CuriosPresence {
    static final Class<?> WRAPPER = CurioInventoryCapability.CurioInventoryWrapper.class;
    /** curios_equipped_presence's duck and accessor are in the game (the switch is on). */
    static final boolean READY = CuriosPresenceWrapperDuck.class.isAssignableFrom(CurioInventoryCapability.CurioInventoryWrapper.class)
            && CurioStacksHandlerPresenceAccessor.class.isAssignableFrom(CurioStacksHandler.class);

    private CuriosPresence() {
    }

    /**
     * 0 = no non-empty stack with getItem() == item in any equipped (not cosmetic) curio slot of the entity, and a walk now
     * would only read; 1 = there is one (same condition); -1 = unknown. No Curios inventory at all (no slots, invalid
     * capabilities, null entity) is 0.
     */
    public static int worn(LivingEntity entity, Item item) {
        return scan(entity, item, null);
    }

    /** As worn, with test.test(stack.getItem()) on every non-empty equipped stack (first true wins). test must be pure. */
    public static int wornAny(LivingEntity entity, Predicate<Item> test) {
        return test == null ? -1 : scan(entity, null, test);
    }

    /** True when a Curios walk over the entity's inventory now would apply no pending slot update and only read. */
    public static boolean quiet(LivingEntity entity) {
        if (!READY || !CuriosItemLookups.enabled) return false;
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (!inventory.isPresent()) return true;
        ICuriosItemHandler handler = inventory.orElse(null);
        return handler != null && readOnly(handler);
    }

    /**
     * As quiet(LivingEntity), for an inventory the caller already holds (added in 1.0.49, for callers that must not look
     * the inventory up a second time): true when a Curios walk over this
     * handler now would apply no pending slot update and only read, i.e. the layer is in the game and on and the handler
     * passes the read-only rule of the class comment; false = unknown (run the stock code). A null handler is false. For
     * the same inventory it answers what quiet(entity) answers for an entity whose inventory is present.
     */
    public static boolean quiet(ICuriosItemHandler handler) {
        return READY && CuriosItemLookups.enabled && handler != null && readOnly(handler);
    }

    /** The read-only condition for one handler (see the class comment). */
    static boolean readOnly(ICuriosItemHandler handler) {
        if (handler.getClass() != WRAPPER || !(handler instanceof CuriosPresenceWrapperDuck duck)) return false;
        Map<String, ICurioStacksHandler> curios = duck.bons$presenceCurios();
        if (curios == null) return false;
        for (ICurioStacksHandler type : curios.values()) {
            if (type == null || type.getClass() != CurioStacksHandler.class) return false;
            if (((CurioStacksHandlerPresenceAccessor) type).bons$presencePendingUpdate()) return false;
            IDynamicStackHandler stacks = type.getStacks();      // no pending update: a plain field read
            if (stacks == null || stacks.getClass() != DynamicStackHandler.class) return false;
        }
        return true;
    }

    private static int scan(LivingEntity entity, Item item, Predicate<Item> test) {
        if (!READY || !CuriosItemLookups.enabled) return -1;
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (!inventory.isPresent()) return 0;
        ICuriosItemHandler handler = inventory.orElse(null);
        if (handler == null || !readOnly(handler)) return -1;
        for (ICurioStacksHandler type : ((CuriosPresenceWrapperDuck) handler).bons$presenceCurios().values()) {
            IDynamicStackHandler stacks = type.getStacks();
            for (int i = 0, n = stacks.getSlots(); i < n; i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.m_41619_()) continue;
                if (test == null ? stack.m_41720_() == item : test.test(stack.m_41720_())) return 1;
            }
        }
        return 0;
    }
}
