package bons.furious.mixin.worldgen_aquifer;

import bons.furious.patch.worldgen_aquifer.AquiferHighAir;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_aquifer_high_air (Minecraft 1.21.1 world generation, server side; tested build NeoForge 21.1.252): only adds
 * the AquiferHighAir marker to Aquifer$NoiseBasedAquifer. vanilla_aquifer_candidate_cache's fast path returns air at once
 * for a block that is at least 5 above the three nearest candidates' fluid levels when rank 2's and rank 3's statuses are
 * already cached (why that is exactly vanilla's result and why no computeFluid call moves: AquiferCandidates). Without
 * that switch this marker does nothing. Minecraft is no-copy: nothing of the class is carried.
 *
 * Ported to 1.21.1: unchanged (interface-only mixin; Mojang names as on 1.20.1's mapped source).
 */
@Mixin(value = Aquifer.NoiseBasedAquifer.class, remap = false)
public abstract class AquiferHighAirMixin implements AquiferHighAir {
}
