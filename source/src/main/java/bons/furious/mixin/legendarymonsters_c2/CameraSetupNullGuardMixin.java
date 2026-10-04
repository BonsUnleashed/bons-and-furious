package bons.furious.mixin.legendarymonsters_c2;

import bons.furious.patch.legendarymonsters_c2.CameraNullGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.miauczel.legendary_monsters.client.event.ClientEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * legendary_monsters_camera_null_guard (Legendary Monsters, All Rights Reserved; 1.21.1 tested build: Legendary Monsters
 * 2.2.3 for NeoForge 1.21.1; client; a fix): the camera shake listener runs only when there is a local player (see
 * {@link CameraNullGuard}). The listener is static, so the wrapper's Operation is a constant (no allocation per frame).
 *
 * Ported to 1.21.1: unchanged. NeoForge's event bus 8.0.5 registers every @SubscribeEvent method from getDeclaredMethods,
 * private ones too; MixinExtras 0.5.3's @WrapMethod moves the body into a private method without copying method
 * annotations (WrapMethodStage.move), so ClientEvent still has exactly one onCameraSetup listener: this wrapper.
 */
@Mixin(value = ClientEvent.class, remap = false)
public abstract class CameraSetupNullGuardMixin {
    @WrapMethod(method = "onCameraSetup")
    private static void bons$onlyWithPlayer(ViewportEvent.ComputeCameraAngles event, Operation<Void> original) {
        CameraNullGuard.onCameraSetup(event, original);
    }
}
