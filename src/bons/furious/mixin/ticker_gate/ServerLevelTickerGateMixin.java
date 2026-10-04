package bons.furious.mixin.ticker_gate;

import bons.furious.patch.ticker_gate.TickerGate;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_ticker_range_memo (Minecraft 1.20.1 on Forge 47.4.16; server): ServerLevel.shouldTickBlocksAt(long) (m_183438_)
 * ends in distanceManager.inBlockTickingRange(chunk) (m_183916_). The redirect hands that call to TickerGate, which answers
 * from the block-entity ticker the level's loop has just handed over (its remembered answer, while the chunk's region
 * epoch is unchanged) and otherwise makes the same call. Every other caller of shouldTickBlocksAt finds no ticker and gets
 * the call as before. The chunk source, chunk map and getDistanceManager reads before the call are unchanged. A plain
 * @Redirect (no allocation); no other mixin in the pack targets this method. vanilla_block_ticking_range_memo redirects a
 * different call (TickingTracker.getLevel inside inBlockTickingRange), so the two compose. No Minecraft code.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelTickerGateMixin {
    @Redirect(method = "m_183438_", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager;m_183916_(J)Z"))
    private boolean bons$tickerRange(DistanceManager manager, long chunk) {
        return TickerGate.inRange(this, manager, chunk);
    }
}
