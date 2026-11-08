package bons.furious.mixin.arsadditions_coo49;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * arsadditions_magelight_far_burst_skip (Minecraft 1.21.1 on NeoForge; client): the switch's probe on LevelRenderer.
 * Priority 2,000,000, so Mixin applies it after every other mod's mixin on the class, and AaPlugin.postApply then sees the
 * particle path (addParticle, addParticleInternal, calculateParticleLevel) as it will run. No method of LevelRenderer is
 * changed; the one @Unique method only keeps this mixin from being empty. No Minecraft code is carried.
 */
@Mixin(value = LevelRenderer.class, priority = 2000000, remap = false)
public abstract class AaLevelRendererProbeMixin {
    @Unique
    private static boolean bons$arsadditionsCoo49RendererProbe() {
        return true;
    }
}
