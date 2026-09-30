package bons.furious.mixin.tacz;

import com.tacz.guns.entity.sync.core.AcCollections;
import com.tacz.guns.entity.sync.core.DataEntry;
import com.tacz.guns.entity.sync.core.DataHolder;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * tacz_sync_collections (TaCZ 1.1.5), part 1 of 2; part 2 is SyncedEntityDataEventMixin.
 *
 * DataHolder.gatherDirty and gatherAll built their lists with a stream pipeline and Collectors.toList(). TaCZ calls
 * them every server tick for every entity with changed synced data. AcCollections applies the same filters with a
 * plain loop over the same map, so the entries keep their order and the caller still gets a mutable ArrayList.
 */
@Mixin(value = DataHolder.class, remap = false)
public abstract class DataHolderMixin {
    /**
     * @author BonsUnleashed
     * @reason Collect the dirty, synced entries without a stream collector (432 to 80 bytes per call).
     */
    @Overwrite
    public List<DataEntry<?, ?>> gatherDirty() {
        return AcCollections.dirty((DataHolder) (Object) this);
    }

    /**
     * @author BonsUnleashed
     * @reason Collect the synced entries without a stream collector.
     */
    @Overwrite
    public List<DataEntry<?, ?>> gatherAll() {
        return AcCollections.all((DataHolder) (Object) this);
    }
}
