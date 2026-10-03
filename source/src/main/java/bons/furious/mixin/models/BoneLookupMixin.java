package bons.furious.mixin.models;

import bons.furious.patch.models.BoneSearch;
import java.util.Spliterator;
import java.util.stream.Stream;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_model_bone_lookup (Minecraft 1.20.1 client models).
 *
 * Keyframe animations look up every animated bone every frame through getAnyDescendantWithName, which streams the
 * whole part tree (nested Stream.concat and flatMap) to find the first part that has a child with that name. That
 * stream is replaced by a stream of just that part, found by a plain depth-first walk in the same order: the part
 * itself, then its children as children.values().spliterator() hands them over (the stream's own source). The rest of
 * the method (filter, findFirst, map to getChild) runs unchanged on it. A part class that overrides getAllParts or
 * hasChild, anywhere in the walk, sends the lookup back to the original stream. Minecraft's code is not carried here:
 * only the stream source is redirected.
 */
@Mixin(value = HierarchicalModel.class, remap = false)
public abstract class BoneLookupMixin {
    @Redirect(method = "getAnyDescendantWithName", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;getAllParts()Ljava/util/stream/Stream;"))
    private Stream<ModelPart> bons$partsWithBone(ModelPart root, String name) {
        BoneSearch search = new BoneSearch();
        ModelPart parent = bons$parentOf(root, name, search);
        if (search.fallback) {
            return root.getAllParts();
        }
        return parent == null ? Stream.empty() : Stream.of(parent);
    }

    /** The first part in getAllParts order that has a child called {@code name}, or null. */
    @Unique
    private static ModelPart bons$parentOf(ModelPart part, String name, BoneSearch search) {
        if (!BoneSearch.plainTraversal(part.getClass())) {   // a null child throws here, as the stream's flatMap would
            search.fallback = true;
            return null;
        }
        if (part.hasChild(name)) {
            return part;
        }
        Spliterator<ModelPart> children = ((ModelPartChildrenAccessor) (Object) part).bons$children().values().spliterator();
        while (children.tryAdvance(search)) {
            ModelPart found = bons$parentOf(search.item, name, search);
            if (found != null || search.fallback) {
                return found;
            }
        }
        return null;
    }
}
