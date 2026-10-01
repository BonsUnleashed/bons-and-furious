package bons.furious.mixin.c2me_compat;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Switch radium_c2me_chunk_access (Radium Re-Reforged 0.14.3 with C2ME 0.2.0+alpha.12). Never applied: preparing it is
 * the moment bons.furious.compat.C2meCompatPlugin checks whether Radium's mixin.world.chunk_access may come back
 * on (C2ME's opts.chunk_access module, which replaces it, is switched off). It names the class Radium's option patches.
 */
@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class ChunkAccessOptionTrigger {
}
