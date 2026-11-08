package bons.furious.mixin.vanilla_lookup_loops;

import bons.furious.patch.vanilla_lookup_loops.ClipBoxes;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_clip_shape_box_memo (Minecraft 1.21.1, both sides; VoxelShape is not patched by NeoForge 21.1.252). @Redirect of
 * the toAabbs call inside VoxelShape.clip: the first clip of a shape of vanilla's own kinds gets the fresh list (as before)
 * and keeps an unmodifiable copy in a field of the shape; later clips of that shape hand the copy to AABB.clip, which only
 * reads it. AABB.clip and its arithmetic are unchanged, so the hit is the same. Other toAabbs callers still get their own
 * fresh list. Only our own logic is carried. See ClipBoxes.
 */
@Mixin(value = VoxelShape.class, remap = false)
public abstract class VoxelShapeClipBoxesMixin {
    /** The box list kept for clip (null until the first clip of a keepable shape). */
    @Unique
    private List<AABB> bons$clipBoxes;

    @Redirect(method = "clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/BlockHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;toAabbs()Ljava/util/List;"))
    private List<AABB> bons$keptBoxes(VoxelShape shape) {
        if (!ClipBoxes.enabled) return shape.toAabbs();
        List<AABB> kept = this.bons$clipBoxes;
        if (kept != null) return ClipBoxes.SHADOW ? ClipBoxes.shadow(shape, kept) : kept;
        List<AABB> fresh = shape.toAabbs();
        if (ClipBoxes.keepable(shape)) this.bons$clipBoxes = ClipBoxes.keep(fresh);
        return fresh;
    }
}
