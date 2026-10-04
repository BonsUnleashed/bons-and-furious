package bons.furious.mixin.armorbake_c2;

import bons.furious.patch.armorbake_c2.LayerBake;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_layer_bake_streamless (Minecraft 1.21.1 with NeoForge 21.1.252, client). Mojang member names.
 *
 * PartDefinition.bake(texWidth, texHeight) is replaced by our own loop implementation in LayerBake: the same ModelPart
 * tree, built in the same order, without the two stream pipelines per part (see LayerBake for why it is identical).
 * Overwritten (not injected) because the cost is the pipelines themselves; other mods' RETURN injectors on bake (Entity
 * Model Features 3.3.9 for 1.21 MixinModelPartData: a texture-size hook that reads only the arguments and the returned
 * part) are applied by Mixin to this body as they were to vanilla's. No Minecraft code is carried. Runtime flag: none
 * (switch it off in the config, which takes effect at the next start).
 *
 * Ported to 1.21.1: unchanged - PartDefinition.bake, its fields and constructor are the same source in 1.21.1.
 */
@Mixin(value = PartDefinition.class, remap = false, priority = 900)
public abstract class LayerBakeStreamlessMixin {
    @Shadow
    @Final
    private List<CubeDefinition> cubes;
    @Shadow
    @Final
    private PartPose partPose;
    @Shadow
    @Final
    private Map<String, PartDefinition> children;

    /**
     * @author Bons and Furious (vanilla_layer_bake_streamless)
     * @reason the same tree without two stream pipelines per part
     */
    @Overwrite
    public ModelPart bake(int texWidth, int texHeight) {
        return LayerBake.bake(this.cubes, this.children, this.partPose, texWidth, texHeight);
    }
}
