package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtPeekDuck;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * fallentraits_unset_persistent_data_skip (server side): reads Forge's Entity.persistentData field without creating it
 * (Forge's getPersistentData() creates an empty compound on the first ask) and keeps the entity's class state for
 * FtPeek.readOnly (one byte, set on the entity's first read). Adds no behaviour.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class FtPersistentDataAccessor implements FtPeekDuck {
    @Shadow(remap = false)
    private CompoundTag persistentData;
    @Unique
    private byte bons$fallentraitsCoo49PeekClassState;

    @Override
    public CompoundTag bons$fallentraitsCoo49PersistentData() {
        return this.persistentData;
    }

    @Override
    public byte bons$fallentraitsCoo49PeekClass() {
        return this.bons$fallentraitsCoo49PeekClassState;
    }

    @Override
    public void bons$fallentraitsCoo49PeekSetClass(byte state) {
        this.bons$fallentraitsCoo49PeekClassState = state;
    }
}
