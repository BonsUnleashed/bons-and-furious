package bons.furious.patch.vanilla_tick_lists;

/**
 * vanilla_level_ticks_blocked_park: implemented by DistanceManager (DistanceManagerParkMixin): its ticking-ticket tracker
 * (f_183901_), whose levels decide inBlockTickingRange. No Mojang code.
 */
public interface TickParkDistanceManager {
    Object bons$tickingTracker();
}
