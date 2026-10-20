package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.HandleTableHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * cataclysm_capability_handles (Minecraft 1.20.1, both sides, since 1.0.36): one reference field on LivingEntity holding the
 * entity's PrivateCapabilityHandles table (kept answers for every registered mod-private capability). Carries no
 * Minecraft code and changes no behaviour; the field is null until a registered capability is first resolved. Since
 * 1.0.46 the table is replaced, never changed in place, and the field is volatile, so a reader on another thread sees a
 * whole table.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityTokenHandlesMixin implements HandleTableHolder {
    @Unique
    private volatile Object[] bons$tokenHandleTable;

    @Override
    public Object[] bons$tokenHandles() {
        return this.bons$tokenHandleTable;
    }

    @Override
    public void bons$setTokenHandles(Object[] handles) {
        this.bons$tokenHandleTable = handles;
    }
}
