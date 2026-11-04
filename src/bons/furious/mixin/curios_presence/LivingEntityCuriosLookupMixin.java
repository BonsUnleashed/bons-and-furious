package bons.furious.mixin.curios_presence;

import bons.furious.patch.curios_presence.CuriosLookupState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * curios_inventory_lookup_layer (Minecraft 1.20.1, both sides, since 1.0.49): one reference field on LivingEntity where
 * CuriosInventoryLookup keeps the entity's last learned Curios inventory answer (an immutable record: the dispatcher and
 * Curios' own LazyOptional, or "empty"). Carries no Minecraft code and changes no behaviour; null until the first lookup
 * that can be kept, replaced (never changed in place) when the answer changes.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityCuriosLookupMixin implements CuriosLookupState {
    @Unique
    private Object bons$curiosLookupKept;

    @Override
    public Object bons$curiosLookup() {
        return this.bons$curiosLookupKept;
    }

    @Override
    public void bons$setCuriosLookup(Object kept) {
        this.bons$curiosLookupKept = kept;
    }
}
