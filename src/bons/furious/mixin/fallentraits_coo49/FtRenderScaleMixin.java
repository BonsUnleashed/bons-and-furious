package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtScale;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * fallentraits_render_scale_frame_memo (Fallen Traits & Relics 1.0.0; client side; trade-off, off by default): MixinExtras
 * @WrapMethod on SizeTrait.getScale(entity): while the world renders on this thread, a living entity's scale is taken once
 * per entity tick and the kept value answered until its next tick; every other call (ticks, other threads, the inventory
 * preview) runs as shipped (FtScale). What differs: a size change shows on screen up to one tick late.
 */
@Mixin(targets = "net.kayn.fallen_traits.content.traits.legendary.SizeTrait", remap = false)
public abstract class FtRenderScaleMixin {
    @WrapMethod(method = "getScale(Lnet/minecraft/world/entity/Entity;)F")
    private static float bons$fallentraitsCoo49RenderScale(Entity entity, Operation<Float> original) {
        return FtScale.scale(entity, original);
    }
}
