package bons.furious.mixin.chunksending_solo;

import bons.furious.patch.chunksending_solo.ChunkSendingSolo;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.BitSet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * chunksending_solo_no_packet_cache (Chunk Sending 4.1 for Minecraft 1.21, All Rights Reserved: our logic only; server side;
 * since 1.0.49). Trade-off (off by default). MixinSquared @TargetHandler injection at the HEAD of Chunk Sending's packet-cache
 * handler chunksending$prioCached, the @WrapOperation its PlayerChunkSenderPacketCacheMixin places on the packet
 * construction in PlayerChunkSender.sendChunk: while fewer than two players are online the handler returns the packet its
 * own uncached branch builds (the wrapped construction, called through the handler's Operation with the same arguments),
 * so nothing is served from the cache and nothing is stored (ChunkSendingSolo). With two or more players it runs
 * unchanged. Priority 1500: applied after Chunk Sending's mixin (priority 1000). require = 0 / expect = 0: when another mod
 * removes Chunk Sending's mixin there is nothing to change.
 */
@Mixin(value = PlayerChunkSender.class, priority = 1500, remap = false)
public abstract class ChunkSendingSoloMixin {
    @TargetHandler(mixin = "com.chunksending.mixin.cache.PlayerChunkSenderPacketCacheMixin", name = "chunksending$prioCached")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void bons$soloBuildUncached(LevelChunk chunk, LevelLightEngine lightEngine, BitSet skyLight, BitSet blockLight,
                                               Operation<ClientboundLevelChunkWithLightPacket> original, ServerGamePacketListenerImpl listener,
                                               ServerLevel level, LevelChunk sentChunk, CallbackInfoReturnable<ClientboundLevelChunkWithLightPacket> cir) {
        if (ChunkSendingSolo.skipCache(level)) cir.setReturnValue(original.call(chunk, lightEngine, skyLight, blockLight));
    }
}
