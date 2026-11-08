package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickPark;
import bons.furious.patch.vanilla_tick_lists.TickParkChunkMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_level_ticks_blocked_park (Minecraft 1.21.1 on NeoForge 21.1.252, server side): the tick check reads the visible
 * chunk holder (getVisibleChunkIfPresent, public, through ServerChunkCache.isPositionTicking), and only promoteChunkMap
 * replaces the visible holder map. When it answers true, the level's router compares every parked chunk's holder with the
 * one it had when it was parked. The holder lookup is offered to TickPark through TickParkChunkMap. A RETURN injection;
 * the other ChunkMap mixins among the 1.21.1 targets leave both methods alone, except C2ME's chunk system rewrite, with
 * which no level parks (TickParkAttach).
 */
@Mixin(value = ChunkMap.class, remap = false)
public abstract class ChunkMapParkMixin implements TickParkChunkMap {
    @Unique
    private TickPark.Router bons$tickParkRouter;

    @Override
    public Object bons$visibleHolder(long chunk) {
        return ((ChunkMap) (Object) this).getVisibleChunkIfPresent(chunk);
    }

    @Override
    public void bons$setTickParkRouter(TickPark.Router router) {
        this.bons$tickParkRouter = router;
    }

    @Inject(method = "promoteChunkMap()Z", at = @At("RETURN"))
    private void bons$holdersPromoted(CallbackInfoReturnable<Boolean> cir) {
        TickPark.Router r = this.bons$tickParkRouter;
        if (r != null && cir.getReturnValueZ()) {
            r.holdersChanged();
        }
    }
}
