package bons.furious.mixin.vanilla_template_memory;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_template_top_air_trim (Minecraft 1.20.1, both sides): access to a template palette's block list (f_74645_, the
 * ArrayList load builds) for StructureTemplateTopAirMixin. An accessor only.
 */
@Mixin(value = StructureTemplate.Palette.class, remap = false)
public interface PaletteBlocksAccess {
    @Accessor(value = "f_74645_", remap = false)
    List<StructureTemplate.StructureBlockInfo> bons$blocks();
}
