package bons.furious.mixin.photon_c49;

import bons.furious.patch.photon_c49.PhotonTrailScratch;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * photon_trail_ribbon_scratch (Photon 1.1.17; client; exact): @Redirect of three "new Vector3f(Vector3fc)" of
 * TrailParticle$TailArray.renderInternal. Mixin counts the ordinal over every "new Vector3f" of the method (the segment's
 * three new Vector3f(x, y, z) come first): ordinal 3 = the segment's direction new Vector3f(next) (crossed and normalized
 * in place, it is the segment's normal and the next segment's lastNormal), ordinal 6 = the segment's upper edge
 * new Vector3f(tailPos) (its lastUp), ordinal 10 = the head's upper edge new Vector3f(head) (lastUp after the loop). PhotonTrailScratch hands back alternating reused vectors with the same
 * components on the render thread, new vectors elsewhere; every other vector of the method is left as it is (the JIT
 * already removes them). Our own logic only.
 */
@Mixin(targets = "com.lowdragmc.photon.client.gameobject.particle.TrailParticle$TailArray", remap = false)
public abstract class TrailTailArrayScratchMixin {

    @Redirect(method = "renderInternal(Lcom/mojang/blaze3d/vertex/VertexConsumer;FLorg/joml/Vector3f;Lorg/joml/Vector4f;I)V", at = @At(value = "NEW", target = "(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;", ordinal = 3), require = 1, allow = 1, remap = false)
    private Vector3f bons$photonC49SegmentNormal(Vector3fc next) {
        return PhotonTrailScratch.normal(next);
    }

    @Redirect(method = "renderInternal(Lcom/mojang/blaze3d/vertex/VertexConsumer;FLorg/joml/Vector3f;Lorg/joml/Vector4f;I)V", at = @At(value = "NEW", target = "(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;", ordinal = 6), require = 1, allow = 1, remap = false)
    private Vector3f bons$photonC49SegmentUp(Vector3fc tailPos) {
        return PhotonTrailScratch.up(tailPos);
    }

    @Redirect(method = "renderInternal(Lcom/mojang/blaze3d/vertex/VertexConsumer;FLorg/joml/Vector3f;Lorg/joml/Vector4f;I)V", at = @At(value = "NEW", target = "(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;", ordinal = 10), require = 1, allow = 1, remap = false)
    private Vector3f bons$photonC49HeadUp(Vector3fc head) {
        return PhotonTrailScratch.up(head);
    }
}
