package bons.furious.mixin.dungeonsdelight_coo49;

import bons.furious.patch.dungeonsdelight_coo49.TenacityIntervalHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * dungeonsdelight_tenacity_per_player_interval: one int on LivingEntity holding the Tenacity interval Dungeons Delight
 * computed for this entity (0 = none). Only TenacityIntervalRecordMixin writes it and MobEffectInstanceTenacityMixin reads
 * it; LivingEntity behaves exactly as before.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityTenacityIntervalMixin implements TenacityIntervalHolder {
    @Unique
    private int bons$ownTenacityInterval;

    @Override
    public int bons$tenacityInterval() {
        return this.bons$ownTenacityInterval;
    }

    @Override
    public void bons$setTenacityInterval(int interval) {
        this.bons$ownTenacityInterval = interval;
    }
}
