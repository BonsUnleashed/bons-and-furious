package bons.furious.mixin.geckolib;

import bons.furious.patch.geckolib.BoneQueueReuse;
import java.util.Collection;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.keyframe.BoneAnimationQueue;

/**
 * geckolib_bone_queue_reuse (GeckoLib 4.8.4, both sides).
 *
 * createInitialQueues runs for every playing controller every rendered frame and allocated one BoneAnimationQueue record
 * and nine AnimationPointQueue lists per bone. The map now receives the same clear() and put() calls in the same order,
 * with the record of the same bone object taken over (its queues emptied) instead of a new one; see BoneQueueReuse for
 * why the map's contents, layout and iteration order are what GeckoLib produces. With the runtime flag off the body is
 * GeckoLib's own (MIT).
 */
@Mixin(value = AnimationController.class, remap = false)
public abstract class AnimationControllerQueueMixin {
    @Shadow
    @Final
    protected Map<String, BoneAnimationQueue> boneAnimationQueues;

    @Unique
    private BoneQueueReuse.Scratch bons$queueScratch;

    /**
     * @author BonsUnleashed
     * @reason Take over the previous frame's queue record of each bone instead of allocating ten objects per bone.
     */
    @Overwrite
    private void createInitialQueues(Collection<CoreGeoBone> modelRendererList) {
        if (BoneQueueReuse.enabled) {
            BoneQueueReuse.Scratch scratch = this.bons$queueScratch;
            if (scratch == null) {
                scratch = this.bons$queueScratch = new BoneQueueReuse.Scratch();
            }
            BoneQueueReuse.rebuild(this.boneAnimationQueues, modelRendererList, scratch);
            return;
        }
        this.boneAnimationQueues.clear();
        for (CoreGeoBone modelRenderer : modelRendererList) {
            this.boneAnimationQueues.put(modelRenderer.getName(), new BoneAnimationQueue(modelRenderer));
        }
    }
}
