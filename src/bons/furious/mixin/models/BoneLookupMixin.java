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
 * Priority 1001 and require = 0 (since 1.0.30): Embeddium replaces this method with its own stream-free lookup
 * (remove_streams HierarchicalModelMixin, an @Overwrite at 1000) unless Oculus or the user turns that off. Mixin
 * refuses an injector into an overwritten method at equal priority, so Embeddium without Oculus crashed the game at
 * start. Above 1000 the redirect is allowed in, finds no stream in Embeddium's body and stands down (one log line from
 * PureMixinPlugin.postApply): exactly one copy of the change runs either way.
 */
@Mixin(value = HierarchicalModel.class, remap = false, priority = 1001)
public abstract class BoneLookupMixin {
    @Redirect(method = "m_233393_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;m_171331_()Ljava/util/stream/Stream;"), require = 0)
    private Stream<ModelPart> bons$partsWithBone(ModelPart root, String name) {
        BoneSearch search = new BoneSearch();
        ModelPart parent = bons$parentOf(root, name, search);
        if (search.fallback) {
            return root.m_171331_();
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
        if (part.m_233562_(name)) {
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
