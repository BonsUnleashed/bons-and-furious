package bons.furious.mixin.c2me_compat;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Switch radium_untrack_chunk_hooks (Radium Re-Reforged 0.14.3): applied whenever Radium's mixin.world.player_chunk_tick
 * applies, with or without C2ME.
 *
 * Radium's ChunkMap.move visits only the chunks that enter or leave a moving player's view and drops a leaving chunk
 * with ServerPlayer.untrackChunk directly. Vanilla drops it through ChunkMap.updateChunkTracking, and mods hook that
 * method: SecurityCraft 1.10.1 cancels the drop while a camera shown on a frame still needs the chunk. The drop now goes
 * through updateChunkTracking(player, pos, packet cache, wasLoaded = true, load = false), which calls untrackChunk
 * exactly as before when no mod cancels it. Radium only reaches this for a player in this ChunkMap's level, the same
 * condition updateChunkTracking checks.
 */
@Mixin(value = ChunkMap.class, priority = 1500, remap = false)
public abstract class RadiumUntrackHookMixin {
    @Shadow
    protected abstract void m_183754_(ServerPlayer player, ChunkPos pos, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache,
                                      boolean wasLoaded, boolean load);

    @Redirect(method = "stopWatchingChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;m_9088_(Lnet/minecraft/world/level/ChunkPos;)V"))
    private void bons$untrackThroughUpdateChunkTracking(ServerPlayer player, ChunkPos pos) {
        this.m_183754_(player, pos, new MutableObject<>(), true, false);
    }
}
