package bons.furious.patch.radium_compat;

import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Switch vanilla_block_entity_tick_state: counts tag reloads. BlockEntityType.isValid is a set lookup in vanilla, but mods
 * extend it (DeltaBox Lib accepts its signs by block tag), so an answer remembered by a block entity ticker is asked again
 * after tags were reloaded (/reload, or new tags received from a server).
 */
@Mod.EventBusSubscriber(modid = "bons_and_furious")
public final class TagEpoch {
    public static volatile int epoch;

    private TagEpoch() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        epoch++;
    }
}
