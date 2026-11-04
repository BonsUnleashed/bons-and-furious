package bons.furious.mixin.cataclysm_c49;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cataclysm_deepling_swim_refresh_skip (both sides; since 1.0.49): an entity's current size (read only). Accessor only;
 * its name carries the group (other groups add their own members to Entity).
 */
@Mixin(value = Entity.class, remap = false)
public interface CataclysmEntityDimensionsAccessor {
    @Accessor(value = "f_19815_", remap = false)
    EntityDimensions bons$cataclysmDimensions();
}
