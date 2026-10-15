package bons.furious.patch.jei_deferral;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * jei_join_recipe_deferral: starts the deferred recipe job once JEI is running, and stops it when JEI stops or restarts.
 * JEI finds this plugin in every pack that has JEI; with the switch off (or stepped aside) no lane was ever scheduled, so
 * both callbacks find nothing to do.
 */
@JeiPlugin
public final class JeiDeferralPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation("bons_and_furious", "jei_deferral");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        Deferral.runtimeAvailable();
    }

    @Override
    public void onRuntimeUnavailable() {
        Deferral.runtimeUnavailable();
    }
}
