package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickParkDistanceManager;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_level_ticks_blocked_park (Minecraft 1.21.1 on NeoForge 21.1.252, server side): offers the distance manager's
 * ticking-ticket tracker (tickingTicketsTracker) to TickParkAttach, which parks a level only while that tracker is exactly
 * the vanilla TickingTracker. Read only; no injection.
 */
@Mixin(value = DistanceManager.class, remap = false)
public abstract class DistanceManagerParkMixin implements TickParkDistanceManager {
    @Shadow
    @Final
    private TickingTracker tickingTicketsTracker;

    @Override
    public Object bons$tickingTracker() {
        return this.tickingTicketsTracker;
    }
}
