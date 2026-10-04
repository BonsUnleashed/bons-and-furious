package bons.furious.mixin.ticker_gate;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** vanilla_ticker_range_memo (Minecraft 1.20.1): read access to DistanceManager.tickingTicketsTracker (f_183901_). */
@Mixin(value = DistanceManager.class, remap = false)
public interface DistanceManagerTrackerAccessor {
    @Accessor("f_183901_")
    TickingTracker bons$tickingTracker();
}
