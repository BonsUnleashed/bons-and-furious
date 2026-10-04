package bons.furious.patch.mowziesmobs;

/**
 * Bons and Furious switch mowziesmobs_capability_handles: the per-entity slot LivingEntityCapabilityHandlesMixin adds to
 * LivingEntity (one reference, null until Mowzie's Mobs first resolves one of its capabilities on that entity). It holds
 * the MowzieCapabilityHandles.Handle array of that entity; nothing else reads or writes it.
 */
public interface MowzieHandleHolder {
    Object bons$mowzieHandles();

    void bons$setMowzieHandles(Object handles);
}
