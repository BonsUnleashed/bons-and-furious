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
 * vanilla_clip_shape_box_memo (Minecraft 1.20.1, SRG client jar 1.20.1-20230612.114412, not patched by Forge 47.4.28; both
 * sides). @Redirect of the toAabbs (m_83299_) call inside VoxelShape.clip (m_83220_): the first clip of a shape of
 * vanilla's own kinds gets the fresh list (as before) and keeps an unmodifiable copy in a field of the shape; later clips
 * of that shape hand the copy to AABB.clip (m_82342_), which only reads it. AABB.clip and its arithmetic are unchanged, so
 * the hit is the same. Other toAabbs callers still get their own fresh list. Only our own logic is carried. See ClipBoxes.
 */
@Mixin(value = VoxelShape.class, remap = false)
public abstract class VoxelShapeClipBoxesMixin {
    /** The box list kept for clip (null until the first clip of a keepable shape). */
    @Unique
    private List<AABB> bons$clipBoxes;

    @Redirect(method = "m_83220_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;m_83299_()Ljava/util/List;"))
    private List<AABB> bons$keptBoxes(VoxelShape shape) {
        if (!ClipBoxes.enabled) return shape.m_83299_();
        List<AABB> kept = this.bons$clipBoxes;
        if (kept != null) return ClipBoxes.SHADOW ? ClipBoxes.shadow(shape, kept) : kept;
        List<AABB> fresh = shape.m_83299_();
        if (ClipBoxes.keepable(shape)) this.bons$clipBoxes = ClipBoxes.keep(fresh);
        return fresh;
    }
}
