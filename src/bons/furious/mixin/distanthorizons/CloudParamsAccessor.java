package bons.furious.mixin.distanthorizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * distanthorizons_cloud_scalars (Distant Horizons 3.3.2).
 *
 * CloudRenderHandler.CloudParams is a private nested class, so CloudRenderHandlerMixin cannot name it; it reads the
 * three fields the cloud culling test needs through this accessor instead.
 */
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.CloudRenderHandler$CloudParams", remap = false)
public interface CloudParamsAccessor {
    @Accessor("instanceOffsetX")
    int bons$instanceOffsetX();

    @Accessor("instanceOffsetZ")
    int bons$instanceOffsetZ();

    @Accessor("widthInBlocks")
    int bons$widthInBlocks();
}
