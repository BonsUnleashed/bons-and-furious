package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickParkDistanceManager;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_level_ticks_blocked_park (Minecraft 1.20.1, server side): offers the distance manager's ticking-ticket tracker
 * (f_183901_) to TickParkAttach, which parks a level only while that tracker is exactly the vanilla TickingTracker (C2ME's
 * no-tick view distance replaces it with its own map). Read only; no injection.
 */
@Mixin(value = DistanceManager.class, remap = false)
public abstract class DistanceManagerParkMixin implements TickParkDistanceManager {
    @Shadow
    private TickingTracker f_183901_;

    @Override
    public Object bons$tickingTracker() {
        return this.f_183901_;
    }
}
