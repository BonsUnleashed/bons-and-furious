package bons.furious.mixin.c2me_compat;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Switch radium_c2me_player_chunk_tick (Radium Re-Reforged 0.14.3 with C2ME 0.2.0+alpha.12). Never applied: preparing
 * it is the moment bons.furious.compat.C2meCompatPlugin checks whether Radium's mixin.world.player_chunk_tick may
 * come back on (C2ME's no-tick view distance module, which replaces it, is switched off). It names the class Radium's
 * option patches.
 */
@Mixin(value = ChunkMap.class, remap = false)
public abstract class PlayerChunkTickOptionTrigger {
}
