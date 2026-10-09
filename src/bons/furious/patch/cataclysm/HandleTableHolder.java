package bons.furious.patch.cataclysm;

/**
 * Bons and Furious switch cataclysm_capability_handles (since 1.0.36): the per-entity table that
 * LivingEntityTokenHandlesMixin adds to LivingEntity (one reference, null until a capability registered with
 * PrivateCapabilityHandles is first resolved on that entity). Only PrivateCapabilityHandles reads or writes it.
 */
public interface HandleTableHolder {
    Object[] bons$tokenHandles();

    void bons$setTokenHandles(Object[] handles);
}
