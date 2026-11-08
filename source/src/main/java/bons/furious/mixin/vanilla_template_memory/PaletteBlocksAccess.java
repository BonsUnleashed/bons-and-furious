package bons.furious.mixin.vanilla_template_memory;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_template_top_air_trim (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): access to a template palette's
 * block list (blocks, the ArrayList buildInfoList makes for load) for StructureTemplateTopAirMixin. An accessor only.
 */
@Mixin(value = StructureTemplate.Palette.class, remap = false)
public interface PaletteBlocksAccess {
    @Accessor(value = "blocks", remap = false)
    List<StructureTemplate.StructureBlockInfo> bons$blocks();
}
