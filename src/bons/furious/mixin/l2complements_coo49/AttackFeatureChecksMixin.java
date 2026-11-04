package bons.furious.mixin.l2complements_coo49;

import bons.furious.patch.l2complements_coo49.AttackChecks;
import dev.xkmc.l2complements.content.feature.EntityFeature;
import dev.xkmc.l2damagetracker.init.data.L2DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2complements_attack_checks_cheap_first (L2 Complements 2.6.1, both sides), part 1 of 3: @Redirect on six of the seven
 * EntityFeature.test calls in MagicEventHandler.onLivingAttack (ordinals 0 and 2-6 of the method's test calls:
 * OWNER_PROTECTION, ENVIRONMENTAL_REJECT, MAGIC_REJECT, PROJECTILE_REJECT, FIRE_REJECT, EXPLOSION_REJECT; ordinal 1,
 * INVINCIBLE, has no paired condition and is not touched). While AttackChecks.fast() holds, each handler evaluates the
 * condition the shipped code ANDs with that test (the same DamageSource reads, with the same tag constants) and leaves the
 * test out when it is false; otherwise it makes the shipped call itself. The handlers capture the target method's
 * LivingAttackEvent argument. Our own logic only (All Rights Reserved target; none of its code is carried).
 */
@Mixin(targets = "dev.xkmc.l2complements.events.MagicEventHandler", remap = false)
public abstract class AttackFeatureChecksMixin {
    private static final String METHOD = "onLivingAttack(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V";
    private static final String TEST = "Ldev/xkmc/l2complements/content/feature/EntityFeature;test(Lnet/minecraft/world/entity/LivingEntity;)Z";

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 0), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Owner(EntityFeature feature, LivingEntity entity, LivingAttackEvent event) {
        if (AttackChecks.fast() && !(event.getSource().m_7639_() instanceof OwnableEntity)) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 2), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Environment(EntityFeature feature, LivingEntity entity, LivingAttackEvent event) {
        if (AttackChecks.fast() && event.getSource().m_7639_() != null) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 3), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Magic(EntityFeature feature, LivingEntity entity, LivingAttackEvent event) {
        if (AttackChecks.fast() && !event.getSource().m_269533_(L2DamageTypes.MAGIC)) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 4), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Projectile(EntityFeature feature, LivingEntity entity, LivingAttackEvent event) {
        if (AttackChecks.fast() && !event.getSource().m_269533_(DamageTypeTags.f_268524_)) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 5), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Fire(EntityFeature feature, LivingEntity entity, LivingAttackEvent event) {
        if (AttackChecks.fast() && !event.getSource().m_269533_(DamageTypeTags.f_268745_)) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 6), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Explosion(EntityFeature feature, LivingEntity entity, LivingAttackEvent event) {
        if (AttackChecks.fast() && !event.getSource().m_269533_(DamageTypeTags.f_268415_)) return AttackChecks.skip();
        return feature.test(entity);
    }
}
