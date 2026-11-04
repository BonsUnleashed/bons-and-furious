package bons.furious.mixin.vanilla_template_memory;

import bons.furious.patch.vanilla_template_memory.TemplatePositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_template_position_share (Minecraft 1.20.1, SRG client jar 1.20.1-20230612.114412, not patched by Forge 47.4.28;
 * both sides; exact variant). @Redirect of the one new BlockPos(int, int, int) in StructureTemplate.loadPalette
 * (m_247272_, a block entry's position) and the one in load (m_246595_, an entity entry's block position) to
 * TemplatePositions.at: the shared position for coordinates 0..63, a new one otherwise. Immersive Engineering's injection
 * into load and the pack's many hooks on placement are untouched. Only our own logic is carried. See TemplatePositions.
 */
@Mixin(value = StructureTemplate.class, remap = false)
public abstract class StructureTemplatePositionsMixin {
    @Redirect(method = "m_247272_", allow = 1, at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;"))
    private BlockPos bons$sharedBlockPos(int x, int y, int z) {
        return TemplatePositions.at(x, y, z);
    }

    @Redirect(method = "m_246595_", allow = 1, at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;"))
    private BlockPos bons$sharedEntityPos(int x, int y, int z) {
        return TemplatePositions.at(x, y, z);
    }
}
