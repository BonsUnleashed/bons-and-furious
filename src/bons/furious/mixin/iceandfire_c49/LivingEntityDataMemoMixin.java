package bons.furious.mixin.iceandfire_c49;

import bons.furious.patch.iceandfire_c49.EntityDataMemo;
import bons.furious.patch.iceandfire_c49.EntityDataMemoHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * iceandfire_entity_data_memo (Ice and Fire 2.1.13-1.20.1-beta-5; both sides; since 1.0.49): one field on LivingEntity
 * for the memo of Ice and Fire's entity data lookup (see bons.furious.patch.iceandfire_c49.EntityDataMemo). No method of
 * LivingEntity changes; the field is read and written only by EntityDataMemo. Applied only with the switch (same key).
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityDataMemoMixin implements EntityDataMemoHolder {
    @Unique
    private EntityDataMemo.Memo bons$iafDataMemoField;

    @Override
    public EntityDataMemo.Memo bons$iafDataMemo() {
        return this.bons$iafDataMemoField;
    }

    @Override
    public void bons$iafDataMemo(EntityDataMemo.Memo memo) {
        this.bons$iafDataMemoField = memo;
    }
}
