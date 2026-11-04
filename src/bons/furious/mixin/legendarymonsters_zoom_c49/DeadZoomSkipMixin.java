package bons.furious.mixin.legendarymonsters_zoom_c49;

import bons.furious.patch.legendarymonsters_zoom_c49.DeadZoom;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.miauczel.legendary_monsters.client.event.ClientEvent;
import net.miauczel.legendary_monsters.mixin.CameraInvoker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * legendary_monsters_dead_zoom_skip (Legendary Monsters 2.2.2 and 2.2.4, client): in ClientEvent.setDynamicZoom the
 * CameraInvoker call of Camera.getMaxZoom, whose result the listener stores and never reads, answers 0 without running
 * (DeadZoom explains why nothing observable changes). Injector only (All Rights Reserved target).
 */
@Mixin(value = ClientEvent.class, remap = false)
public abstract class DeadZoomSkipMixin {
    @WrapOperation(method = "setDynamicZoom(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V", at = @At(value = "INVOKE",
            target = "Lnet/miauczel/legendary_monsters/mixin/CameraInvoker;legendaryMonsters$invokeGetMaxZoom(D)D"), require = 1, allow = 1)
    private static double bons$skipDiscardedMaxZoom(CameraInvoker camera, double distance, Operation<Double> original) {
        return DeadZoom.skip() ? 0.0D : original.call(camera, distance);
    }
}
