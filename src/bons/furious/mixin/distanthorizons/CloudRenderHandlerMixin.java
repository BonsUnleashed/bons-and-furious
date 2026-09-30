package bons.furious.mixin.distanthorizons;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.renderer.CloudRenderHandler;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import com.seibel.distanthorizons.core.util.math.DhVec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_cloud_scalars (Distant Horizons 3.3.2).
 *
 * The per-frame cloud culling test built three temporary vectors for each of the four corners of every cloud tile:
 * a copy of the corner and a copy of the camera position with y set to 0 (for the horizontal distance), and a
 * normalized camera-to-corner vector (for the "behind the camera" test). The same values are now computed on scalars
 * by ac$flatDistance and ac$cornerDot; thresholds, corner order and the near-cloud exemption are unchanged.
 *
 * shouldCloudBeCulled takes the private nested CloudParams type, so it cannot be overwritten from Java. Its only
 * caller, preRender, is redirected to bons$shouldCloudBeCulled, which is the patched method body; the original method
 * stays in the class but is no longer called.
 */
@Mixin(value = CloudRenderHandler.class, remap = false)
public abstract class CloudRenderHandlerMixin {
    @Shadow
    @Final
    private static IMinecraftRenderWrapper MC_RENDER;

    @Shadow
    @Final
    private DhVec3d[] cullingCorners;

    @Redirect(method = "preRender", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/render/renderer/CloudRenderHandler;shouldCloudBeCulled(FFFLcom/seibel/distanthorizons/core/render/renderer/CloudRenderHandler$CloudParams;)Z"))
    private boolean bons$shouldCloudBeCulled(CloudRenderHandler handler, float minPosX, float minPosY, float minPosZ,
                                             @Coerce Object params) {
        CloudParamsAccessor cloudParams = (CloudParamsAccessor) params;
        if (cloudParams.bons$instanceOffsetX() >= -1 && cloudParams.bons$instanceOffsetX() <= 1
                && cloudParams.bons$instanceOffsetZ() >= -1 && cloudParams.bons$instanceOffsetZ() <= 1) {
            return false;
        }
        this.cullingCorners[0].x = minPosX;
        this.cullingCorners[0].y = minPosY;
        this.cullingCorners[0].z = minPosZ;
        this.cullingCorners[1].x = minPosX;
        this.cullingCorners[1].y = minPosY;
        this.cullingCorners[1].z = minPosZ + cloudParams.bons$widthInBlocks();
        this.cullingCorners[2].x = minPosX + cloudParams.bons$widthInBlocks();
        this.cullingCorners[2].y = minPosY;
        this.cullingCorners[2].z = minPosZ;
        this.cullingCorners[3].x = minPosX + cloudParams.bons$widthInBlocks();
        this.cullingCorners[3].y = minPosY;
        this.cullingCorners[3].z = minPosZ + cloudParams.bons$widthInBlocks();
        DhVec3d cameraPos = MC_RENDER.getCameraExactPosition();
        DhVec3f cameraLookAtVector = MC_RENDER.getLookAtVector();
        cameraLookAtVector.normalize();
        double renderDistance = Math.max(Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get(), 256) * 16 * 1.5;
        boolean allOutsideRenderDistance = true;
        boolean allBehindCamera = true;
        for (DhVec3d corner : this.cullingCorners) {
            double cornerDistance = ac$flatDistance(corner, cameraPos);
            if (cornerDistance <= renderDistance) {
                allOutsideRenderDistance = false;
            }
            if (ac$cornerDot(cameraLookAtVector, corner, cameraPos) > 0.0f) {
                allBehindCamera = false;
            }
        }
        return allOutsideRenderDistance || allBehindCamera;
    }

    /** Horizontal distance between two points: the original copied both points and set their y to 0 first. */
    @Unique
    private static double ac$flatDistance(DhVec3d corner, DhVec3d cameraPos) {
        return Math.sqrt(Math.pow(corner.x - cameraPos.x, 2.0) + Math.pow(0.0, 2.0) + Math.pow(corner.z - cameraPos.z, 2.0));
    }

    /**
     * lookAt dot the normalized camera-to-corner direction, on scalars. This is DhVec3f.normalize() and
     * DhVec3f.dotProduct() inlined; a subclass of DhVec3f keeps the original calls on a real vector.
     */
    @Unique
    private static float ac$cornerDot(DhVec3f lookAt, DhVec3d corner, DhVec3d cameraPos) {
        float x = (float) (corner.x - cameraPos.x);
        float y = (float) (corner.y - cameraPos.y);
        float z = (float) (corner.z - cameraPos.z);
        if (lookAt.getClass() != DhVec3f.class) {
            DhVec3f toCorner = new DhVec3f(x, y, z);
            toCorner.normalize();
            return lookAt.dotProduct(toCorner);
        }
        float squaredSum = x * x + y * y + z * z;
        if (!(squaredSum < 1.0E-5)) {
            float inverseLength = MathUtil.fastInvSqrt(squaredSum);
            x *= inverseLength;
            y *= inverseLength;
            z *= inverseLength;
        }
        return lookAt.x * x + lookAt.y * y + lookAt.z * z;
    }
}
