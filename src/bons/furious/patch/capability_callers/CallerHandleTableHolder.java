package bons.furious.patch.capability_callers;

/**
 * Bons and Furious switch forge_capability_handle_layer (since 1.0.46): the per-entity table that
 * LivingEntityCallerHandlesMixin adds to LivingEntity (one volatile reference, null until a capability registered with
 * CallerCapabilityHandles is first resolved on that entity). Only CallerCapabilityHandles reads or writes it.
 */
public interface CallerHandleTableHolder {
    Object[] bons$callerHandles();

    void bons$setCallerHandles(Object[] handles);
}
