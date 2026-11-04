package bons.furious.mixin.vanilla_lookup_loops;

import bons.furious.patch.vanilla_lookup_loops.SingleValueSections;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_single_value_section_get (Minecraft 1.20.1 as patched by Forge 47.4.28, both sides). @Redirect of the one
 * Palette.valueFor (m_5795_) call in PalettedContainer.get(int) (m_63085_) to SingleValueSections.valueFor: a single-value
 * palette with id 0 and a set value answers from its field, every other case makes the original call. The storage read
 * before it (and its range check) is unchanged. Only our own logic is carried. See SingleValueSections.
 */
@Mixin(value = PalettedContainer.class, remap = false)
public abstract class PalettedContainerSingleValueMixin {
    @SuppressWarnings("rawtypes")
    @Redirect(method = "m_63085_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/Palette;m_5795_(I)Ljava/lang/Object;"))
    private Object bons$valueFor(Palette palette, int id) {
        return SingleValueSections.valueFor(palette, id);
    }
}
