package bons.furious.mixin.curios;

import bons.furious.patch.curios.CuriosSlotlessState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * curios_slotless_tick_skip (Minecraft 1.20.1, both sides): one reference field on LivingEntity where CuriosSlotlessTick
 * remembers the capability dispatcher for which the entity's Curios inventory lookup came back empty. Carries no
 * Minecraft code and changes no behaviour; null until the first such lookup.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityCuriosStateMixin implements CuriosSlotlessState {
    @Unique
    private Object bons$curiosEmptyDispatcherRef;

    @Override
    public Object bons$curiosEmptyDispatcher() {
        return this.bons$curiosEmptyDispatcherRef;
    }

    @Override
    public void bons$setCuriosEmptyDispatcher(Object dispatcher) {
        this.bons$curiosEmptyDispatcherRef = dispatcher;
    }
}
