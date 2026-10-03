package bons.furious.mixin.radium_compat;

import java.util.Optional;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * vanilla_poi_chunk_sections, part 2 of 2: SectionStorage.getOrLoad (getOrLoad) and the storage's height accessor
 * (levelHeightAccessor), for PoiSectionsSpliterator.
 */
@Mixin(value = SectionStorage.class, remap = false)
public interface SectionStorageAccess {
    @Invoker(value = "getOrLoad", remap = false)
    Optional<?> bons$getOrLoad(long sectionKey);

    @Accessor(value = "levelHeightAccessor", remap = false)
    LevelHeightAccessor bons$heightAccessor();
}
