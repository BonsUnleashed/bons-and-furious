package bons.furious.mixin.adamsarsplus_coo49;

import bons.furious.patch.adamsarsplus_coo49.AapAuraClock;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * adamsarsplus_flame_aura_interval (trade-off, off by default; Minecraft 1.21.1 LivingEntity on NeoForge, no code carried):
 * one int per living entity, the tick count from which its next Flame Deity aura search may run (AapEffects.skipAura). No
 * method of LivingEntity is changed; the members carry the group's name.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class AapAuraClockMixin implements AapAuraClock {
    @Unique
    private int bons$adamsarsplusAuraNextSearch;

    @Override
    public int bons$adamsarsplusAuraNext() {
        return this.bons$adamsarsplusAuraNextSearch;
    }

    @Override
    public void bons$adamsarsplusSetAuraNext(int tick) {
        this.bons$adamsarsplusAuraNextSearch = tick;
    }
}
