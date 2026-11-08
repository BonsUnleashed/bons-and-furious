package bons.furious.patch.persistentpeek_coo49;

import net.minecraft.nbt.CompoundTag;

/**
 * industrialforegoing_stasis_filter_peek: what PpStasisEntityMixin adds to Entity: NeoForge's persistentData field read
 * without creating it, and the entity's class state (0 not yet looked at, StasisPeek.NEO: the class answers
 * getPersistentData() with NeoForge's own code, StasisPeek.OWN: a class on the way declares its own).
 */
public interface StasisPeekDuck {
    CompoundTag bons$persistentpeekCoo49StasisData();

    byte bons$persistentpeekCoo49StasisClass();

    void bons$persistentpeekCoo49StasisSetClass(byte state);
}
