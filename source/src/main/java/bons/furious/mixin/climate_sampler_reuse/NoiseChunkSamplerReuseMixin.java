package bons.furious.mixin.climate_sampler_reuse;

import bons.furious.patch.climate_sampler_reuse.SamplerReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_chunk_climate_sampler_reuse (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252; both sides).
 *
 * NoiseChunk.cachedClimateSampler maps six functions of the noise router through the NoiseChunk's wrap
 * table and returns a new Climate.Sampler record. The biome stage calls it once per chunk; YUNG's Cave Biomes calls it
 * again for the same NoiseChunk with the same router and spawn target when the terrain fill starts. The second mapping
 * walks six large graphs to arrive at the objects the first one made (the wrap table hands back the first pass's
 * wrappers for equal nodes), so the record it returns holds functions that answer every position exactly like the
 * first record's. The wrapper keeps the first sampler together with the two arguments it was made from and returns it
 * for a later call with the same two argument objects; any other call runs the original and replaces what is kept.
 * A NoiseChunk is used by one thread at a time, so the three fields need no lock.
 *
 * Ported to 1.21.1: cachedClimateSampler is unchanged (six router functions each mapped with this::wrap, then the record),
 * and so is wrap (wrapped.computeIfAbsent(function, this::wrapNew)); Mojang names, nothing else changed. In the 1.21.1 jars
 * looked at for the port the second request comes from Bye Pregen 1.1.3.0 (its NoiseChunk mixin forwards to this method);
 * no 1.21.1 build of YUNG's Cave Biomes was among them.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkSamplerReuseMixin {
    @Unique
    private Climate.Sampler bons$keptSampler;
    @Unique
    private Object bons$keptSamplerRouter;
    @Unique
    private Object bons$keptSamplerSpawn;

    @WrapMethod(method = "cachedClimateSampler")
    private Climate.Sampler bons$oneSampler(NoiseRouter router, List<Climate.ParameterPoint> spawnTarget, Operation<Climate.Sampler> original) {
        Climate.Sampler kept = this.bons$keptSampler;
        if (kept != null && SamplerReuse.ACTIVE && this.bons$keptSamplerRouter == router && this.bons$keptSamplerSpawn == spawnTarget) {
            SamplerReuse.served();
            return kept;
        }
        Climate.Sampler made = original.call(router, spawnTarget);
        this.bons$keptSampler = made;
        this.bons$keptSamplerRouter = router;
        this.bons$keptSamplerSpawn = spawnTarget;
        SamplerReuse.made();
        return made;
    }
}
