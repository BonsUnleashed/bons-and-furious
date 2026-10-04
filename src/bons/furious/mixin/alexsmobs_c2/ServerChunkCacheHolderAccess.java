package bons.furious.mixin.alexsmobs_c2;

import bons.furious.patch.alexsmobs_c2.CrowPumpkinScan;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * alexsmobs_crow_scan_palette_check (Alex's Mobs 1.22.9; both sides), part 2 of 2: an invoker for ServerChunkCache's
 * private getVisibleChunkIfPresent (m_8364_), a chunk-map lookup without the lookup cache, tickets or loading, so
 * CrowPumpkinScan can check that every chunk of the crow's search box is already a full chunk before anything else.
 */
@Mixin(value = ServerChunkCache.class, remap = false)
public interface ServerChunkCacheHolderAccess extends CrowPumpkinScan.HolderLookup {
    @Override
    @Invoker("m_8364_")
    ChunkHolder bons$visibleHolder(long pos);
}
