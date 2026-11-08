package bons.furious.mixin.structurify_set_reuse;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * structurify_set_entries_reuse: a getter for Structurify's answer field structurify$structures, which Structurify's own
 * StructureSetMixin adds to StructureSet (Structurify 2.0.42 for NeoForge 1.21.1). A @Shadow cannot name it (Mixin checks
 * shadows against the class before any mixin of that class is applied); an accessor is resolved in Mixin's accessor pass,
 * after every mixin's fields are in the class. Our own code only (reads the field, never writes it).
 */
@Mixin(value = StructureSet.class, remap = false)
public interface StructurifyAnswerAccess {
    @Accessor(value = "structurify$structures", remap = false)
    List<StructureSet.StructureSelectionEntry> bons$structurifyAnswer();
}
