package bons.furious.mixin.brutality_coo49;

import java.util.Set;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * brutality_effect_sync_tracking_scope (vanilla ChunkMap$TrackedEntity, server; since 1.0.49): read access to the set of
 * player connections that have the entity (seenBy), used by BrutalitySync.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity", remap = false)
public interface BrutalityCoo49TrackedEntityAccessor {
    @Accessor("f_140475_")
    Set<ServerPlayerConnection> bons$brutalityCoo49SeenBy();
}
