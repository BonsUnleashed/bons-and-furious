package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickPark;
import bons.furious.patch.vanilla_tick_lists.TickParkChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_level_ticks_blocked_park (Minecraft 1.20.1, server side): the tick check reads the visible chunk holder
 * (getVisibleChunkIfPresent, m_140327_), and only promoteChunkMap (m_140324_) replaces the visible holder map. When it
 * answers true, the level's router compares every parked chunk's holder with the one it had when it was parked. The
 * holder lookup is offered to TickPark through TickParkChunkMap. A RETURN injection; the pack's other ChunkMap mixins do
 * not touch either method.
 */
@Mixin(value = ChunkMap.class, remap = false)
public abstract class ChunkMapParkMixin implements TickParkChunkMap {
    @Unique
    private TickPark.Router bons$tickParkRouter;

    @Shadow
    protected abstract ChunkHolder m_140327_(long chunk);

    @Override
    public Object bons$visibleHolder(long chunk) {
        return this.m_140327_(chunk);
    }

    @Override
    public void bons$setTickParkRouter(TickPark.Router router) {
        this.bons$tickParkRouter = router;
    }

    @Inject(method = "m_140324_", at = @At("RETURN"))
    private void bons$holdersPromoted(CallbackInfoReturnable<Boolean> cir) {
        TickPark.Router r = this.bons$tickParkRouter;
        if (r != null && cir.getReturnValueZ()) {
            r.holdersChanged();
        }
    }
}
