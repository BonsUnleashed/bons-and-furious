package bons.furious.mixin.flywheel_level_attached;

import bons.furious.patch.flywheel_level_attached.AttachedPins;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * flywheel_level_attached_weak_keys (Flywheel 1.0.5 inside Create 6.0.8), client only: every Level carries the values
 * Flywheel's LevelAttached caches hold for it (pairs cache token, value; copy-on-write, written under
 * AttachedPins' lock), so a value lives exactly as long as its level does. Null until a LevelAttached cache loads a value
 * for the level. Nothing else of Level is touched.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelAttachedPinsMixin implements AttachedPins.Holder {
    @Unique
    private volatile Object[] bons$pins;

    @Override
    public Object[] bons$attachedPins() {
        return this.bons$pins;
    }

    @Override
    public void bons$setAttachedPins(Object[] pins) {
        this.bons$pins = pins;
    }
}
