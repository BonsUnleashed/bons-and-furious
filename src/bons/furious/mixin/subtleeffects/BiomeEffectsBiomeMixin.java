package bons.furious.mixin.subtleeffects;

import bons.furious.patch.subtleeffects.DisplayTickBiomes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import einstein.subtle_effects.ticking.BiomeEffectsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * subtleeffects_display_tick_biome_reuse (Subtle Effects 1.15.1-hotfix.1, client): BiomeEffectsManager.tick looks the
 * sample's biome up once per call instead of once per registered effect (DisplayTickBiomes explains why the Holder is the
 * one getBiome returns). The first test of the method (state.isCollisionShapeFullBlock) marks the start of a call; the per-effect
 * lookup (the first getBiome call site) is redirected; the surface lookup (second call site) is untouched. Our own logic
 * only.
 */
@Mixin(value = BiomeEffectsManager.class, remap = false)
public abstract class BiomeEffectsBiomeMixin {
    @ModifyExpressionValue(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;m_60838_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z", ordinal = 0))
    private static boolean bons$seBiomeEffectsBegin(boolean fullBlock) {
        return DisplayTickBiomes.begin(fullBlock);
    }

    @Redirect(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_204166_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;", ordinal = 0))
    private static Holder<Biome> bons$seBiomeEffectsBiome(Level level, BlockPos pos) {
        return DisplayTickBiomes.biomeEffects(level, pos);
    }
}
