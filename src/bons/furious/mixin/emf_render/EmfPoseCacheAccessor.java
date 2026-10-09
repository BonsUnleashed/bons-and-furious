package bons.furious.mixin.emf_render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Deque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * emf_arm_walk_gate (Embeddium 0.3.31 on Minecraft 1.20.1, client only): read access to PoseStack's pose deque (f_85834_)
 * and to Embeddium's pose-cache depth (cacheEnabled, added by Embeddium's core MatrixStackMixin at priority 900; this
 * accessor's priority 1100 makes Mixin apply it afterwards). While the depth is above 0, Embeddium's push takes a cached
 * Pose object and its pop returns it, so a skipped push/pop pair would leave a different object in that cache: the gate
 * only skips at depth 0.
 */
@Mixin(value = PoseStack.class, priority = 1100, remap = false)
public interface EmfPoseCacheAccessor {
    @Accessor(value = "cacheEnabled", remap = false)
    int bons$poseCacheDepth();

    @Accessor(value = "f_85834_", remap = false)
    Deque<PoseStack.Pose> bons$poseDeque();
}
