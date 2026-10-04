package bons.furious.mixin.radium_fixes;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * radium_hash_palette_copy (Radium Re-Reforged 0.14.3, both sides): read access to LithiumHashPalette's private
 * entry-to-id map ("table"), for HashPaletteCopyMixin. Accessor only; no behaviour of its own.
 */
@Mixin(targets = "me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette", remap = false)
public interface HashPaletteTableAccessor {
    @Accessor("table")
    Reference2IntMap<?> bons$table();
}
