package bons.furious.patch.create_logistics;

import bons.furious.mixin.create_logistics.CompoundContainerAccessor;
import bons.furious.mixin.create_logistics.SidedInvWrapperAccessor;
import java.lang.reflect.Method;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/**
 * The item handlers whose read path create_item_helper_empty_slots and create_single_pass_extraction rely on (Create 6.0.8
 * with Forge 47.4.16 and Minecraft 1.20.1). A handler qualifies when every method on that path is the audited code below,
 * decided per class (ClassValue) by the class that declares each method, so a subclass that overrides one of them, or any
 * class not listed, does not qualify and keeps Create's original code.
 *
 * Audited read path (Forge 47.4.16 and Minecraft 1.20.1 bytecode, read for this switch):
 *  - ItemStackHandler (and subclasses that keep its getSlots, getStackInSlot, extractItem and validateSlotIndex):
 *    getStackInSlot = validateSlotIndex + stacks.get; extractItem(slot, n, true) = n == 0 ? EMPTY : validateSlotIndex +
 *    stacks.get, EMPTY when that stack is empty, else a copy of up to n items; no write when simulating.
 *  - InvWrapper (and subclasses that keep those three methods, e.g. Forge's VanillaHopperItemHandler, which only
 *    overrides insertItem): getStackInSlot = inv.getItem(slot); extractItem(slot, n, true) = n == 0 ? EMPTY :
 *    inv.getItem(slot), EMPTY when empty, else a copy; getSlots = inv.getContainerSize().
 *  - SidedInvWrapper (same rule): getStackInSlot = getSlot(inv, slot, side) == -1 ? EMPTY : inv.getItem(i);
 *    extractItem(slot, n, true) = n == 0 ? EMPTY : the same getSlot, EMPTY for -1, inv.getItem(i), EMPTY when empty,
 *    inv.canTakeItemThroughFace(i, stack, side), a copy; getSlots = inv.getSlotsForFace(side).length.
 *  - EmptyHandler: constant answers.
 *  - Containers behind the wrappers: getItem, getContainerSize (and for sided wrappers getSlotsForFace and
 *    canTakeItemThroughFace) must be declared by Minecraft classes, and getItem by one of: SimpleContainer,
 *    AbstractFurnaceBlockEntity, BrewingStandBlockEntity, ChiseledBookShelfBlockEntity (a plain list read each), or
 *    RandomizableContainerBlockEntity (chests, barrels, shulker boxes, dispensers, droppers, hoppers) with getItems
 *    declared by a Minecraft class and its own unpackLootTable: the first read of a container that still has a loot
 *    table fills it (with the level's random when the seed is 0), every later read is a list read. CompoundContainer
 *    (double chests) qualifies when both halves do.
 *
 * What the two switches then rely on:
 *  - emptySkip: for such a handler, an empty getStackInSlot(slot) means extractItem(slot, n, true) returns ItemStack.EMPTY
 *    (the same getItem / stacks.get read, nothing in between changes it), and skipping that call skips only reads.
 *  - simulatePure: getSlots, getStackInSlot and extractItem(..., true) write nothing; the loot fill happens at the first
 *    getItem of a pass, so with or without an earlier simulated pass it happens before any other effect of the call.
 * Item stack creation (copies), Item.getMaxStackSize and Forge's capability comparison in canItemStacksStack are treated
 * as side-effect free here, as vanilla and Forge themselves treat them; Create's original makes the same calls twice.
 */
public final class PureHandlers {
    private static final int NO = 0, STACK_HANDLER = 1, INV_WRAPPER = 2, SIDED_WRAPPER = 3, EMPTY_HANDLER = 4;
    private static final int CONTAINER_NO = 0, CONTAINER_PURE = 1, CONTAINER_COMPOUND = 2;

    private static final ClassValue<Integer> HANDLER_KIND = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> c) {
            try {
                if (c == EmptyHandler.class) return EMPTY_HANDLER;
                if (ItemStackHandler.class.isAssignableFrom(c)) {
                    return declaredBy(c, ItemStackHandler.class, "getSlots")
                            && declaredBy(c, ItemStackHandler.class, "getStackInSlot", int.class)
                            && declaredBy(c, ItemStackHandler.class, "extractItem", int.class, int.class, boolean.class)
                            && declaredBy(c, ItemStackHandler.class, "validateSlotIndex", int.class) ? STACK_HANDLER : NO;
                }
                if (InvWrapper.class.isAssignableFrom(c)) {
                    return declaredBy(c, InvWrapper.class, "getSlots")
                            && declaredBy(c, InvWrapper.class, "getStackInSlot", int.class)
                            && declaredBy(c, InvWrapper.class, "extractItem", int.class, int.class, boolean.class)
                            && declaredBy(c, InvWrapper.class, "getInv") ? INV_WRAPPER : NO;
                }
                if (SidedInvWrapper.class.isAssignableFrom(c)) {
                    return declaredBy(c, SidedInvWrapper.class, "getSlots")
                            && declaredBy(c, SidedInvWrapper.class, "getStackInSlot", int.class)
                            && declaredBy(c, SidedInvWrapper.class, "extractItem", int.class, int.class, boolean.class) ? SIDED_WRAPPER : NO;
                }
            } catch (Throwable t) {
                return NO;
            }
            return NO;
        }
    };

    private static final ClassValue<Integer> CONTAINER_KIND = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> c) {
            try {
                if (c == CompoundContainer.class) return CONTAINER_COMPOUND;
                Class<?> getItem = declarer(c, "m_8020_", int.class);
                Class<?> size = declarer(c, "m_6643_");
                if (getItem == null || size == null || !minecraft(size)) return CONTAINER_NO;
                if (getItem == SimpleContainer.class || getItem == AbstractFurnaceBlockEntity.class || getItem == BrewingStandBlockEntity.class
                        || getItem == ChiseledBookShelfBlockEntity.class) return CONTAINER_PURE;
                if (getItem == RandomizableContainerBlockEntity.class) {
                    Class<?> items = declarer(c, "m_7086_");
                    Class<?> unpack = declarer(c, "m_59640_", Player.class);
                    return items != null && minecraft(items) && unpack == RandomizableContainerBlockEntity.class ? CONTAINER_PURE : CONTAINER_NO;
                }
            } catch (Throwable t) {
                return CONTAINER_NO;
            }
            return CONTAINER_NO;
        }
    };

    private static final ClassValue<Boolean> SIDED_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                Class<?> faces = declarer(c, "m_7071_", Direction.class);
                Class<?> take = declarer(c, "m_7157_", int.class, ItemStack.class, Direction.class);
                return faces != null && take != null && minecraft(faces) && minecraft(take);
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private PureHandlers() {
    }

    /** The handler's read path is the audited one (see the class comment); false for anything else, including null. */
    public static boolean audited(IItemHandler h) {
        if (h == null) return false;
        switch (HANDLER_KIND.get(h.getClass())) {
            case STACK_HANDLER, EMPTY_HANDLER -> {
                return true;
            }
            case INV_WRAPPER -> {
                return container(((InvWrapper) h).getInv(), false, 0);
            }
            case SIDED_WRAPPER -> {
                // the accessors belong to create_item_helper_empty_slots: without them (that switch off) nothing qualifies here
                if (!((Object) h instanceof SidedInvWrapperAccessor a)) return false;
                WorldlyContainer inv = a.bons$inv();
                return inv != null && SIDED_OK.get(inv.getClass()) && container(inv, true, 0);
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean container(Container c, boolean sided, int depth) {
        if (c == null || depth > 8) return false;
        switch (CONTAINER_KIND.get(c.getClass())) {
            case CONTAINER_PURE -> {
                return true;
            }
            case CONTAINER_COMPOUND -> {
                if (sided || !((Object) c instanceof CompoundContainerAccessor cc)) return false;
                return container(cc.bons$first(), false, depth + 1) && container(cc.bons$second(), false, depth + 1);
            }
            default -> {
                return false;
            }
        }
    }

    /** True when the effective implementation of name(params) in c is the one declared by owner. */
    static boolean declaredBy(Class<?> c, Class<?> owner, String name, Class<?>... params) {
        return declarer(c, name, params) == owner;
    }

    /** The class whose declaration of name(params) c uses (walking superclasses), or null. Interface defaults are not followed. */
    static Class<?> declarer(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                if (!m.isBridge() && !m.isSynthetic()) return k;
            } catch (NoSuchMethodException e) {
                // keep walking
            }
        }
        return null;
    }

    private static boolean minecraft(Class<?> c) {
        return c.getName().startsWith("net.minecraft.");
    }
}
