package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.ClientLeaks;
import com.github.L_Ender.cataclysm.client.render.entity.Boltstrike_Renderer;
import com.github.L_Ender.cataclysm.client.render.entity.Death_Laser_beam_Renderer;
import com.github.L_Ender.cataclysm.client.render.layer.Maledictus_Cicle_Layer;
import com.github.L_Ender.cataclysm.client.render.layer.Scylla_Anchor_Layer;
import com.github.L_Ender.cataclysm.client.render.layer.Scylla_Eye_Spark_Layer;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cataclysm_client_leaks (five lightning holders, L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1
 * tested build L_Ender's Cataclysm 1.21.1-3.33; client). Fix. Each holder's constructor hands its Map<UUID, LightningRender>
 * to ClientLeaks (held weakly), which drops the entry of an entity that leaves the client level and clears the map when
 * the client level unloads.
 *
 * Ported to 1.21.1: the same five classes hold the same private lightningRenderMap in 3.33 (constructors, getLightingRender
 * and the never-reached removal in render unchanged in shape); nothing changed.
 */
@Mixin(value = {Boltstrike_Renderer.class, Death_Laser_beam_Renderer.class, Maledictus_Cicle_Layer.class, Scylla_Anchor_Layer.class,
        Scylla_Eye_Spark_Layer.class}, remap = false)
public abstract class LightningRenderMapsMixin {
    @Shadow(remap = false)
    private Map<UUID, Object> lightningRenderMap;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bons$trackLightningMap(CallbackInfo ci) {
        ClientLeaks.track(this.lightningRenderMap);
    }
}
