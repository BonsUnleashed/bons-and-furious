package bons.furious.mixin.persistentpeek_coo49;

import bons.furious.patch.persistentpeek_coo49.StasisPeekDuck;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * industrialforegoing_stasis_filter_peek (both sides): reads NeoForge's Entity.persistentData field without creating it
 * (NeoForge's getPersistentData() creates an empty compound on the first ask) and keeps the entity's class state for
 * StasisPeek.stasis (one byte, set on the entity's first filter call). Adds no behaviour.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class PpStasisEntityMixin implements StasisPeekDuck {
    @Shadow(remap = false)
    private CompoundTag persistentData;
    @Unique
    private byte bons$persistentpeekCoo49StasisClassState;

    @Override
    public CompoundTag bons$persistentpeekCoo49StasisData() {
        return this.persistentData;
    }

    @Override
    public byte bons$persistentpeekCoo49StasisClass() {
        return this.bons$persistentpeekCoo49StasisClassState;
    }

    @Override
    public void bons$persistentpeekCoo49StasisSetClass(byte state) {
        this.bons$persistentpeekCoo49StasisClassState = state;
    }
}
