package bons.furious.mixin.mowziesmobs;

import bons.furious.patch.mowziesmobs.MowzieHandleHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mowziesmobs_capability_handles (Minecraft 1.20.1, both sides): one reference field on LivingEntity where
 * MowzieCapabilityHandles keeps the entity's resolved Mowzie's Mobs capability answers. Carries no Minecraft code and
 * changes no behaviour; the field is null until Mowzie's CapabilityHandler first resolves a capability on the entity.
 * Since 1.0.46 it holds a table that is replaced, never changed in place, and the field is volatile, so a reader on
 * another thread sees a whole table.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityCapabilityHandlesMixin implements MowzieHandleHolder {
    @Unique
    private volatile Object bons$mowzieHandleArray;

    @Override
    public Object bons$mowzieHandles() {
        return this.bons$mowzieHandleArray;
    }

    @Override
    public void bons$setMowzieHandles(Object handles) {
        this.bons$mowzieHandleArray = handles;
    }
}
