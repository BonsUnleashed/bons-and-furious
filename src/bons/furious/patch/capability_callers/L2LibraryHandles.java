package bons.furious.patch.capability_callers;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Bons and Furious switch l2library_capability_handles (L2 Library 2.5.5, LGPL-2.1; both sides; since 1.0.49). Idea:
 * Collections Of Optimizations (l2library.leanCapabilityResolve), idea text only; COO only removes the Optionals (which the
 * JIT likely removes anyway), ours removes the dispatcher walk.
 *
 * Every L2 mod reads its data through GeneralCapabilityHolder.get (getCapability(cap).resolve().get().check()) and isProper
 * (getCapability(cap).isPresent()), and PlayerCapabilityHolder.get (first getCapability(cap), then a revive branch when
 * empty): L2 Hostility's traits per mob per tick and per rendered name plate, every player holder per player per tick. The
 * holders' getCapability calls on an Entity go to the layer's kept handle (see HandleCallers); levels, chunks and stacks are
 * asked as before, and so is PlayerCapabilityHolder's revive branch (only its first lookup is redirected). The provider is
 * GeneralCapabilitySerializer (PlayerCapabilitySerializer's too), which answers its lo field, written only by its
 * constructor (census, guard lines). The holders are created by each L2 mod, so a capability is registered with the layer
 * the first time it is asked for (one owner per capability object).
 */
public final class L2LibraryHandles {
    public static final HandleCallers KEY = new HandleCallers("l2library_capability_handles", "l2libraryCapabilityHandles", true);
    private static final ConcurrentHashMap<Capability<?>, CallerCapabilityHandles.Owner> OWNERS = new ConcurrentHashMap<>();

    private L2LibraryHandles() {
    }

    /** provider.getCapability(cap): an Entity through the layer, anything else as before. */
    public static <T> LazyOptional<T> lookup(ICapabilityProvider provider, Capability<T> cap) {
        if (!KEY.enabled || cap == null || !(provider instanceof Entity entity)) return provider.getCapability(cap);
        CallerCapabilityHandles.Owner owner = OWNERS.get(cap);
        if (owner == null) owner = OWNERS.computeIfAbsent(cap, c -> KEY.registerMore(c));
        return KEY.lookup(entity, cap, owner);
    }
}
