package bons.furious.mixin.soulsweapons_coo49;

import bons.furious.patch.soulsweapons_coo49.SoulsEntityPeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * soulsweapons_despawn_timer_no_write (Soulslike Weaponry 1.4.10, CC0-1.0; server side; since 1.0.49): read access to
 * Forge's Entity.persistentData field (a Forge-added name, not an SRG one), which Entity.getPersistentData() creates on its
 * first call and Entity.saveWithoutId writes as ForgeData whenever it exists, and one byte per entity that keeps
 * SoulsDespawnPeek's "does this class use Forge's getPersistentData" answer, so the per-tick read does not look it up
 * again. Lets SoulsDespawnPeek read the field without creating it. Adds no behaviour by itself.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class SoulsEntityPeekMixin implements SoulsEntityPeek {
    @Shadow(remap = false)
    private CompoundTag persistentData;

    @Unique
    private byte bons$soulsweaponsCoo49Plain;

    @Override
    public CompoundTag bons$soulsweaponsCoo49PersistentData() {
        return this.persistentData;
    }

    @Override
    public byte bons$soulsweaponsCoo49Plain() {
        return this.bons$soulsweaponsCoo49Plain;
    }

    @Override
    public void bons$soulsweaponsCoo49Plain(byte plain) {
        this.bons$soulsweaponsCoo49Plain = plain;
    }
}
