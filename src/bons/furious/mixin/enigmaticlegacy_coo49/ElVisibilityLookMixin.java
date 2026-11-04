package bons.furious.mixin.enigmaticlegacy_coo49;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * enigmaticlegacy_cursed_ring_anger_candidates_exact (server side): a mixin of priority 999999 on LivingEntity that
 * changes nothing; it adds one unused marker method (so the merged class shows the look ran) and gives the group's plugin
 * (ElPlugin.postApply) a look at LivingEntity after the mixins of other mods were merged, to record whether
 * getVisibilityPercent (m_20968_) is still the plain Minecraft body.
 */
@Mixin(value = LivingEntity.class, priority = 999999, remap = false)
public abstract class ElVisibilityLookMixin {
    @Unique
    private static boolean bons$enigmaticlegacyCoo49VisibilityLook() {
        return true;
    }
}
