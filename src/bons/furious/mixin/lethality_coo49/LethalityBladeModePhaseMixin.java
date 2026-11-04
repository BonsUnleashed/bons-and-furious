package bons.furious.mixin.lethality_coo49;

import bons.furious.patch.lethality_coo49.LethalityBladeMode;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraftforge.event.TickEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * lethality_blade_mode_end_phase_only (Lethality 0.4.1, MIT; server; since 1.0.49; change, off by default): both
 * BladeModeEvents.onPlayerTick and HFMeowrasamaEvents.onPlayerTick open with `if (event.player.level().isClientSide)
 * return;`. MixinExtras @ModifyExpressionValue on that first isClientSide read answers true for a START post as well, so
 * the handler returns there (after two plain reads) unless the event is the END post of the player tick. Helper
 * LethalityBladeMode.
 */
@Mixin(targets = {"net.daphne.lethality.client.BladeModeEvents", "net.daphne.lethality.events.HFMeowrasamaEvents"}, remap = false)
public abstract class LethalityBladeModePhaseMixin {
    @ModifyExpressionValue(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;f_46443_:Z", opcode = Opcodes.GETFIELD, ordinal = 0),
            require = 1, allow = 1)
    private static boolean bons$lethalityCoo49EndOnly(boolean clientSide, TickEvent.PlayerTickEvent event) {
        return clientSide || LethalityBladeMode.skip(event.phase);
    }
}
