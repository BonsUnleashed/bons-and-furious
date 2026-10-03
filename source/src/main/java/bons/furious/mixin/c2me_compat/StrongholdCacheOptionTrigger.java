package bons.furious.mixin.c2me_compat;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Switch modernfix_c2me_cache_strongholds (ModernFix 5.27.77 with C2ME 0.2.0+alpha.12). Never applied: preparing it is
 * the moment bons.furious.compat.C2meCompatPlugin checks whether ModernFix's mixin.perf.cache_strongholds may come back
 * on (C2ME was the only reason it was off, and the tested C2ME build does not touch stronghold placement). It names the
 * class whose ring positions ModernFix caches.
 */
@Mixin(value = ChunkGeneratorStructureState.class, remap = false)
public abstract class StrongholdCacheOptionTrigger {
}
