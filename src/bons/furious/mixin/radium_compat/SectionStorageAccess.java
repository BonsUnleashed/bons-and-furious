package bons.furious.mixin.radium_compat;

import java.util.Optional;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * vanilla_poi_chunk_sections, part 2 of 2: SectionStorage.getOrLoad (m_63823_) and the storage's height accessor
 * (f_156618_), for PoiSectionsSpliterator.
 */
@Mixin(value = SectionStorage.class, remap = false)
public interface SectionStorageAccess {
    @Invoker(value = "m_63823_", remap = false)
    Optional<?> bons$getOrLoad(long sectionKey);

    @Accessor(value = "f_156618_", remap = false)
    LevelHeightAccessor bons$heightAccessor();
}
