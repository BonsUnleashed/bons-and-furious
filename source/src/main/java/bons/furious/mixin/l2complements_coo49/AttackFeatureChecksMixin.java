package bons.furious.mixin.l2complements_coo49;

import bons.furious.patch.l2complements_coo49.AttackChecks;
import dev.xkmc.l2complements.content.feature.EntityFeature;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2complements_attack_checks_cheap_first (L2 Complements, LGPL-2.1; 1.21.1 tested build 3.1.3; both sides), part 1 of 3:
 * a redirect on six of the seven EntityFeature.test calls in MagicEventHandler.onLivingAttack (ordinals 0 and 2-6 of the
 * method's test calls: OWNER_PROTECTION, ENVIRONMENTAL_REJECT, MAGIC_REJECT, PROJECTILE_REJECT, FIRE_REJECT,
 * EXPLOSION_REJECT; ordinal 1, INVINCIBLE, has no paired condition and is not touched). While AttackChecks.fast() holds,
 * each handler evaluates the condition the shipped code ANDs with that test (the same DamageSource reads, with the same tag
 * constants) and leaves the test out when it is false; otherwise it makes the shipped call itself. The handlers capture the
 * target method's LivingIncomingDamageEvent argument. Our own logic only (none of L2 Complements' code is carried).
 *
 * Ported to 1.21.1: the handler listens to LivingIncomingDamageEvent (was LivingAttackEvent); MAGIC_REJECT pairs with
 * NeoForge's Tags.DamageTypes.IS_MAGIC (was L2 Damage Tracker's magic tag); the five reject tests now sit behind the
 * shipped bypass checks, which the redirects leave as they are.
 */
@Mixin(targets = "dev.xkmc.l2complements.events.MagicEventHandler", remap = false)
public abstract class AttackFeatureChecksMixin {
    private static final String METHOD = "onLivingAttack(Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;)V";
    private static final String TEST = "Ldev/xkmc/l2complements/content/feature/EntityFeature;test(Lnet/minecraft/world/entity/LivingEntity;)Z";

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 0), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Owner(EntityFeature feature, LivingEntity entity, LivingIncomingDamageEvent event) {
        if (AttackChecks.fast() && !(event.getSource().getEntity() instanceof OwnableEntity)) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 2), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Environment(EntityFeature feature, LivingEntity entity, LivingIncomingDamageEvent event) {
        if (AttackChecks.fast() && event.getSource().getEntity() != null) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 3), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Magic(EntityFeature feature, LivingEntity entity, LivingIncomingDamageEvent event) {
        if (AttackChecks.fast() && !event.getSource().is(Tags.DamageTypes.IS_MAGIC)) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 4), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Projectile(EntityFeature feature, LivingEntity entity, LivingIncomingDamageEvent event) {
        if (AttackChecks.fast() && !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 5), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Fire(EntityFeature feature, LivingEntity entity, LivingIncomingDamageEvent event) {
        if (AttackChecks.fast() && !event.getSource().is(DamageTypeTags.IS_FIRE)) return AttackChecks.skip();
        return feature.test(entity);
    }

    @Redirect(method = METHOD, at = @At(value = "INVOKE", target = TEST, ordinal = 6), require = 1, allow = 1)
    private static boolean bons$l2complements_coo49Explosion(EntityFeature feature, LivingEntity entity, LivingIncomingDamageEvent event) {
        if (AttackChecks.fast() && !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return AttackChecks.skip();
        return feature.test(entity);
    }
}
