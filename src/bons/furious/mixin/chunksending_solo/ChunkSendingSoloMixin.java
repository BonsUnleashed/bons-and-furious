package bons.furious.mixin.chunksending_solo;

import bons.furious.patch.chunksending_solo.ChunkSendingSolo;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * chunksending_solo_no_packet_cache (Chunk Sending 3.7, All Rights Reserved: our logic only; server side; since 1.0.49).
 * Trade-off (off by default). MixinSquared @TargetHandler injections at the HEAD of Chunk Sending's two packet-cache handlers
 * that its ChunkMapPacketCacheMixin merges into ChunkMap.playerLoadedChunk (m_183760_): while fewer than two players are
 * online each handler returns at once (cancellable @Inject), so the chunk packet is built as without the cache and nothing
 * is stored (ChunkSendingSolo). With two or more players both run unchanged. Priority 1500: applied after Chunk Sending's
 * mixin (priority 1000). require = 0 / expect = 0: when another mod removes Chunk Sending's mixin there is nothing to change.
 */
@Mixin(value = ChunkMap.class, priority = 1500, remap = false)
public abstract class ChunkSendingSoloMixin {
    @Shadow
    @Final
    ServerLevel f_140133_;

    @TargetHandler(mixin = "com.chunksending.mixin.cache.ChunkMapPacketCacheMixin", name = "chunksending$prioCached")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$soloServeNothing(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet, LevelChunk chunk,
                                       CallbackInfo theirs, CallbackInfo ci) {
        if (ChunkSendingSolo.skipServe(this.f_140133_)) ci.cancel();
    }

    @TargetHandler(mixin = "com.chunksending.mixin.cache.ChunkMapPacketCacheMixin", name = "chunksending$cachePacket")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$soloStoreNothing(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet, LevelChunk chunk,
                                       CallbackInfo theirs, CallbackInfo ci) {
        if (ChunkSendingSolo.skipStore(this.f_140133_)) ci.cancel();
    }
}
