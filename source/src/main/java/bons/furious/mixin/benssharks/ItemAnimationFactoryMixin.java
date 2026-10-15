package bons.furious.mixin.benssharks;

import net.mcreator.sharks.item.SpetumItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ben's Sharks 1.2.6: only Spetum hands can reach an animation update. Post, not Forge's START. */
@Mixin(targets = "net.mcreator.sharks.init.ItemAnimationFactory", remap = false)
public abstract class ItemAnimationFactoryMixin {
    @Inject(method = "animatedItems(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void bons$sharksOnlySpetum(PlayerTickEvent.Post event, CallbackInfo ci) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof SpetumItem
                || player.getOffhandItem().getItem() instanceof SpetumItem)) ci.cancel();
    }
}
