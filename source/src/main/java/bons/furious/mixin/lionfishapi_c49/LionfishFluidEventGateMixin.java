package bons.furious.mixin.lionfishapi_c49;

import bons.furious.patch.lionfishapi_c49.FluidEventGate;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * lionfishapi_fluid_render_event_gate (Lionfish API 3.1 for Minecraft 1.21.1, client): a MixinSquared @TargetHandler HEAD
 * injection into Lionfish's ItemBlockRenderTypesMixin.lionfish_getFluidRenderLayer handler (merged into ItemBlockRenderTypes
 * at the end of getRenderLayer(FluidState)) that returns at once while nothing listens for its EventGetFluidRenderType on
 * NeoForge.EVENT_BUS (FluidEventGate explains why nothing observable changes). Our own logic only. Priority 1500: after
 * Lionfish's mixin.
 */
@Mixin(value = ItemBlockRenderTypes.class, priority = 1500, remap = false)
public abstract class LionfishFluidEventGateMixin {
    @TargetHandler(mixin = "com.github.L_Ender.lionfishapi.mixin.client.ItemBlockRenderTypesMixin", name = "lionfish_getFluidRenderLayer")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void bons$skipUnheardFluidEvent(FluidState state, CallbackInfoReturnable<RenderType> theirs, CallbackInfo ci) {
        if (FluidEventGate.skip(state, theirs.getReturnValue())) ci.cancel();
    }
}
