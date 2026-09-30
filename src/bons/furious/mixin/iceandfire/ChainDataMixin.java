package bons.furious.mixin.iceandfire;

import com.github.alexthe666.iceandfire.entity.props.ChainData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * iceandfire_lazy_reference_lists, chain half (Ice and Fire 2.1.13-beta-5).
 *
 * Every living entity carries a ChainData, and almost none of them is ever chained. initialize() built an ArrayList on
 * each entity's first tick only to drop it again when it stayed empty, and serialize() built a ListTag on every save
 * even when there were no chains to write. Both now allocate only when there is something to keep; a chained entity
 * ends up with the same list and writes the same NBT as before.
 */
@Mixin(value = ChainData.class, remap = false)
public abstract class ChainDataMixin {
    @Shadow
    public List<Entity> chainedTo;
    @Shadow
    private List<Integer> chainedToIds;
    @Shadow
    private List<UUID> chainedToUUIDs;
    @Shadow
    private boolean isInitialized;
    @Shadow
    private boolean triggerClientUpdate;

    /**
     * @author BonsUnleashed
     * @reason Create the UUID list tag only when there are chains to write.
     */
    @Overwrite
    public void serialize(CompoundTag tag) {
        CompoundTag chainedData = new CompoundTag();
        if (this.chainedTo != null) {
            ListTag uuids = new ListTag();
            int[] ids = new int[this.chainedTo.size()];
            for (int i = 0; i < this.chainedTo.size(); i++) {
                Entity entity = this.chainedTo.get(i);
                ids[i] = entity.m_19879_();
                uuids.add(NbtUtils.m_129226_(entity.m_20148_()));
            }
            chainedData.m_128385_("chainedToIds", ids);
            chainedData.m_128365_("chainedToUUIDs", uuids);
        }
        tag.m_128365_("chainedData", chainedData);
    }

    /**
     * @author BonsUnleashed
     * @reason Create the chain list only when a chained entity is actually found (null stays null).
     */
    @Overwrite
    private void initialize(Level level) {
        ArrayList<Entity> entities = null;
        if (this.chainedToUUIDs != null && level instanceof ServerLevel serverLevel) {
            for (UUID uuid : this.chainedToUUIDs) {
                Entity entity = serverLevel.m_8791_(uuid);
                if (entity == null) {
                    continue;
                }
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                entities.add(entity);
            }
            this.triggerClientUpdate = true;
        } else if (this.chainedToIds != null) {
            for (int id : this.chainedToIds) {
                Entity entity;
                if (id == -1 || (entity = level.m_6815_(id)) == null) {
                    continue;
                }
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                entities.add(entity);
            }
        }
        this.chainedTo = entities;
        this.chainedToIds = null;
        this.chainedToUUIDs = null;
        this.isInitialized = true;
    }
}
