package bons.furious.mixin.attribute_lookup;

import bons.furious.patch.attribute_lookup.ProjectileReapply;
import com.bawnorton.mixinsquared.TargetHandler;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * projectiledamage_weapon_modifier_unchanged_skip (Projectile Damage 3.2.3+1.20.1, GPL-3.0; server; since 1.0.49): a
 * MixinSquared @TargetHandler @WrapOperation of the AttributeMap.addTransientAttributeModifiers (m_22178_) call inside
 * Projectile Damage's ServerWorldMixin.pre_spawnEntity handler, merged into ServerLevel.addFreshEntity: skipped when it would
 * change nothing (ProjectileReapply.unchanged), otherwise it runs as shipped. Priority 1500 (after Projectile Damage's
 * mixin), require = 0, expect = 0: without that handler nothing is injected. Our own logic only.
 */
@Mixin(value = ServerLevel.class, priority = 1500, remap = false)
public abstract class ProjectileDamageReapplyMixin {
    @TargetHandler(mixin = "net.projectile_damage.mixin.ServerWorldMixin", name = "pre_spawnEntity")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeMap;m_22178_(Lcom/google/common/collect/Multimap;)V"), require = 0, expect = 0)
    private void bons$keepWeaponModifiers(AttributeMap map, Multimap<Attribute, AttributeModifier> modifiers, Operation<Void> original) {
        if (ProjectileReapply.unchanged(map, modifiers)) return;
        original.call(map, modifiers);
    }
}
