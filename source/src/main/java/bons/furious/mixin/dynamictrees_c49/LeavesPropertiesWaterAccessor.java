package bons.furious.mixin.dynamictrees_c49;

import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * dynamictrees_leaf_site_single_read (Dynamic Trees 1.7.2 for NeoForge 1.21.1): read access to LeavesProperties'
 * protected waterResistant field, which isLocationSuitableForNewLeaves reads (this.properties.waterResistant) and which has
 * no getter. Adds no behaviour.
 */
@Mixin(value = LeavesProperties.class, remap = false)
public interface LeavesPropertiesWaterAccessor {
    @Accessor(value = "waterResistant", remap = false)
    boolean bons$waterResistant();
}
