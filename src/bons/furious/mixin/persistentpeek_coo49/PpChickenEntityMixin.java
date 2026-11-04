package bons.furious.mixin.persistentpeek_coo49;

import bons.furious.patch.persistentpeek_coo49.PpChickenDuck;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * mobgrindingutils_chicken_tag_peek (both sides): reads Forge's Entity.persistentData field without creating it (Forge's
 * getPersistentData() creates an empty compound on the first ask) and keeps the entity's class state for
 * PersistentPeek.chicken (one byte, set on the entity's first call). Adds no behaviour.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class PpChickenEntityMixin implements PpChickenDuck {
    @Shadow(remap = false)
    private CompoundTag persistentData;
    @Unique
    private byte bons$persistentpeekCoo49ChickenClassState;

    @Override
    public CompoundTag bons$persistentpeekCoo49ChickenData() {
        return this.persistentData;
    }

    @Override
    public byte bons$persistentpeekCoo49ChickenClass() {
        return this.bons$persistentpeekCoo49ChickenClassState;
    }

    @Override
    public void bons$persistentpeekCoo49ChickenSetClass(byte state) {
        this.bons$persistentpeekCoo49ChickenClassState = state;
    }
}
