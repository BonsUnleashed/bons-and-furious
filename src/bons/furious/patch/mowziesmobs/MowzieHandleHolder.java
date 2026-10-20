package bons.furious.patch.mowziesmobs;

/**
 * Bons and Furious switch mowziesmobs_capability_handles: the per-entity slot LivingEntityCapabilityHandlesMixin adds to
 * LivingEntity (one reference, null until Mowzie's Mobs first resolves one of its capabilities on that entity). It holds
 * the MowzieCapabilityHandles table of that entity (since 1.0.46 an Object[]: the dispatcher, then a LazyOptional or
 * absent entry and its value per capability; replaced on every change); nothing else reads or writes it.
 */
public interface MowzieHandleHolder {
    Object bons$mowzieHandles();

    void bons$setMowzieHandles(Object handles);
}
