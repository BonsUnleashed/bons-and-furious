package bons.furious.mixin.mcreator_guards49;

import bons.furious.patch.mcreator_guards49.MacabreAnimationGuards;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * macabre_item_animation_idle (Macabre 1.0.1 for NeoForge 1.21.1; both sides; since 1.0.49), on the
 * mcreator_item_animation_idle design.
 *
 * ItemAnimationFactory.animatedItems(PlayerTickEvent.Post) (2,594 bytes) copies both held stacks for every player once a
 * tick, then acts only while a hand holds a GeckoLib item (13 item classes of the mod). It now returns at once when that
 * test is false, evaluated on the held stacks themselves (MacabreAnimationGuards.idle); otherwise it runs as shipped.
 * Guarded by the handler's fingerprint. Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "com.curseforge.macabre.init.ItemAnimationFactory", remap = false)
public abstract class MacabreItemAnimationMixin {
    @Inject(method = "animatedItems(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V", at = @At("HEAD"), cancellable = true,
            remap = false)
    private static void bons$idle(PlayerTickEvent.Post event, CallbackInfo ci) {
        if (MacabreAnimationGuards.idle(event)) ci.cancel();
    }
}
