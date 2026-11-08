package bons.furious.mixin.biolith_restamp_c49;

import bons.furious.patch.biolith_restamp_c49.SourceRestamp;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * biolith_swapped_source_restamp (Biolith; 1.21.1 tested build: Biolith 3.0.14 for NeoForge; server; a gameplay fix, off
 * by default): at the start of MinecraftServer.createLevels the LevelStems' untagged Biolith biome sources get their
 * dimension type (SourceRestamp). Priority 2100: after other mods' injections at the same point, so a source they swap in
 * there is seen too. Our own logic only.
 *
 * Ported to 1.21.1: createLevels(ChunkProgressListener) still reads the LEVEL_STEM registry of the composite registry
 * access (registryAccess()) and builds every ServerLevel after ServerAboutToStartEvent; Biolith 3.0.14 still tags sources
 * only in LevelStem's constructor (MixinDimensionOptions and MixinBiomeSource are byte-identical to 3.0.10's).
 */
@Mixin(value = MinecraftServer.class, priority = 2100, remap = false)
public abstract class CreateLevelsRestampMixin {
    @Inject(method = "createLevels(Lnet/minecraft/server/level/progress/ChunkProgressListener;)V", at = @At("HEAD"))
    private void bons$restampSwappedSources(ChunkProgressListener listener, CallbackInfo ci) {
        SourceRestamp.restamp(((MinecraftServer) (Object) this).registryAccess().registryOrThrow(Registries.LEVEL_STEM));
    }
}
