package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.CallerHandleTableHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * forge_capability_handle_layer (Minecraft 1.20.1 / Forge 47.4.28, both sides; the Level half since 1.0.49): one volatile
 * reference field on Level holding the level's CallerCapabilityHandles table (kept answers of the level capabilities that
 * registered caller switches ask for, e.g. Torchmaster's TEB_REGISTRY). Carries no Minecraft code and changes no
 * behaviour; null until such a capability is first resolved on the level, and the table in it is replaced, never changed in
 * place.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelCallerHandlesMixin implements CallerHandleTableHolder {
    @Unique
    private volatile Object[] bons$levelCallerHandleTable;

    @Override
    public Object[] bons$callerHandles() {
        return this.bons$levelCallerHandleTable;
    }

    @Override
    public void bons$setCallerHandles(Object[] handles) {
        this.bons$levelCallerHandleTable = handles;
    }
}
