package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickPark;
import bons.furious.patch.vanilla_tick_lists.TickParkRouted;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_level_ticks_blocked_park (Minecraft 1.21.1 on NeoForge 21.1.252, server side): TickingTracker.setLevel is the only
 * writer of the levels DistanceManager.inBlockTickingRange reads (ChunkLevel.isBlockTicking(getLevel(chunk))). A level that
 * is block-ticking (isBlockTicking: level 32 or less) tells the router of the tracker's level that this chunk's tick check
 * may now be true. Only the ServerLevel's own ticking tracker carries a router (set by TickParkAttach); every other tracker
 * has none. A HEAD injection beside Bons and Furious' own hooks in this method (vanilla_block_ticking_range_memo,
 * vanilla_ticker_range_memo), which keep working unchanged.
 */
@Mixin(value = TickingTracker.class, remap = false)
public abstract class TickingTrackerParkMixin implements TickParkRouted {
    @Unique
    private TickPark.Router bons$tickParkRouter;

    @Override
    public void bons$setTickParkRouter(TickPark.Router router) {
        this.bons$tickParkRouter = router;
    }

    @Inject(method = "setLevel(JI)V", at = @At("HEAD"))
    private void bons$levelChanged(long chunk, int level, CallbackInfo ci) {
        TickPark.Router r = this.bons$tickParkRouter;
        if (r != null && ChunkLevel.isBlockTicking(level)) {
            r.trigger(chunk);
        }
    }
}
