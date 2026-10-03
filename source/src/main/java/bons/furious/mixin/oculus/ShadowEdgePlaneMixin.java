package bons.furious.mixin.oculus;

import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * oculus_shadow_edge_vectors (Oculus 1.8.0 for Minecraft 1.20.1).
 *
 * addEdgePlane runs for every edge plane each time Oculus builds an advanced shadow frustum. The helper cross(a, b)
 * allocates a new Vector3f for each of the two products ixb and fxi. At that point the truncated plane normals are no
 * longer needed, so the products are computed into them with JOML's cross methods that take a destination; the
 * arithmetic is the same. The rest of the method is Oculus' original code.
 */
@Mixin(value = AdvancedShadowCullingFrustum.class, remap = false)
public abstract class ShadowEdgePlaneMixin {
    @Shadow @Final private Vector3f shadowLightVectorFromOrigin;

    @Shadow
    private Vector3f truncate(Vector4f base) {
        throw new AssertionError();
    }

    @Shadow
    private Vector4f extend(Vector3f base, float w) {
        throw new AssertionError();
    }

    @Shadow
    private float lengthSquared(Vector3f v) {
        throw new AssertionError();
    }

    @Shadow
    private Vector3f cross(Vector3f first, Vector3f second) {
        throw new AssertionError();
    }

    @Shadow
    private void addPlane(float[] plane) {
        throw new AssertionError();
    }

    /**
     * @author BonsUnleashed
     * @reason Compute the two cross products into the plane normals that are no longer used, saving two allocations.
     */
    @Overwrite
    private void addEdgePlane(Vector4f backPlane4, Vector4f frontPlane4) {
        Vector3f backPlaneNormal = truncate(backPlane4);
        Vector3f frontPlaneNormal = truncate(frontPlane4);

        // vector along the intersection of the two planes
        Vector3f intersection = cross(backPlaneNormal, frontPlaneNormal);

        // the edge plane normal is perpendicular to the shadow light vector
        Vector3f edgePlaneNormal = cross(intersection, shadowLightVectorFromOrigin);

        Vector3f point;

        {
            // "Intersection of 2-planes", Graphics Gems 1, page 305. The plane normals are not needed anymore, so they
            // receive the two cross products.
            Vector3f ixb = intersection.cross(backPlaneNormal, backPlaneNormal);
            Vector3f fxi = frontPlaneNormal.cross(intersection);

            ixb.mul(-frontPlane4.w());
            fxi.mul(-backPlane4.w());

            ixb.add(fxi);

            point = ixb;
            point.mul(1.0F / lengthSquared(intersection));
        }

        Vector4f plane;

        {
            // d = -dot(normal, point)
            float d = edgePlaneNormal.dot(point);
            float w = -d;

            plane = extend(edgePlaneNormal, w);
        }

        addPlane(new float[] {plane.x, plane.y, plane.z, plane.w});
    }
}
