package bons.furious.mixin.cofh;

import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cofh_translucent_renderer_memo, part 1 of 2: the dispatcher's player renderer map (f_114363_; Forge's getSkinMap()
 * wraps it in a new view per call). Priority 100000 puts it after every other mod's mixins on EntityRenderDispatcher, so
 * when it is applied the mixin plugin can see whether any of them changed getRenderer (Guards.untouched).
 */
@Mixin(value = EntityRenderDispatcher.class, remap = false, priority = 100000)
public interface DispatcherRenderersAccessor {
    @Accessor(value = "f_114363_", remap = false)
    Map<String, EntityRenderer<? extends Player>> bons$playerRenderers();
}
