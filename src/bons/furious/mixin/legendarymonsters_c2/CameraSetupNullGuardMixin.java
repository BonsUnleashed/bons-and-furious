package bons.furious.mixin.legendarymonsters_c2;

import bons.furious.patch.legendarymonsters_c2.CameraNullGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.miauczel.legendary_monsters.client.event.ClientEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * legendary_monsters_camera_null_guard (Legendary Monsters 2.2.2, All Rights Reserved; client; a fix): the camera shake
 * listener runs only when there is a local player (see {@link CameraNullGuard}). The listener is static, so the wrapper's
 * Operation is a constant (no allocation per frame).
 */
@Mixin(value = ClientEvent.class, remap = false)
public abstract class CameraSetupNullGuardMixin {
    @WrapMethod(method = "onCameraSetup")
    private static void bons$onlyWithPlayer(ViewportEvent.ComputeCameraAngles event, Operation<Void> original) {
        CameraNullGuard.onCameraSetup(event, original);
    }
}
