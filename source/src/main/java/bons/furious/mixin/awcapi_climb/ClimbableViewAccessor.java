package bons.furious.mixin.awcapi_climb;

import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * awcapi_collision_direct (Advanced Wall Climber API 1.0.3 in Nyf's Spiders 3.0.2, both sides, since 1.0.46): read access to
 * the chunk behind awcapi's per-column "climbable view" (the anonymous BlockGetter ClimberComponent.climbableView builds;
 * its captured field val$getter is the chunk getChunkForCollisions returned). ClimberCollisions uses it only to tell, before
 * asking the view, that a cell lies in an all-air section of a plain LevelChunk. Adds no behaviour; no awcapi code.
 */
@Mixin(targets = "com.nyfaria.awcapi.entity.ClimberComponent$1", remap = false)
public interface ClimbableViewAccessor {
    @Accessor(value = "val$getter", remap = false)
    BlockGetter bons$climbChunk();
}
