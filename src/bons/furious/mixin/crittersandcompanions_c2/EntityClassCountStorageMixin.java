package bons.furious.mixin.crittersandcompanions_c2;

import bons.furious.patch.crittersandcompanions_c2.EntityClassCounts;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_entity_class_count_layer (Minecraft 1.20.1, both sides; SRG client jar 1.20.1-20230612.114412, not patched by
 * Forge 47.4.16), part 1 of 4: every EntitySectionStorage gets the per-class counts, the thread that created it (the
 * level's own thread: ServerLevel and ClientLevel build their section managers in their constructors) and a "doubtful"
 * flag; createSection (the only place sections are made) tells each new section which storage it belongs to.
 * Nothing the game reads changes: the added fields are only read by EntityClassCounts.
 */
@Mixin(value = EntitySectionStorage.class, remap = false)
public abstract class EntityClassCountStorageMixin implements EntityClassCounts.Storage {
    @Shadow
    @Final
    private Long2ObjectMap<EntitySection<?>> f_156852_;
    @Unique
    private int[] bons$classCounts;
    @Unique
    private Thread bons$classCountOwner;
    @Unique
    private volatile boolean bons$classCountsDoubtful;

    /**
     * Set up at the end of the constructor (field initialisers in a mixin are not reliably merged: Mixin 0.8.5 copied only
     * one of two). No section can exist before the constructor returns, so nothing is counted earlier.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$classCountsCreated(CallbackInfo ci) {
        this.bons$classCounts = new int[EntityClassCounts.TRACKED_COUNT];
        this.bons$classCountOwner = Thread.currentThread();
        EntityClassCounts.storageCreated();
    }

    @ModifyReturnValue(method = "m_156901_", at = @At("RETURN"))
    private EntitySection<?> bons$classCountSection(EntitySection<?> section) {
        ((EntityClassCounts.Section) section).bons$classCountStorage(this);
        return section;
    }

    @Override
    public int[] bons$classCounts() {
        return this.bons$classCounts;
    }

    @Override
    public Thread bons$classCountOwner() {
        return this.bons$classCountOwner;
    }

    @Override
    public boolean bons$classCountsDoubtful() {
        return this.bons$classCountsDoubtful;
    }

    @Override
    public void bons$classCountsDoubtful(boolean doubtful) {
        this.bons$classCountsDoubtful = doubtful;
    }

    @Override
    public int bons$classCountTruth(int index) {
        int n = 0;
        for (EntitySection<?> section : this.f_156852_.values()) n += ((EntityClassCounts.Section) section).bons$trackedTruth(index);
        return n;
    }
}
