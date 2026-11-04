package bons.furious.patch.capability_callers;

import com.eeeab.eeeabsmobs.sever.handler.CapabilityHandler;

/**
 * Bons and Furious switch eeeabsmobs_capability_handles (EEEAB's Mobs 0.98.1, LGPL-3.0; both sides; since 1.0.49). Idea:
 * Collections Of Optimizations (eeeabsmobs.singleCapabilityLookup), idea text only; ours asks the dispatcher zero times per
 * call instead of once. CapabilityHandler.getCapability(Entity, Capability) asks the entity twice (isPresent, then
 * orElseThrow): four times per living entity per tick on both sides (stun, frenzy, electricity, ability) plus HUD and
 * ability events. Both calls go to the layer's kept handle (see HandleCallers). All four providers answer
 * CAP.orEmpty(cap, their private final LazyOptional field) (guard lines), so absent entries are kept too (only players carry
 * the ability provider).
 */
public final class EeeabHandles {
    public static final HandleCallers KEY = new HandleCallers("eeeabsmobs_capability_handles", "eeeabsmobsCapabilityHandles", true)
            .register(CapabilityHandler.STUN_CAPABILITY, CapabilityHandler.FRENZY_CAPABILITY, CapabilityHandler.ELECTRICITY_CAPABILITY,
                    CapabilityHandler.ABILITY_CAPABILITY);

    private EeeabHandles() {
    }
}
