package bons.furious.mixin.cablefacades;

import java.util.Map;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cablefacades_no_facade_fast_exit (Cable Facades 2.1.3, both sides): read access to DimensionDataStorage's cache map, the
 * map computeIfAbsent reads first, so CfFastExit can see whether a level's CableFacadeSavedData exists without loading it.
 * Applied only with the switch (its guard needs Cable Facades).
 */
@Mixin(value = DimensionDataStorage.class, remap = false)
public interface DimensionDataStorageCacheAccessor {
    @Accessor("f_78144_")
    Map<String, SavedData> bons$cfCache();
}
