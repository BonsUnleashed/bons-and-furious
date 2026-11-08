package bons.furious.mixin.xaerominimap_c49;

import bons.furious.patch.xaerominimap_c49.MapRedrawCap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xaero.common.graphics.CustomVertexConsumers;
import xaero.common.graphics.ImprovedFramebuffer;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.render.MinimapFBORenderer;
import xaero.hud.minimap.module.MinimapSession;

/**
 * xaerominimap_map_redraw_cap (Xaero's Minimap 26.6.0 for NeoForge 1.21.1, client, since 1.0.49; change, off by default).
 * MixinExtras @WrapMethod on MinimapFBORenderer.renderChunksToFBO: the redraw (with every other mod's code inside the method)
 * runs at most `value` times per second unless what the picture is made from changed (MapRedrawCap); otherwise the kept
 * picture is shown again. No Minimap code is carried (All Rights Reserved).
 */
@Mixin(value = MinimapFBORenderer.class, remap = false)
public abstract class MmRedrawCapMixin {
    @Shadow
    private ImprovedFramebuffer scalingFramebuffer;
    @Shadow
    private ImprovedFramebuffer rotationFramebuffer;

    @WrapMethod(method = "renderChunksToFBO(Lxaero/hud/minimap/module/MinimapSession;Lnet/minecraft/client/gui/GuiGraphics;Lxaero/common/minimap/MinimapProcessor;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/resources/ResourceKey;DIFIZZIDDZLxaero/common/graphics/CustomVertexConsumers;)V")
    private void bons$xaerominimapC49RedrawCap(MinimapSession minimapSession, GuiGraphics guiGraphics, MinimapProcessor minimap, Vec3 renderPos,
                                               ResourceKey<Level> mapDimension, double mapDimensionScale, int viewW, float partial, int level,
                                               boolean useWorldMap, boolean lockedNorth, int shape, double ps, double pc, boolean cave,
                                               CustomVertexConsumers cvc, Operation<Void> original) {
        if (MapRedrawCap.redraw(scalingFramebuffer, rotationFramebuffer, ((MinimapFBORenderer) (Object) this).getZoom(), minimap, mapDimension,
                mapDimensionScale, viewW, level, useWorldMap, lockedNorth, shape, cave))
            original.call(minimapSession, guiGraphics, minimap, renderPos, mapDimension, mapDimensionScale, viewW, partial, level, useWorldMap,
                    lockedNorth, shape, ps, pc, cave, cvc);
    }
}
