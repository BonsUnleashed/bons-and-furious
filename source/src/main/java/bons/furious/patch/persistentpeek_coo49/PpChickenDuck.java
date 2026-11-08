package bons.furious.patch.persistentpeek_coo49;

import net.minecraft.nbt.CompoundTag;

/**
 * mobgrindingutils_chicken_tag_peek: what PpChickenEntityMixin adds to Entity: NeoForge's persistentData field read without
 * creating it, and the entity's class state (0 not yet looked at, PpChickenPeek.NEOFORGE: the class answers
 * getPersistentData() with Entity's own code, PpChickenPeek.OWN: a class on the way declares its own).
 */
public interface PpChickenDuck {
    CompoundTag bons$persistentpeekCoo49ChickenData();

    byte bons$persistentpeekCoo49ChickenClass();

    void bons$persistentpeekCoo49ChickenSetClass(byte state);
}
