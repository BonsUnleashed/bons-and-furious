package bons.furious.mixin.bowenchants_coo49;

import bons.furious.patch.bowenchants_coo49.BowFixes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * bowenchants_draw_speed_bows_only (Advanced Bow Enchantments 1.2.0, both sides; gameplay fix, off by default): at every
 * return of BowEnchantsEvents.applyDrawSpeedAdjustment (after its own computation, every read made), an item whose use
 * animation is neither the bow's nor the crossbow's gets the event's own duration back (BowFixes.drawSpeedOff). Our own
 * logic only (All Rights Reserved target; none of its code is carried).
 */
@Mixin(targets = "com.doug.bowenchants.events.BowEnchantsEvents", remap = false)
public abstract class BowDrawSpeedMixin {
    @Inject(method = "applyDrawSpeedAdjustment(Lnet/minecraftforge/event/entity/living/LivingEntityUseItemEvent$Tick;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)I",
            at = @At("RETURN"), cancellable = true)
    private static void bons$bowenchants_coo49DrawSpeed(LivingEntityUseItemEvent.Tick event, Player player, ItemStack used, CallbackInfoReturnable<Integer> cir) {
        if (BowFixes.drawSpeedOff(used)) cir.setReturnValue(event.getDuration());
    }
}
