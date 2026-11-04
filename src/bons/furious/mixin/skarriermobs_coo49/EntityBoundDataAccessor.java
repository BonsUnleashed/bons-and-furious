package bons.furious.mixin.skarriermobs_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * skarriermobs_region_scan_narrowing_all (both sides): read access to Forge's Entity.persistentData field without creating
 * it (getPersistentData() creates an empty compound, which is then saved as ForgeData). Used only by
 * SkarrierScans.narrowBound; applied only while that switch is on and Skarrier Mobs is present (each switch has its own
 * accessor, so either can be on alone).
 */
@Mixin(value = Entity.class, remap = false)
public interface EntityBoundDataAccessor {
    @Accessor(value = "persistentData", remap = false)
    CompoundTag bons$skarriermobsCoo49BoundData();
}