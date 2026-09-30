package bons.furious.mixin.tacz;

import agentcraft.pure.TaCZScope;
import com.tacz.guns.entity.sync.core.DataHolder;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * tacz_sync_scope (TaCZ 1.1.5), part 1 of 2; part 2 is TaczLivingEntityMixin.
 *
 * Every synced-data read or write looks up the entity's DataHolder capability. TaCZScope.resolve performs exactly that
 * lookup, except inside the scope that TaCZ's server-side living-entity tick opens around its eight adjacent writes:
 * there the holder of that entity is looked up once and reused while its capability handle stays valid. A different
 * entity, an absent holder or an invalidated handle falls back to the original lookup.
 */
@Mixin(value = SyncedEntityData.class, remap = false)
public abstract class SyncedEntityDataMixin {
    /**
     * @author BonsUnleashed
     * @reason Reuse the holder resolved in the current TaCZ update scope (eight lookups: 128 to 16 bytes per tick).
     */
    @Overwrite
    public DataHolder getDataHolder(Entity entity) {
        return TaCZScope.resolve(entity);
    }
}
