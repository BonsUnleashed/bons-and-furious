package bons.furious.patch.capability_callers;

import org.thecelestialworkshop.celestisynth.common.capabilities.CSEntityCapabilityProvider;

/**
 * Bons and Furious switch celestisynth_capability_handles (Celestisynth 1.3.4, MIT; both sides; since 1.0.49). Idea:
 * Collections Of Optimizations (celestisynth.fastCapabilityLookup), idea text only. CSEntityCapabilityProvider.get(
 * LivingEntity) = entity.getCapability(CAPABILITY), asked by Celestisynth's isInvisible / isInvisibleTo hooks, its living tick
 * handler and three render hooks, several times per entity per tick and per frame; the call goes to the layer's kept handle
 * (see HandleCallers). CapabilitySyncer 4.0.1's attacher providers (CapabilityAttacher$1/$2) answer their final captured
 * LazyOptional (guard lines), so absent entries are kept too; CAPABILITY is written only by the provider's static
 * initialiser (census). COO keeps the handle in a field it drops "whenever it stops being valid"; ours also needs the same
 * dispatcher and valid capabilities and refuses classes with their own getCapability.
 */
public final class CelestisynthHandles {
    public static final HandleCallers KEY = new HandleCallers("celestisynth_capability_handles", "celestisynthCapabilityHandles", true)
            .register(CSEntityCapabilityProvider.CAPABILITY);

    private CelestisynthHandles() {
    }
}
