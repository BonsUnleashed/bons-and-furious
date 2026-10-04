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
 * vanilla_layer_bake_streamless (Minecraft 1.20.1 client).
 *
 * PartDefinition.bake(texWidth, texHeight) is replaced by our own loop implementation in LayerBake: the same ModelPart
 * tree, built in the same order, without the two stream pipelines per part (see LayerBake for why it is identical).
 * Overwritten (not injected) because the cost is the pipelines themselves; other mods' RETURN injectors on bake (Entity
 * Model Features, Supplementaries: texture-size hooks that read only the arguments and the returned part) are applied by
 * Mixin to this body as they were to vanilla's. No Minecraft code is carried. Runtime flag: none (switch it off in the
 * config, which takes effect at the next start).
 */
@Mixin(value = PartDefinition.class, remap = false, priority = 900)
public abstract class LayerBakeStreamlessMixin {
    @Shadow
    @Final
    private List<CubeDefinition> f_171577_;      // cubes
    @Shadow
    @Final
    private PartPose f_171578_;                  // partPose
    @Shadow
    @Final
    private Map<String, PartDefinition> f_171579_;  // children

    /**
     * @author Bons and Furious (vanilla_layer_bake_streamless)
     * @reason the same tree without two stream pipelines per part
     */
    @Overwrite
    public ModelPart m_171583_(int texWidth, int texHeight) {
        return LayerBake.bake(this.f_171577_, this.f_171579_, this.f_171578_, texWidth, texHeight);
    }
}
