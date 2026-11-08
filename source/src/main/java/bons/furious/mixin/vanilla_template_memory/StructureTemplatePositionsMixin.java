package bons.furious.mixin.vanilla_template_memory;

import bons.furious.patch.vanilla_template_memory.TemplatePositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_template_position_share (Minecraft 1.21.1, StructureTemplate not patched by NeoForge 21.1.252 in these methods;
 * both sides; exact variant). @Redirect of the one new BlockPos(int, int, int) in StructureTemplate.loadPalette (a block
 * entry's position) and the one in load (an entity entry's block position; the size is a Vec3i and stays as it is) to
 * TemplatePositions.at: the shared position for coordinates 0..63, a new one otherwise. Copycats' wrap of the new
 * StructureBlockInfo in loadPalette receives the position as before, the injections of Immersive Engineering and Cable
 * Facades at the end of load and the many hooks on placement are untouched. Only our own logic is carried. See
 * TemplatePositions.
 */
@Mixin(value = StructureTemplate.class, remap = false)
public abstract class StructureTemplatePositionsMixin {
    @Redirect(method = "loadPalette(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/ListTag;Lnet/minecraft/nbt/ListTag;)V", allow = 1,
            at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;"))
    private BlockPos bons$sharedBlockPos(int x, int y, int z) {
        return TemplatePositions.at(x, y, z);
    }

    @Redirect(method = "load(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/CompoundTag;)V", allow = 1,
            at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;"))
    private BlockPos bons$sharedEntityPos(int x, int y, int z) {
        return TemplatePositions.at(x, y, z);
    }
}
