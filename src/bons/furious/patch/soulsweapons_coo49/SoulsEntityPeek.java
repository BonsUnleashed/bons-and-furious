package bons.furious.patch.soulsweapons_coo49;

import net.minecraft.nbt.CompoundTag;

/**
 * What SoulsEntityPeekMixin adds to every Entity for SoulsDespawnPeek: read access to Forge's persistentData field
 * (without creating it) and a per-entity mark of whether the entity's class answers getPersistentData with Forge's own
 * code (0 = not looked at yet, 1 = yes, 2 = no; an entity's class never changes).
 */
public interface SoulsEntityPeek {
    CompoundTag bons$soulsweaponsCoo49PersistentData();

    byte bons$soulsweaponsCoo49Plain();

    void bons$soulsweaponsCoo49Plain(byte plain);
}
