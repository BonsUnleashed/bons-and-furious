package bons.furious.mixin.benssharks;

import bons.furious.patch.benssharks.Bleed;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): records an entity the moment it gets Ben's Bleeding effect (runs on effect
 * application only, not per tick). Helper: {@link Bleed}.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEffectAddedMixin {
    @Inject(method = "m_142540_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("TAIL"), remap = false)
    private void bons$sharksEffectAdded(MobEffectInstance instance, Entity source, CallbackInfo ci) {
        Bleed.onAdded((LivingEntity) (Object) this, instance);
    }
}
