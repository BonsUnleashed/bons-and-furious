package bons.furious.patch.worldgen_aquifer;

/**
 * vanilla_aquifer_high_air (Minecraft 1.21.1 world generation, server side; tested build NeoForge 21.1.252): marker that
 * AquiferHighAirMixin adds to Aquifer$NoiseBasedAquifer when that switch's mixins apply. vanilla_aquifer_candidate_cache's
 * fast path takes the early air return only on aquifers that carry it (and while AquiferCandidates.highAirEnabled), so
 * each switch can be turned off on its own; see AquiferCandidates for why the return is identical.
 *
 * Ported to 1.21.1: unchanged (the marker carries no members; NoiseBasedAquifer and FluidStatus are unchanged in 1.21.1).
 */
public interface AquiferHighAir {
}
