package bons.furious.patch.radium_compat;

import bons.furious.mixin.radium_compat.SectionStorageAccess;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiSection;

/**
 * Switch vanilla_poi_chunk_sections: the point-of-interest sections of one chunk column, bottom to top, for
 * PoiManager.getInChunk.
 *
 * Vanilla builds IntStream.range(minSection, maxSection).boxed().map(getOrLoad(SectionPos.of(chunk, y).asLong()))
 * .filter(Optional::isPresent): a stream pipeline and one SectionPos per section of the column, for every chunk a
 * villager, an iron golem, a bee or a modded block searches. This source calls getOrLoad for the same keys in the same
 * order and just as lazily: a section is looked up only when the stream asks for the next element, so a search that
 * stops early (findFirst, anyMatch) touches no section vanilla would not touch. That matters because getOrLoad reads the
 * whole column from disk when a key is missing, which Chunk Pregenerator's memory cleanup can cause mid-column.
 */
public final class PoiSectionsSpliterator extends Spliterators.AbstractSpliterator<PoiSection> {
    private final SectionStorageAccess storage;
    private final int x;
    private final int z;
    private final int maxSection;
    private int y;

    public PoiSectionsSpliterator(SectionStorageAccess storage, int x, int z, int minSection, int maxSection) {
        super(Math.max(0, maxSection - minSection), Spliterator.ORDERED | Spliterator.NONNULL);
        this.storage = storage;
        this.x = x;
        this.z = z;
        this.y = minSection;
        this.maxSection = maxSection;
    }

    @Override
    public boolean tryAdvance(Consumer<? super PoiSection> action) {
        while (this.y < this.maxSection) {
            Optional<?> section = this.storage.bons$getOrLoad(SectionPos.m_123209_(this.x, this.y++, this.z));
            if (section.isPresent()) {
                action.accept((PoiSection) section.get());
                return true;
            }
        }
        return false;
    }
}
