package bons.furious.mixin.mcreator_item_animation;

import bons.furious.patch.mcreator_item_animation.ItemAnimationIdle;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * mcreator_item_animation_idle (Under the Moon 1.0.5; both sides; since 1.0.45).
 *
 * ItemAnimationFactory.animatedItems (236 bytes) copies both held stacks for every player twice a tick, then acts only in
 * the START phase while a hand holds a GeckoLib item (1 item class of the mod). It now returns at once when that
 * test is false, evaluated on the held stacks themselves (ItemAnimationIdle.idle); otherwise it runs as shipped. Guarded per
 * target (TargetGuards). Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "net.mcreator.underthemoon.init.ItemAnimationFactory", remap = false)
public abstract class UnderTheMoonItemAnimationMixin {
    @Inject(method = "animatedItems(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V", at = @At("HEAD"), cancellable = true,
            remap = false)
    private static void bons$idle(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (ItemAnimationIdle.idle(event)) ci.cancel();
    }
}
