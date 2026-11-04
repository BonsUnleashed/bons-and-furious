package bons.furious.patch.capability_callers;

import dev.xkmc.l2hostility.content.capability.mob.MobTraitCap;

/**
 * Bons and Furious switch l2hostility_capability_handles (L2 Hostility 2.5.19, All Rights Reserved: no L2 Hostility code is
 * carried; both sides; since 1.0.49). Idea: Collections Of Optimizations (l2hostility.skipTraitlessCapLookup), idea text only.
 * CapabilityEvents.livingTickEvent asks every living entity every tick for MobTraitCap.CAPABILITY (getCapability, then
 * ifPresent); the call goes to the layer's kept handle (see HandleCallers), with absent entries for entities that carry no
 * trait capability (players, entities outside the holder's predicate). The provider is L2 Library's
 * GeneralCapabilitySerializer, which answers its lo field, written only by its constructor (census of every client, server
 * and extra jar, Jar-in-Jar copies included).
 */
public final class L2HostilityHandles {
    public static final HandleCallers KEY = new HandleCallers("l2hostility_capability_handles", "l2hostilityCapabilityHandles", true)
            .register(MobTraitCap.CAPABILITY);

    private L2HostilityHandles() {
    }
}
