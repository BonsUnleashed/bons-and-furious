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
 * Bons and Furious switch vanilla_layer_bake_streamless (Minecraft 1.21.1 with NeoForge 21.1.252, client). Our own code;
 * Mojang member names.
 *
 * PartDefinition.bake(texWidth, texHeight) turns a model layer definition into a ModelPart tree; vanilla does it with two
 * stream pipelines for every part. Armour models of many mods (the MCreator template and others) bake their layer on every
 * render call, three times per chestplate.
 *
 * This is our own loop implementation of the same result: the children in the child map's iteration order, each baked
 * (recursively, through PartDefinition.bake) and merged into a new Object2ObjectArrayMap keeping the first part for a
 * name (Collectors.toMap with a map factory accumulates by map.merge(key, value, merge), key read before the value is
 * computed); then the cubes in list order, each baked through CubeDefinition.bake, added to an ImmutableList.Builder
 * (Guava's toImmutableList collector is Collector.of(builder, add, combine, build), so the list class and contents are
 * the same); then the ModelPart constructor, setInitialPose and loadPose. Same objects, same construction order (ModelPart
 * and Cube constructor hooks see the same sequence), same exceptions. Other mods' RETURN hooks on bake are applied by
 * Mixin to this body as before.
 *
 * Runtime flag: none (an @Overwrite of a Minecraft method; switch it off in the config, which takes effect at the next
 * start: the mixin is then not applied at all). No Minecraft code is carried here.
 *
 * Ported to 1.21.1: unchanged code. PartDefinition.bake and CubeDefinition.bake are the same source in 1.21.1; Guava
 * 32.1.2's CollectCollectors.TO_IMMUTABLE_LIST is still Collector.of(ImmutableList::builder, Builder::add,
 * Builder::combine, Builder::build) (31.1 on 1.20.1); fastutil 8.5.12's Object2ObjectArrayMap serves both forms alike.
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
            parts.merge(name, e.getValue().bake(w, h), LayerBake::keepFirst);
        }
        ImmutableList.Builder<ModelPart.Cube> list = ImmutableList.builder();
        for (CubeDefinition cube : cubes) list.add(cube.bake(w, h));
        ModelPart part = new ModelPart(list.build(), parts);
        part.setInitialPose(pose);
        part.loadPose(pose);
        return part;
    }

    private static ModelPart keepFirst(ModelPart first, ModelPart second) {
        return first;
    }
}
