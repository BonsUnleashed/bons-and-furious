package bons.furious.mixin.distanthorizons;

import bons.furious.patch.distanthorizons.CloudPass;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.render.renderer.CloudRenderHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.awt.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_cloud_pass_invariants (Distant Horizons 3.3.2), part 1 of 2: the two per-group lookups in
 * CloudRenderHandler.preRender go through CloudPass, which answers repeats inside one render pass. The existing
 * distanthorizons_cloud_scalars switch redirects a different call in the same method (the culling test).
 */
@Mixin(value = CloudRenderHandler.class, remap = false)
public abstract class CloudPassInvariantsMixin {
    @Redirect(method = "preRender", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/level/IDhClientLevel;getClientLevelWrapper()Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;"))
    private IClientLevelWrapper bons$passWrapper(IDhClientLevel level) {
        return CloudPass.clientLevelWrapper(level);
    }

    @Redirect(method = "preRender", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;getCloudColor(F)Ljava/awt/Color;"))
    private Color bons$passColor(IClientLevelWrapper wrapper, float partialTicks) {
        return CloudPass.cloudColor(wrapper, partialTicks);
    }
}
