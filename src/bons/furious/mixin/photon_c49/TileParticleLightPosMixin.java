package bons.furious.mixin.photon_c49;

import bons.furious.patch.photon_c49.PhotonLightPos;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * photon_particle_light_pos_reuse (Photon 1.1.17; client; exact variant): @Redirect of the one "new BlockPos(int, int, int)"
 * of TileParticle.getLightColor(). The handler receives the three ints Photon computed with its own (int) truncations;
 * when they equal the coordinates of the BlockPos this particle built last, that BlockPos is returned, otherwise a new
 * BlockPos(x, y, z) is built and kept. See PhotonLightPos. Our own logic only.
 */
@Mixin(value = TileParticle.class, remap = false)
public abstract class TileParticleLightPosMixin {
    /** The BlockPos this particle last handed to its emitter (an immutable position). */
    @Unique
    private BlockPos bons$photonC49HeldLightPos;

    @Redirect(method = "getLightColor()I", at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;"),
            require = 1, allow = 1, remap = false)
    private BlockPos bons$photonC49ReuseLightPos(int x, int y, int z) {
        if (!PhotonLightPos.enabled) return new BlockPos(x, y, z);
        BlockPos held = this.bons$photonC49HeldLightPos;
        if (held != null && held.m_123341_() == x && held.m_123342_() == y && held.m_123343_() == z) {
            PhotonLightPos.reused();
            return held;
        }
        BlockPos made = new BlockPos(x, y, z);
        this.bons$photonC49HeldLightPos = made;
        return made;
    }
}
