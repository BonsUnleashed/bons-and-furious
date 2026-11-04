package bons.furious.mixin.brutality_coo49;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * brutality_effect_sync_tracking_scope (vanilla ChunkMap, server; since 1.0.49): read access to ChunkMap's entity tracker
 * map (entity id -> TrackedEntity), used by BrutalitySync to find who tracks the entity a capability sync is about.
 */
@Mixin(value = ChunkMap.class, remap = false)
public interface BrutalityCoo49ChunkMapAccessor {
    @Accessor("f_140150_")
    Int2ObjectMap<?> bons$brutalityCoo49EntityMap();
}
