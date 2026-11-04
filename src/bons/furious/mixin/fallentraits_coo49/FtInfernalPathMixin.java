package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtInfernal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * fallentraits_infernal_follow_range_cap (Fallen Traits & Relics 1.0.0; server side; trade-off, off by default):
 * @ModifyVariable at the head of PathNavigation.createPath(targets, regionOffset, offsetUpward, accuracy, followRange)
 * (m_148222_): for a mob whose FOLLOW_RANGE carries Fallen Traits' Infernal modifier, a follow range above 256 becomes 256,
 * so the path search and its region stop at 256 blocks; every other mob and range is untouched (one comparison). What
 * differs: an Infernal mob's path toward a target farther than 256 blocks ends at the closest point reached (FtInfernal).
 */
@Mixin(value = PathNavigation.class, remap = false)
public abstract class FtInfernalPathMixin {
    @Shadow
    @Final
    protected Mob f_26494_;

    @ModifyVariable(method = "m_148222_(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), argsOnly = true, require = 1, allow = 1)
    private float bons$fallentraitsCoo49InfernalPathRange(float followRange) {
        return FtInfernal.clampPath(this.f_26494_, followRange);
    }
}
