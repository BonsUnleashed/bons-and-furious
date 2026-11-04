package bons.furious.mixin.photon_c49;

import bons.furious.patch.photon_c49.PhotonQuadScratch;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * photon_particle_quad_scratch (Photon 1.1.17; client; exact): @Redirect of the four "new Vector3f(float, float, float)" of
 * TileParticle.renderInternal (the corner vectors of the billboard branch's Vector3f[4]; the method has no other such
 * construction) to PhotonQuadScratch, which hands back four reused vectors set to the same components on the render
 * thread and new vectors elsewhere. Photon's own code then transforms them and writes the vertices. Our own logic only.
 */
@Mixin(value = TileParticle.class, remap = false)
public abstract class TileParticleQuadScratchMixin {

    @Redirect(method = "renderInternal(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V", at = @At(value = "NEW", target = "(FFF)Lorg/joml/Vector3f;", ordinal = 0), require = 1, allow = 1, remap = false)
    private Vector3f bons$photonC49Corner0(float x, float y, float z) {
        return PhotonQuadScratch.corner0(x, y, z);
    }

    @Redirect(method = "renderInternal(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V", at = @At(value = "NEW", target = "(FFF)Lorg/joml/Vector3f;", ordinal = 1), require = 1, allow = 1, remap = false)
    private Vector3f bons$photonC49Corner1(float x, float y, float z) {
        return PhotonQuadScratch.corner1(x, y, z);
    }

    @Redirect(method = "renderInternal(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V", at = @At(value = "NEW", target = "(FFF)Lorg/joml/Vector3f;", ordinal = 2), require = 1, allow = 1, remap = false)
    private Vector3f bons$photonC49Corner2(float x, float y, float z) {
        return PhotonQuadScratch.corner2(x, y, z);
    }

    @Redirect(method = "renderInternal(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V", at = @At(value = "NEW", target = "(FFF)Lorg/joml/Vector3f;", ordinal = 3), require = 1, allow = 1, remap = false)
    private Vector3f bons$photonC49Corner3(float x, float y, float z) {
        return PhotonQuadScratch.corner3(x, y, z);
    }
}
