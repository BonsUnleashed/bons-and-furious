package bons.furious.mixin.iceandfire_client_c2;

import bons.furious.patch.iceandfire_client_c2.PathDebugStageGate;
import com.github.alexthe666.iceandfire.event.ClientEvents;
import com.github.alexthe666.iceandfire.pathfinding.raycoms.WorldEventContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * iceandfire_path_debug_stage_gate (Ice and Fire 2.1.13-1.20.1-beta-5, LGPL-3.0; client).
 *
 * ClientEvents.renderWorldLastEvent (RenderLevelStageEvent, lowest priority) passes every render stage to
 * WorldEventContext.renderWorldLastEvent. That call goes through PathDebugStageGate: at the two stages the pathfinding debug
 * renderer uses it runs unchanged; at the other stages it ends after the buffer-source fetch that opens the original (see
 * PathDebugStageGate for why nothing observable is skipped).
 */
@Mixin(value = ClientEvents.class, remap = false)
public abstract class PathDebugStageGateMixin {
    @WrapOperation(method = "renderWorldLastEvent", at = @At(value = "INVOKE",
            target = "Lcom/github/alexthe666/iceandfire/pathfinding/raycoms/WorldEventContext;renderWorldLastEvent(Lnet/minecraftforge/client/event/RenderLevelStageEvent;)V"))
    private static void bons$usedStagesOnly(WorldEventContext context, RenderLevelStageEvent event, Operation<Void> original) {
        if (!PathDebugStageGate.skip(event)) original.call(context, event);
    }
}
