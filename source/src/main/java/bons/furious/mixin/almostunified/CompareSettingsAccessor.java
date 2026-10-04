package bons.furious.mixin.almostunified;

import com.almostreliable.unified.utils.JsonCompare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * almostunified_duplicate_groups (Almost Unified 1.4.2 for NeoForge 1.21.1, both sides): read access to
 * JsonCompare.CompareSettings' handleImplicitCounts flag (when it is on, JsonCompare.matches sanitizes values before
 * comparing them, so values are not part of the duplicate key). Read only.
 *
 * Ported to 1.21.1: AU 1.x renamed the 0.11.0 flag shouldSanitize (config key shouldSanitize) to handleImplicitCounts
 * (config key handle_implicit_counts); matches uses it exactly where 0.11.0 used shouldSanitize.
 */
@Mixin(value = JsonCompare.CompareSettings.class, remap = false)
public interface CompareSettingsAccessor {
    @Accessor(value = "handleImplicitCounts", remap = false)
    boolean bons$handleImplicitCounts();
}
