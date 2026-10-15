package bons.furious.patch.benssharks;

import bons.furious.guard.Guards;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * benssharks_tick_listeners: at the end of common setup, when (and only when) the switch applies, hand over to
 * {@link SharksSetup} on the main thread. This class names no Ben's Sharks class, so it is safe in every pack; SharksSetup,
 * which does, is only loaded after the switch's fingerprints matched (so Ben's Sharks 1.3.0 is installed).
 */
@Mod.EventBusSubscriber(modid = "bons_and_furious", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SetupHook {
    private SetupHook() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (Guards.decide("benssharks_tick_listeners").state() != Guards.State.APPLY) return;
        event.enqueueWork(() -> SharksSetup.runIfApplied());
    }
}
