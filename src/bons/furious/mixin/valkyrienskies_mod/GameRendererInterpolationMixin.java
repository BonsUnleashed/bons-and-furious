package bons.furious.mixin.valkyrienskies_mod;

import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.util.AcVsSweep5;
import org.valkyrienskies.mod.common.util.EntityDragger;

/**
 * valkyrien_render_interpolation (Valkyrien Skies 2.4.11), client only.
 *
 * VS's client.renderer.MixinGameRenderer.preRender runs every frame and, for every entity of the client level, looks
 * up the ship it is mounted on and the ship it last stood on to interpolate its render position. An entity without a
 * vehicle that never stood on a ship has neither (AcVsSweep5.skipRenderInterpolation), so it is now skipped like an
 * entity that is not draggable. A MixinSquared redirect of the loop's EntityDragger.isDraggable test in VS's handler.
 */
@Mixin(value = GameRenderer.class, priority = 1500, remap = false)
public abstract class GameRendererInterpolationMixin {
    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.client.renderer.MixinGameRenderer", name = "preRender")
    @Redirect(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lorg/valkyrienskies/mod/common/util/EntityDragger;isDraggable(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean bons$interpolatedAndDraggable(Entity entity) {
        return !AcVsSweep5.skipRenderInterpolation(entity) && EntityDragger.isDraggable(entity);
    }
}
