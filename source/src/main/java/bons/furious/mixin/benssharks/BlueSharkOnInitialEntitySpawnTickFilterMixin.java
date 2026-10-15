package bons.furious.mixin.benssharks;

import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skip only the proven no-op domain of the fingerprinted 1.2.6 handler; event order stays unchanged. */
@Mixin(targets = "net.mcreator.sharks.procedures.BlueSharkOnInitialEntitySpawnProcedure", remap = false)
public abstract class BlueSharkOnInitialEntitySpawnTickFilterMixin {
    @Inject(method = "onEntityTick(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void bons$skipUnrelatedEntity(EntityTickEvent.Pre event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof net.mcreator.sharks.entity.BlueSharkEntity)) ci.cancel();
    }
}
