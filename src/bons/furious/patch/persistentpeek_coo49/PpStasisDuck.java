package bons.furious.patch.persistentpeek_coo49;

import net.minecraft.nbt.CompoundTag;

/**
 * industrialforegoing_stasis_filter_peek: what PpStasisEntityMixin adds to Entity: Forge's persistentData field read
 * without creating it, and the entity's class state (0 not yet looked at, PersistentPeek.FORGE: the class answers
 * getPersistentData() with Forge's own code, PersistentPeek.OWN: a class on the way declares its own).
 */
public interface PpStasisDuck {
    CompoundTag bons$persistentpeekCoo49StasisData();

    byte bons$persistentpeekCoo49StasisClass();

    void bons$persistentpeekCoo49StasisSetClass(byte state);
}
