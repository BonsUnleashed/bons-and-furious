package bons.furious.mixin.almostunified;

import com.almostreliable.unified.utils.JsonCompare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * almostunified_duplicate_groups (Almost Unified 0.11.0, both sides): read access to JsonCompare.CompareSettings'
 * shouldSanitize flag (with sanitizing on, values are not part of the duplicate key). Read only.
 */
@Mixin(value = JsonCompare.CompareSettings.class, remap = false)
public interface CompareSettingsAccessor {
    @Accessor(value = "shouldSanitize", remap = false)
    boolean bons$shouldSanitize();
}
