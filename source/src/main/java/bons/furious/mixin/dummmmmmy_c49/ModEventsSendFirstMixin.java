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
 * dummmmmmy_damage_number_send_first (MmmMmmMmmMmm; 1.21.1 tested build: MmmMmmMmmMmm 1.21-2.1.2 for NeoForge,
 * dummmmmmy-1.21-2.1.2-neoforge.jar; server side): a cancellable HEAD inject on ModEvents.onEntityDamage. After the mod's own
 * first checks, in its order (level side, not a target dummy, amount not 0: when one fails the method does nothing either
 * way), the method returns before the damage number message is built when its mode switch would send nothing
 * (DamageNumberFixes); otherwise the mod's body runs unchanged. With Critical Strike installed the mod reads the source's
 * crit multiplier before building the message, and the switch stands aside. onEntityHeal is left as it is.
 *
 * Ported to 1.21.1: onEntityDamage is called by the mod's LivingEntityMixin from LivingEntity.actuallyHurt (the setHealth
 * call) with the hurt's damage source; the message is built from (entity id, amount, source, crit, level) before the mode
 * switch, as in 2.0.12.
 */
@Mixin(value = ModEvents.class, remap = false)
public abstract class ModEventsSendFirstMixin {
    @Inject(method = "onEntityDamage(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$dummyC49Damage(LivingEntity target, float amount, DamageSource source, CallbackInfo ci) {
        if (!DamageNumberFixes.sendFirst || Dummmmmmy.CRITICAL_STRIKE) return;
        if (target.level().isClientSide || target.getType() == Dummmmmmy.TARGET_DUMMY.get() || amount == 0.0f) return;
        if (!DamageNumberFixes.damageSent(source)) ci.cancel();
    }
}
