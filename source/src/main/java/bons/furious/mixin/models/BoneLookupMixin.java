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
 *
 * Priority 1001 and require = 0 (since 1.0.30): a mod that replaces this method
 * with an @Overwrite at the default 1000 (Embeddium 1.0.15's remove_streams HierarchicalModelMixin on 1.21.1) would make
 * Mixin refuse this redirect at equal priority and the class fail to load. Above 1000 the redirect is let in, finds no
 * stream in that body and stands down (one log line from PureMixinPlugin.postApply). Guards also yields this switch
 * whenever Embeddium is installed, so this is the second line of defence on 1.21.1.
 */
@Mixin(value = HierarchicalModel.class, remap = false, priority = 1001)
public abstract class BoneLookupMixin {
    @Redirect(method = "getAnyDescendantWithName", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;getAllParts()Ljava/util/stream/Stream;"), require = 0)
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
