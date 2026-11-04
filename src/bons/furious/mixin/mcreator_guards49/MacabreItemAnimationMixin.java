package bons.furious.mixin.mcreator_guards49;

import bons.furious.patch.mcreator_guards49.AnimationGuards;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * macabre_item_animation_idle (Macabre 1.0.0, also 0.8.4 and 0.9.2: the same class; both sides; since 1.0.49), on the
 * mcreator_item_animation_idle layer.
 *
 * ItemAnimationFactory.animatedItems (2,266 bytes) copies both held stacks for every player twice a tick, then acts only in the
 * START phase while a hand holds a GeckoLib item (13 item classes of the mod). It now returns at once when that test is false,
 * evaluated on the held stacks themselves (AnimationGuards.Caller.idle); otherwise it runs as shipped. Guarded by the handler's
 * fingerprint. Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "com.curseforge.macabre.init.ItemAnimationFactory", remap = false)
public abstract class MacabreItemAnimationMixin {
    @Inject(method = "animatedItems(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V", at = @At("HEAD"), cancellable = true,
            remap = false)
    private static void bons$idle(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (AnimationGuards.MACABRE_ITEM.idle(event)) ci.cancel();
    }
}
