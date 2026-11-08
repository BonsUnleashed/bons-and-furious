package bons.furious.mixin.xaerominimap_c49;

import bons.furious.patch.xaerominimap_c49.EmptyElementSkip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.IdentityHashMap;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.element.render.MinimapElementRendererHandler;

/**
 * xaerominimap_empty_element_renderer_skip (Xaero's Minimap 26.6.0 for NeoForge 1.21.1, client, since 1.0.49; change, off by
 * default). MixinExtras @WrapOperation on MinimapElementRendererHandler.render's renderForRenderer call (counts what each of
 * Xaero's own renderers drew on a client tick's first frame) and on renderForRenderer's provider begin / hasNext / end (left
 * out on the tick's other frames for a renderer that drew nothing; its preRender and postRender still run). See
 * EmptyElementSkip. No Minimap code is carried (All Rights Reserved).
 */
@Mixin(value = MinimapElementRendererHandler.class, remap = false)
public abstract class MmElementSkipMixin {
    @Unique
    private IdentityHashMap<Object, long[]> bons$xaerominimapC49Seen;

    @WrapOperation(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/phys/Vec3;FLcom/mojang/blaze3d/pipeline/RenderTarget;DLnet/minecraft/resources/ResourceKey;)V",
            require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lxaero/hud/minimap/element/render/MinimapElementRendererHandler;renderForRenderer(Lxaero/hud/minimap/element/render/MinimapElementRenderer;Lnet/minecraft/client/gui/GuiGraphics;ILxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;ILxaero/hud/minimap/element/render/MinimapElementRenderInfo;)I"))
    private int bons$xaerominimapC49ElementRender(MinimapElementRendererHandler handler, MinimapElementRenderer<?, ?> renderer, GuiGraphics guiGraphics,
                                                  int elementIndex, MultiTextureRenderTypeRendererProvider renderers, int indexLimit,
                                                  MinimapElementRenderInfo renderInfo, Operation<Integer> original) {
        IdentityHashMap<Object, long[]> seen = bons$xaerominimapC49Seen;
        if (seen == null) bons$xaerominimapC49Seen = seen = new IdentityHashMap<>();
        return EmptyElementSkip.render(seen, renderer, elementIndex, original, handler, renderer, guiGraphics, elementIndex, renderers, indexLimit, renderInfo);
    }

    @WrapOperation(method = "renderForRenderer(Lxaero/hud/minimap/element/render/MinimapElementRenderer;Lnet/minecraft/client/gui/GuiGraphics;ILxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;ILxaero/hud/minimap/element/render/MinimapElementRenderInfo;)I",
            require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lxaero/hud/minimap/element/render/MinimapElementRenderProvider;begin(Lxaero/hud/minimap/element/render/MinimapElementRenderLocation;Ljava/lang/Object;)V"))
    private void bons$xaerominimapC49ElementBegin(MinimapElementRenderProvider<?, ?> provider, MinimapElementRenderLocation location, Object context,
                                                  Operation<Void> original) {
        if (!EmptyElementSkip.leftOut(provider)) original.call(provider, location, context);
    }

    @WrapOperation(method = "renderForRenderer(Lxaero/hud/minimap/element/render/MinimapElementRenderer;Lnet/minecraft/client/gui/GuiGraphics;ILxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;ILxaero/hud/minimap/element/render/MinimapElementRenderInfo;)I",
            require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lxaero/hud/minimap/element/render/MinimapElementRenderProvider;hasNext(Lxaero/hud/minimap/element/render/MinimapElementRenderLocation;Ljava/lang/Object;)Z"))
    private boolean bons$xaerominimapC49ElementHasNext(MinimapElementRenderProvider<?, ?> provider, MinimapElementRenderLocation location, Object context,
                                                       Operation<Boolean> original) {
        return !EmptyElementSkip.leftOut(provider) && original.call(provider, location, context);
    }

    @WrapOperation(method = "renderForRenderer(Lxaero/hud/minimap/element/render/MinimapElementRenderer;Lnet/minecraft/client/gui/GuiGraphics;ILxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;ILxaero/hud/minimap/element/render/MinimapElementRenderInfo;)I",
            require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lxaero/hud/minimap/element/render/MinimapElementRenderProvider;end(Lxaero/hud/minimap/element/render/MinimapElementRenderLocation;Ljava/lang/Object;)V"))
    private void bons$xaerominimapC49ElementEnd(MinimapElementRenderProvider<?, ?> provider, MinimapElementRenderLocation location, Object context,
                                                Operation<Void> original) {
        if (!EmptyElementSkip.leftOut(provider)) original.call(provider, location, context);
    }
}
