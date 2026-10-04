package bons.furious.mixin.ticker_gate;

import bons.furious.patch.ticker_gate.TickerGate;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_ticker_range_memo (Minecraft 1.21.1 with NeoForge 21.1.252; server): ServerLevel.shouldTickBlocksAt(long) ends
 * in distanceManager.inBlockTickingRange(chunk). The redirect hands that call to TickerGate, which answers from the
 * block-entity ticker the level's loop has just handed over (its remembered answer, while the chunk's region epoch is
 * unchanged) and otherwise makes the same call. Every other caller of shouldTickBlocksAt finds no ticker and gets the call
 * as before. The chunk source, chunk map and getDistanceManager reads before the call are unchanged. A plain @Redirect (no
 * allocation). vanilla_block_ticking_range_memo redirects a different call (TickingTracker.getLevel inside
 * inBlockTickingRange), so the two compose. No Minecraft code.
 *
 * Ported to 1.21.1: unchanged body ({@code chunkSource.chunkMap.getDistanceManager().inBlockTickingRange(chunk)});
 * selector with descriptor (Level declares the BlockPos overload of the name).
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelTickerGateMixin {
    @Redirect(method = "shouldTickBlocksAt(J)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager;inBlockTickingRange(J)Z"))
    private boolean bons$tickerRange(DistanceManager manager, long chunk) {
        return TickerGate.inRange(this, manager, chunk);
    }
}
