package bons.furious.mixin.benssharks;

import net.mcreator.sharks.item.SpetumItem;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * benssharks_item_animation_idle (Ben's Sharks 1.3.0): ItemAnimationFactory.animatedItems copied both held stacks (NBT and
 * all) for every player, twice a tick, before even checking the phase, and only ever acts on Ben's Spetum. It now returns at
 * once unless it is the START phase and a hand holds a Spetum - the only case in which it did anything.
 */
@Mixin(targets = "net.mcreator.sharks.init.ItemAnimationFactory", remap = false)
public abstract class ItemAnimationFactoryMixin {
    @Inject(method = "animatedItems(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void bons$sharksOnlySpetum(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        Player p = event.player;
        if (event.phase != TickEvent.Phase.START
                || !(p.m_21205_().m_41720_() instanceof SpetumItem || p.m_21206_().m_41720_() instanceof SpetumItem)) {
            ci.cancel();
        }
    }
}
