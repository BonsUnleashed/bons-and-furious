package bons.furious.mixin.dummmmmmy_c49;

import bons.furious.patch.dummmmmmy_c49.DamageNumberFixes;
import net.mehvahdjukaar.dummmmmmy.Dummmmmmy;
import net.mehvahdjukaar.dummmmmmy.common.ModEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * dummmmmmy_damage_number_send_first (MmmMmmMmmMmm 1.20-2.0.12; server side): a cancellable HEAD inject on
 * ModEvents.onEntityDamage. After the mod's own first checks, in its order (level side, not a target dummy, amount not 0:
 * when one fails the method does nothing either way), the method returns before the damage number message is built when
 * its mode switch would send nothing (DamageNumberFixes); otherwise the mod's body runs unchanged. onEntityHeal is left as
 * it is: its message has no damage source, so building it never throws and costs nothing measurable.
 */
@Mixin(value = ModEvents.class, remap = false)
public abstract class ModEventsSendFirstMixin {
    @Inject(method = "onEntityDamage(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$dummyC49Damage(LivingEntity target, float amount, DamageSource source, CallbackInfo ci) {
        if (!DamageNumberFixes.sendFirst) return;
        if (target.m_9236_().f_46443_ || target.m_6095_() == Dummmmmmy.TARGET_DUMMY.get() || amount == 0.0f) return;
        if (!DamageNumberFixes.damageSent(source)) ci.cancel();
    }
}
