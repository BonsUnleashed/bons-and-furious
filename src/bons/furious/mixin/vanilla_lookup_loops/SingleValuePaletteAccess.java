package bons.furious.mixin.vanilla_lookup_loops;

import net.minecraft.world.level.chunk.SingleValuePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_single_value_section_get (Minecraft 1.20.1, both sides): read access to SingleValuePalette's value (f_188204_,
 * the field its valueFor returns) for SingleValueSections. An accessor only; SingleValuePalette's code is unchanged.
 */
@Mixin(value = SingleValuePalette.class, remap = false)
public interface SingleValuePaletteAccess {
    @Accessor(value = "f_188204_", remap = false)
    Object bons$value();
}
