package bons.furious.mixin.bomd_c49;

import bons.furious.patch.bomd_c49.PlayerTickPhase;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * bomd_player_tick_end_only (Bosses of Mass Destruction 1.1.2; server; since 1.0.49; gameplay fix, off by default):
 * ForgeEvents.onPlayerTick is wrapped so the START post returns before it (PlayerTickPhase); END runs the original.
 */
@Mixin(targets = "com.cerbon.bosses_of_mass_destruction.event.ForgeEvents", remap = false)
public abstract class PlayerTickPhaseMixin {
    @WrapMethod(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V", remap = false)
    private static void bons$endPhaseOnly(TickEvent.PlayerTickEvent event, Operation<Void> original) {
        PlayerTickPhase.onPlayerTick(event, original);
    }
}
