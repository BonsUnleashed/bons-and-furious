package bons.furious.mixin.uniqueaccessories_coo49;

import bons.furious.patch.uniqueaccessories_coo49.UaRoseCandidates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * uniqueaccessories_rose_cleanup_candidates (since 1.0.49): read access to Forge's Entity.persistentData field, so the
 * candidate set can read a mob's boost value without creating its ForgeData. Adds no behaviour by itself.
 */
@Mixin(value = Entity.class, remap = false)
public interface UaRoseDataAccessor extends UaRoseCandidates.Data {
    @Override
    @Accessor(value = "persistentData", remap = false)
    CompoundTag bons$uniqueaccessoriesCoo49RoseData();
}
