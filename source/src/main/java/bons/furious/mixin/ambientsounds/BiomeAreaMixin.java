package bons.furious.mixin.ambientsounds;

import agentcraft.consolidated.bons_pure_optimizations.team.creative.ambientsounds.condition.AcBiomeMatchCache;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import team.creative.ambientsounds.condition.BiomeCondition;
import team.creative.ambientsounds.environment.BiomeEnvironment;

/**
 * ambientsounds_biome_match_cache (AmbientSounds 6.3.8, client).
 *
 * checkBiome ran every biome regex against a freshly built identifier string on each environment scan. Both regex
 * checks now go through AcBiomeMatchCache.matches, a memo of the pure (pattern, identifier) result with weak pattern
 * keys and at most 256 identifiers per pattern. Tags and conditions are still read live; only the regex answer for a
 * given identifier is reused.
 */
@Mixin(value = BiomeEnvironment.BiomeArea.class, remap = false)
public abstract class BiomeAreaMixin {
    @Shadow
    @Final
    public Holder<Biome> biome;

    @Shadow
    @Final
    public ResourceLocation location;

    /**
     * @author BonsUnleashed
     * @reason Answer both biome regex checks from the (pattern, identifier) memo.
     */
    @Overwrite
    public boolean checkBiome(BiomeCondition[] conditions) {
        for (BiomeCondition condition : conditions) {
            if (condition.tag()) {
                if (this.biome.tags().anyMatch(x -> AcBiomeMatchCache.matches(condition.pattern(), x.location())))
                    return true;
            } else if (AcBiomeMatchCache.matches(condition.pattern(), this.location))
                return true;
        }
        return false;
    }

    /**
     * AmbientSounds compiles the tag test inside checkBiome to the synthetic method lambda$checkBiome$0. It gets the
     * same memoized body as the lambda above (which javac also names lambda$checkBiome$0; Mixin keeps one of the two
     * identical bodies under that name), so no copy of the old per-call regex test stays in the class.
     *
     * @author BonsUnleashed
     * @reason Same memoized tag test as in checkBiome.
     */
    @Overwrite(aliases = "lambda$checkBiome$0")
    private static boolean tagMatches(BiomeCondition condition, TagKey<Biome> x) {
        return AcBiomeMatchCache.matches(condition.pattern(), x.location());
    }
}
