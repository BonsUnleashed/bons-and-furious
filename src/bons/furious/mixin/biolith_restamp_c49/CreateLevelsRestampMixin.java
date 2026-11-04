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
 * biolith_swapped_source_restamp (Biolith 1.0.1-beta.1, server; a gameplay fix, off by default): at the start of
 * MinecraftServer.createLevels (m_129815_) the LevelStems' untagged Biolith biome sources get their dimension type
 * (SourceRestamp). Priority 2100: after other mods' injections at the same point (BCLib's at 2000), so a source they swap
 * in there is seen too. Our own logic only.
 */
@Mixin(value = MinecraftServer.class, priority = 2100, remap = false)
public abstract class CreateLevelsRestampMixin {
    @Inject(method = "m_129815_", at = @At("HEAD"))
    private void bons$restampSwappedSources(ChunkProgressListener listener, CallbackInfo ci) {
        SourceRestamp.restamp(((MinecraftServer) (Object) this).m_206579_().m_175515_(Registries.f_256862_));
    }
}