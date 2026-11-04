package bons.furious.mixin.w2w2_coo49;

import bons.furious.patch.w2w2_coo49.W2w2Saves;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.minimap.waypoints.WaypointWorld;
import xaero.common.settings.ModSettings;

/**
 * w2w2_waypoint_save_coalesce (Xaero's Map - Waystones Compatibility 1.1 with Xaero's Minimap, client; kind change, off by
 * default; no code of that mod carried). Both XaeronCompatibility.addWaypoint overloads: the ModSettings.saveWaypoints call
 * is remembered and run once per waypoint world at the end of the client tick (and before logout / level unload); see
 * W2w2Saves. One wrapper per overload, so each stands alone.
 */
@Mixin(targets = "fr.shoqapik.w2w2.XaeronCompatibility", remap = false)
public abstract class XaeronSaveMixin {
    @WrapOperation(method = "addWaypoint(Lnet/minecraft/core/BlockPos;Ljava/lang/String;Lnet/blay09/mods/waystones/api/IWaystone;)V",
            at = @At(value = "INVOKE", target = "Lxaero/common/settings/ModSettings;saveWaypoints(Lxaero/common/minimap/waypoints/WaypointWorld;)V"),
            require = 1, allow = 1)
    private static void bons$w2w2SaveCoalesceWaystone(ModSettings settings, WaypointWorld world, Operation<Void> original) {
        if (!W2w2Saves.defer(settings, world, original)) original.call(settings, world);
    }

    @WrapOperation(method = "addWaypoint(Lnet/minecraft/core/BlockPos;Ljava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lxaero/common/settings/ModSettings;saveWaypoints(Lxaero/common/minimap/waypoints/WaypointWorld;)V"),
            require = 1, allow = 1)
    private static void bons$w2w2SaveCoalescePlain(ModSettings settings, WaypointWorld world, Operation<Void> original) {
        if (!W2w2Saves.defer(settings, world, original)) original.call(settings, world);
    }
}
