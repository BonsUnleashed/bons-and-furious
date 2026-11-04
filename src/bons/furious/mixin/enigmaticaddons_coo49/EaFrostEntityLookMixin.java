package bons.furious.mixin.enigmaticaddons_coo49;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * enigmaticaddons_idle_frost_skip (both sides): a mixin of priority 999999 on Entity that changes nothing; it adds one
 * unused marker method (so the merged class shows the look ran) and gives the group's plugin (EaPlugin.postApply) a look at
 * Entity after the mixins of other mods were merged, to record which handlers are merged into Entity.canFreeze
 * (m_142079_), the super call of LivingEntity.canFreeze.
 */
@Mixin(value = Entity.class, priority = 999999, remap = false)
public abstract class EaFrostEntityLookMixin {
    @Unique
    private static boolean bons$enigmaticaddonsCoo49EntityLook() {
        return true;
    }
}
