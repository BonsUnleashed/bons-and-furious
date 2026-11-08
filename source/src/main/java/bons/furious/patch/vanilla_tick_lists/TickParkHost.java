package bons.furious.patch.vanilla_tick_lists;

/**
 * vanilla_level_ticks_blocked_park: implemented by LevelTicks (LevelTicksParkMixin). The park state of that LevelTicks,
 * null while it runs the stock walk (not a vanilla ServerLevel's, or the level was declined). No Mojang code.
 */
public interface TickParkHost {
    TickPark.Parked bons$tickPark();

    void bons$setTickPark(TickPark.Parked parked);
}
