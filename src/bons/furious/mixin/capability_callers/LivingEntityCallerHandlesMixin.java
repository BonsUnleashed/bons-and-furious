package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.CallerHandleTableHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * forge_capability_handle_layer (Minecraft 1.20.1, both sides, since 1.0.46): one volatile reference field on
 * LivingEntity holding the entity's CallerCapabilityHandles table (kept answers of the capabilities that Spawn's,
 * Botania's and Clanging Howl's per-tick helpers ask for). Carries no Minecraft code and changes no behaviour; the field
 * is null until one of those capabilities is first resolved on the entity, and the table in it is replaced, never
 * changed in place.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityCallerHandlesMixin implements CallerHandleTableHolder {
    @Unique
    private volatile Object[] bons$callerHandleTable;

    @Override
    public Object[] bons$callerHandles() {
        return this.bons$callerHandleTable;
    }

    @Override
    public void bons$setCallerHandles(Object[] handles) {
        this.bons$callerHandleTable = handles;
    }
}
