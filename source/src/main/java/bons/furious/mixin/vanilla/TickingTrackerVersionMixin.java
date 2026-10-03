package bons.furious.mixin.vanilla;

import bons.furious.patch.vanilla.TickingLevels;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_block_ticking_range_memo (Minecraft 1.20.1), part 1 of 2: TickingTracker gets a write version and the memo slot
 * TickingLevels uses. setLevel, the only writer of the ticking-level map, advances the version at its start and at its
 * return; it runs during ticket propagation, not per block entity.
 */
@Mixin(value = TickingTracker.class, remap = false)
public abstract class TickingTrackerVersionMixin implements TickingLevels.Tracker {
    @Unique
    private int bons$version;
    @Unique
    private volatile Object bons$memo;

    @Shadow
    protected abstract int getLevel(long chunk);

    @Override
    public int bons$version() {
        return this.bons$version;
    }

    @Override
    public Object bons$memo() {
        return this.bons$memo;
    }

    @Override
    public void bons$memo(Object memo) {
        this.bons$memo = memo;
    }

    @Override
    public int bons$lookup(long chunk) {
        return this.getLevel(chunk);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void bons$beforeWrite(long chunk, int level, CallbackInfo ci) {
        this.bons$version++;
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void bons$afterWrite(long chunk, int level, CallbackInfo ci) {
        this.bons$version++;
    }
}
