package bons.furious.mixin.radium_fixes;

import bons.furious.patch.radium_fixes.HashPaletteCopy;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * radium_hash_palette_copy (Radium Re-Reforged 0.14.3, tested build 0.14.3+git.e141a47, both sides). Fix, latent here.
 *
 * Target: Radium's own LithiumHashPalette.copy() (Palette.m_199814_). It copies the entry-to-id map with
 * new Reference2IntOpenHashMap(map), which keeps the entries but resets the "absent" answer from -1 to 0, so the copy
 * maps every new entry to id 0. The handler gives the returned copy's map the source map's default return value; the
 * copy then indexes exactly as the original (see HashPaletteCopy). The maps are reached through
 * HashPaletteTableAccessor. Radium is LGPL-3.0; no Radium code is carried, only one value set on the copy Radium built.
 */
@Mixin(targets = "me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette", remap = false)
public abstract class HashPaletteCopyMixin {
    @ModifyReturnValue(method = "m_199814_()Lnet/minecraft/world/level/chunk/Palette;", at = @At("RETURN"), require = 1, allow = 1)
    private Palette<?> bons$copyKeepsAbsentMarker(Palette<?> copy) {
        if (HashPaletteCopy.enabled) {
            ((HashPaletteTableAccessor) copy).bons$table().defaultReturnValue(((HashPaletteTableAccessor) this).bons$table().defaultReturnValue());
        }
        return copy;
    }
}
