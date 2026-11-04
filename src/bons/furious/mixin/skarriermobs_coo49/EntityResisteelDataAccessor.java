package bons.furious.mixin.skarriermobs_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * skarriermobs_resisteel_set_lean (both sides): read access to Forge's Entity.persistentData field without creating it
 * (getPersistentData() creates an empty compound, which is then saved as ForgeData). Used only by
 * SkarrierScans.resisteelUnused; applied only while that switch is on and Skarrier Mobs is present.
 */
@Mixin(value = Entity.class, remap = false)
public interface EntityResisteelDataAccessor {
    @Accessor(value = "persistentData", remap = false)
    CompoundTag bons$skarriermobsCoo49PersistentData();
}
