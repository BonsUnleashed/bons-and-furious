package bons.furious.mixin.arsadditions_coo49;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * arsadditions_magelight_far_burst_skip (Minecraft 1.20.1 on Forge 47.4.28; client): the switch's probe on ClientLevel.
 * Priority 2,000,000, so Mixin applies it after every other mod's mixin on the class, and AaPlugin.postApply then sees
 * addParticle as it will run. No method of ClientLevel is changed; the one @Unique method only keeps this mixin from being
 * empty. No Minecraft code is carried.
 */
@Mixin(value = ClientLevel.class, priority = 2000000, remap = false)
public abstract class AaClientLevelProbeMixin {
    @Unique
    private static boolean bons$arsadditionsCoo49LevelProbe() {
        return true;
    }
}
