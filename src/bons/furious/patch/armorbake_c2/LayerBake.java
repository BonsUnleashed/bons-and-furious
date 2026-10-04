package bons.furious.patch.armorbake_c2;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_layer_bake_streamless (Minecraft 1.20.1 client). Our own code; SRG member names.
 *
 * PartDefinition.bake(texWidth, texHeight) turns a model layer definition into a ModelPart tree; vanilla does it with two
 * stream pipelines for every part. Armour models of many mods (the MCreator template of Clothing of the Lowlands,
 * Terramity, Aquamirae, ...) bake their layer on every render call, three times per chestplate.
 *
 * This is our own loop implementation of the same result: the children in the child map's iteration order, each baked
 * (recursively, through PartDefinition.bake) and merged into a new Object2ObjectArrayMap keeping the first part for a
 * name; then the cubes in list order, each baked through CubeDefinition.bake, added to an ImmutableList.Builder (Guava's
 * toImmutableList collector builds its list the same way, so the list class and contents are the same); then the
 * ModelPart constructor, setInitialPose and loadPose. Same objects, same construction order (ModelPart and Cube
 * constructor hooks of Embeddium, Oculus and EMF see the same sequence), same exceptions. Other mods' RETURN hooks on
 * bake (EMF, Supplementaries) are applied by Mixin to this body as before.
 *
 * Runtime flag: none (an @Overwrite of a Minecraft method; switch it off in the config, which takes effect at the next
 * start: the mixin is then not applied at all). No Minecraft code is carried here.
 */
public final class LayerBake {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean announced;

    private LayerBake() {
    }

    /** The bake of a part definition with the given cubes, children and pose. */
    public static ModelPart bake(List<CubeDefinition> cubes, Map<String, PartDefinition> children, PartPose pose, int w, int h) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_layer_bake_streamless: model layers are baked with plain loops");
        }
        Object2ObjectArrayMap<String, ModelPart> parts = new Object2ObjectArrayMap<>();
        for (Map.Entry<String, PartDefinition> e : children.entrySet()) {
            String name = e.getKey();
            parts.merge(name, e.getValue().m_171583_(w, h), LayerBake::keepFirst);
        }
        ImmutableList.Builder<ModelPart.Cube> list = ImmutableList.builder();
        for (CubeDefinition cube : cubes) list.add(cube.m_171455_(w, h));
        ModelPart part = new ModelPart(list.build(), parts);
        part.m_233560_(pose);
        part.m_171322_(pose);
        return part;
    }

    private static ModelPart keepFirst(ModelPart first, ModelPart second) {
        return first;
    }
}
