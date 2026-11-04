package bons.furious.patch.fallentraits_coo49;

import net.minecraft.nbt.CompoundTag;

/**
 * fallentraits_unset_persistent_data_skip: what FtPersistentDataAccessor adds to Entity: Forge's persistentData field read
 * without creating it, and the entity's class state for FtPeek (0 not looked at yet, FtPeek.FORGE, FtPeek.OWN).
 */
public interface FtPeekDuck {
    CompoundTag bons$fallentraitsCoo49PersistentData();

    byte bons$fallentraitsCoo49PeekClass();

    void bons$fallentraitsCoo49PeekSetClass(byte state);
}
