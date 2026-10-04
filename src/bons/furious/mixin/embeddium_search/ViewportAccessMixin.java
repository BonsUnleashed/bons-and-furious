package bons.furious.mixin.embeddium_search;

import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.render.viewport.frustum.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * embeddium_search_replay (Embeddium 0.3.31+mc1.20.1; client only): read access to the Viewport's frustum, so the replay
 * can tell which kind of search it is (main view or Oculus's shadow pass) and whether that frustum's test is a tested,
 * pure implementation (SearchReplay.frustumOk). Read only; nothing in Viewport changes.
 */
@Mixin(value = Viewport.class, remap = false)
public interface ViewportAccessMixin {
    @Accessor("frustum")
    Frustum bons$frustum();
}
