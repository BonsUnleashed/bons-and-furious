package bons.furious.mixin.radium_compat;

import bons.furious.patch.radium_compat.PoiSectionsSpliterator;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * vanilla_poi_chunk_sections (Minecraft 1.20.1), part 1 of 2.
 *
 * Radium's mixin.ai.poi, which replaces the point-of-interest lookups with an indexed version, is forced off in this pack
 * by Valkyrien Skies (its own POI hooks cannot apply on top of Radium's @Overwrite, VS #1485). Every POI search therefore
 * runs vanilla's getInChunk for each chunk in range: one stream pipeline per chunk and one SectionPos per section. This
 * returns the same records in the same order, looking the sections up through PoiSectionsSpliterator (same getOrLoad
 * calls, same order, equally lazy). Applied only while Radium's own ai.poi mixin is not (RadiumCompatPlugin).
 */
@Mixin(value = PoiManager.class, remap = false)
public abstract class PoiChunkSectionsMixin {
    /**
     * @author BonsUnleashed
     * @reason Same sections, same order, same laziness as vanilla, without a stream pipeline and a SectionPos per section.
     */
    @Overwrite
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> predicate, ChunkPos chunkPos, PoiManager.Occupancy occupancy) {
        SectionStorageAccess storage = (SectionStorageAccess) this;
        LevelHeightAccessor height = storage.bons$heightAccessor();
        return StreamSupport.stream(new PoiSectionsSpliterator(storage, chunkPos.x, chunkPos.z,
                        height.getMinSection(), height.getMaxSection()), false)
                .flatMap(section -> section.getRecords(predicate, occupancy));
    }
}
