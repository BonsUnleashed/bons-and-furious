package bons.furious.mixin.vanilla_ticking_prune;

import bons.furious.patch.vanilla_ticking_prune.TickingPrune;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_ticking_tracker_prune (Minecraft 1.20.1 on Forge 47.4.16; server side incl. the integrated server):
 * TickingTracker.setLevel (m_7351_) removes a chunk's entry instead of storing the map's default level (33), which
 * getLevel answers for absent chunks anyway (TickingPrune). Bons' HEAD/RETURN injections in m_7351_
 * (vanilla_block_ticking_range_memo, vanilla_ticker_range_memo) are untouched. No Minecraft code.
 */
@Mixin(value = TickingTracker.class, remap = false)
public abstract class TickingTrackerPruneMixin {
    @WrapOperation(method = "m_7351_", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;put(JB)B"))
    private byte bons$pruneDefault(Long2ByteMap map, long chunk, byte level, Operation<Byte> original) {
        return TickingPrune.put(map, chunk, level, original);
    }
}
