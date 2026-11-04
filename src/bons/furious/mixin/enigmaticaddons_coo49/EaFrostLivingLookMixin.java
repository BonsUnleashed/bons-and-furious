package bons.furious.mixin.enigmaticaddons_coo49;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * enigmaticaddons_idle_frost_skip (both sides): a mixin of priority 999999 on LivingEntity that changes nothing; it adds
 * one unused marker method (so the merged class shows the look ran) and gives the group's plugin (EaPlugin.postApply) a
 * look at LivingEntity after the mixins of other mods were merged, to record which handlers are merged into
 * LivingEntity.canFreeze (m_142079_).
 */
@Mixin(value = LivingEntity.class, priority = 999999, remap = false)
public abstract class EaFrostLivingLookMixin {
    @Unique
    private static boolean bons$enigmaticaddonsCoo49LivingLook() {
        return true;
    }
}
