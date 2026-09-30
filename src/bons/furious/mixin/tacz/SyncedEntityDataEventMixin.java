package bons.furious.mixin.tacz;

import com.tacz.guns.entity.sync.core.AcCollections;
import com.tacz.guns.entity.sync.core.DataEntry;
import com.tacz.guns.entity.sync.core.DataHolder;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import com.tacz.guns.event.SyncedEntityDataEvent;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageUpdateEntityData;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * tacz_sync_collections (TaCZ 1.1.5), part 2 of 2; part 1 is DataHolderMixin.
 *
 * At the end of every server tick TaCZ sends each changed entity's synced data, split into the entries its own player
 * sees and the entries tracking players see. Both splits were stream filters with Collectors.toList(); they are now
 * AcCollections.self and AcCollections.tracking, plain loops that keep the entry order. Gathering, the self send, the
 * tracking send and clean() still happen in the original order, and @SubscribeEvent stays on the method so Forge keeps
 * registering it.
 */
@Mixin(value = SyncedEntityDataEvent.class, remap = false)
public abstract class SyncedEntityDataEventMixin {
    /**
     * @author BonsUnleashed
     * @reason Route the dirty entries without stream collectors (688 to 160 bytes per entity).
     */
    @Overwrite
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        SyncedEntityData instance = SyncedEntityData.instance();
        if (event.side != LogicalSide.SERVER) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!instance.isDirty()) {
            return;
        }
        List<Entity> dirtyEntities = instance.getDirtyEntities();
        if (dirtyEntities.isEmpty()) {
            instance.setDirty(false);
            return;
        }
        for (Entity entity : dirtyEntities) {
            DataHolder holder = instance.getDataHolder(entity);
            if (holder == null || !holder.isDirty()) {
                continue;
            }
            List<DataEntry<?, ?>> entries = holder.gatherDirty();
            if (entries.isEmpty()) {
                continue;
            }
            List<DataEntry<?, ?>> selfEntries = AcCollections.self(entries);
            if (!selfEntries.isEmpty() && entity instanceof ServerPlayer) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) entity),
                        new ServerMessageUpdateEntityData(entity.m_19879_(), selfEntries));
            }
            List<DataEntry<?, ?>> trackingEntries = AcCollections.tracking(entries);
            if (!trackingEntries.isEmpty()) {
                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                        new ServerMessageUpdateEntityData(entity.m_19879_(), trackingEntries));
            }
            holder.clean();
        }
        dirtyEntities.clear();
        instance.setDirty(false);
    }
}
