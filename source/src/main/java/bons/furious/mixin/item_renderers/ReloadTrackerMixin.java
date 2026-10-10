package bons.furious.mixin.item_renderers;

import bons.furious.patch.item_renderers.ItemRendererReuse;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * item_renderer_reload_tracker (Minecraft 1.21.1 on NeoForge 21.1, client): ReloadableResourceManager.createReload, at
 * RETURN. Every client resource reload (the start-up one in Minecraft's constructor and each F3+T / pack change in
 * reloadResourcePacks) goes through it; ItemRendererReuse counts it as in flight until its done() future completes, and
 * drops every item renderer memo at both ends. The ReloadInstance is returned unchanged. The *_item_renderer_reuse
 * switches only reuse renderers after this has seen the start-up reload, so without it they run the original code.
 *
 * Ported to 1.21.1: no change; createReload(Executor, Executor, CompletableFuture, List) is the same method (Mojang names,
 * same body) and both client reload paths still call it.
 */
@Mixin(value = ReloadableResourceManager.class, remap = false)
public abstract class ReloadTrackerMixin {
    @ModifyReturnValue(method = "createReload", at = @At("RETURN"))
    private ReloadInstance bons$trackReload(ReloadInstance reload) {
        ItemRendererReuse.reloadStarted(reload);
        return reload;
    }
}
