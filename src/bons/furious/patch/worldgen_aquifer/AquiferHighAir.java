package bons.furious.patch.worldgen_aquifer;

/**
 * vanilla_aquifer_high_air (Minecraft 1.20.1 world generation, server side): marker that AquiferHighAirMixin adds to
 * Aquifer$NoiseBasedAquifer when that switch's mixins apply. vanilla_aquifer_candidate_cache's fast path takes the early
 * air return only on aquifers that carry it (and while AquiferCandidates.highAirEnabled), so each switch can be turned
 * off on its own; see AquiferCandidates for why the return is identical.
 */
public interface AquiferHighAir {
}
