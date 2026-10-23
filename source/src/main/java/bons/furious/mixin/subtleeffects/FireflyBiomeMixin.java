package bons.furious.mixin.subtleeffects;

import bons.furious.patch.subtleeffects.DisplayTickBiomes;
import einstein.subtle_effects.ticking.FireflyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * subtleeffects_display_tick_biome_reuse (Subtle Effects 1.15.1-hotfix.1, client): FireflyManager.tick's one
 * level.getBiome(pos) goes through DisplayTickBiomes (vanilla_animate_tick_uniform_biome's uniform-section helper when that
 * switch is enabled). Our own logic only (Subtle Effects is All Rights Reserved).
 */
@Mixin(value = FireflyManager.class, remap = false)
public abstract class FireflyBiomeMixin {
    @Redirect(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private static Holder<Biome> bons$seFireflyBiome(Level level, BlockPos pos) {
        return DisplayTickBiomes.firefly(level, pos);
    }
}
