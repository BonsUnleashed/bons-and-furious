package bons.furious.patch.arsnouveau_mop49;

import com.hollingsworth.arsnouveau.common.light.LightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch arsnouveau_dynamic_light_hook_lean (Ars Nouveau 5.13.2 for NeoForge 1.21.1, client; since 1.0.49).
 * Ars Nouveau is GPL-3.0; {@link #light} makes the calls its light.LevelRendererMixin handler makes.
 *
 * Ars Nouveau's dynamic light hooks LevelRenderer.getLightColor(BlockAndTintGetter, BlockState, BlockPos), the packed
 * light of a block position that chunk meshing (Sodium-style renderers ask it for every light sample of a section they
 * rebuild), entities, particles and block entity renderers read, with a cancellable inject at the method's last return
 * (handler ars_nouveau$onGetLightmapCoordinates): every call built a CallbackInfoReturnable holding the boxed light value,
 * and with Ars Nouveau's dynamic lights turned off (its client config "lightsEnabled", off in many packs that use another
 * dynamic light mod) the handler then only read that setting. With this switch Ars Nouveau's handler is left out and a
 * MixinExtras @ModifyReturnValue at the same place makes the same calls in the same order on the plain int: the same light
 * on every call, without the callback object.
 */
public final class DynamicLightHook {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    static {
        LOGGER.info("Bons and Furious: arsnouveau_dynamic_light_hook_lean: Ars Nouveau's dynamic light reads the block light without a callback object");
    }

    private DynamicLightHook() {
    }

    /**
     * The light getLightColor returns, after Ars Nouveau's dynamic light: when its dynamic lights are on and the block at
     * the position is not a solid render block, LightManager.getLightmapWithDynamicLight(pos, light); otherwise the light
     * unchanged. The same calls in the same order as Ars Nouveau's handler (its setting first, then the block state read
     * from the level, not the state argument).
     */
    public static int light(int light, BlockAndTintGetter level, BlockPos pos) {
        if (!LightManager.shouldUpdateDynamicLight()) return light;
        if (!level.getBlockState(pos).isSolidRender(level, pos)) return LightManager.getLightmapWithDynamicLight(pos, light);
        return light;
    }
}
