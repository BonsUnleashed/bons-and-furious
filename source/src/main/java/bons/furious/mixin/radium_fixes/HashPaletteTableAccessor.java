package bons.furious.mixin.radium_fixes;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * radium_hash_palette_copy (Radium, 1.21.1 tested build radium-mc1.21.1-0.13.1+git.4994e83; both sides): read access to
 * LithiumHashPalette's private entry-to-id map ("table"), for HashPaletteCopyMixin. Accessor only; no behaviour of its own.
 * Ported to 1.21.1: unchanged (same private final field).
 */
@Mixin(targets = "me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette", remap = false)
public interface HashPaletteTableAccessor {
    @Accessor("table")
    Reference2IntMap<?> bons$table();
}
