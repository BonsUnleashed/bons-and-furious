package bons.furious.mixin.species_post_guard;

import bons.furious.patch.species_post_guard.PostDisguiseGuard;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.ninni.species.client.events.ForgeClientEvents;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * species_post_disguise_guard (Species 3.5, All Rights Reserved: no Species code is carried; client). Fix. The first test of
 * livingEntityRenderer's disguise branch (headItem.is(WICKED_MASK), the method's only ItemStack.is call) is true only for a
 * Pre event, so a Post event never reaches the branch's setCanceled (see {@link PostDisguiseGuard}).
 */
@Mixin(value = ForgeClientEvents.class, remap = false)
public abstract class ForgeClientEventsPostGuardMixin {
    @ModifyExpressionValue(method = "livingEntityRenderer", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z"))
    private static boolean bons$maskOnlyBeforeRender(boolean wearsMask, @Local(argsOnly = true) RenderLivingEvent<?, ?> event) {
        return PostDisguiseGuard.maskTest(wearsMask, event);
    }
}
