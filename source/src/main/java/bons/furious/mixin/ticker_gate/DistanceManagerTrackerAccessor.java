package bons.furious.mixin.ticker_gate;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_ticker_range_memo (Minecraft 1.21.1 with NeoForge 21.1.252): read access to DistanceManager.tickingTicketsTracker.
 *
 * Ported to 1.21.1: same field (private final TickingTracker tickingTicketsTracker = new TickingTracker()), Mojang name.
 */
@Mixin(value = DistanceManager.class, remap = false)
public interface DistanceManagerTrackerAccessor {
    @Accessor("tickingTicketsTracker")
    TickingTracker bons$tickingTracker();
}
