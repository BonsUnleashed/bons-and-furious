package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.WitherHandles;
import com.alexander.witherreincarnated.attachments.WRAttachments;
import com.alexander.witherreincarnated.capabilities.possessed.PossessedCapability;
import com.alexander.witherreincarnated.events.HandlePossessedMobsEvent;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * witherreincarnated_possession_handles (Wither Reincarnated 1.0.5 for NeoForge 1.21.1, All Rights Reserved: this injection
 * carries none of its code; both sides; since 1.0.49): possessedTick returns at once for a Mob that already has its possession
 * attachment and whose state is idle (WitherHandles.idle: the handler would change nothing). Everything else runs as shipped;
 * a mob without the attachment runs the handler, which creates it.
 */
@Mixin(value = HandlePossessedMobsEvent.class, remap = false)
public abstract class WitherPossessedHandlesMixin {
    @WrapMethod(method = "possessedTick(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)V")
    private static void bons$idlePossession(EntityTickEvent.Pre event, Operation<Void> original) {
        if (WitherHandles.enabled && event != null && event.getEntity() instanceof Mob mob) {
            PossessedCapability possessed = mob.getExistingDataOrNull(WRAttachments.POSSESSED);
            if (possessed != null && WitherHandles.idle(possessed)) {
                if (WitherHandles.SHADOW) {
                    original.call(event);
                    WitherHandles.shadow(possessed);
                }
                return;
            }
        }
        original.call(event);
    }
}
