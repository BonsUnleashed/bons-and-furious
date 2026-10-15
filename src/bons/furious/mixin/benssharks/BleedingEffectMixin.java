package bons.furious.mixin.benssharks;

import bons.furious.patch.benssharks.Bleed;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): Bleeding ticks every tick (isDurationEffectTick = true): each call proves
 * the entity still bleeds and still ticks. Helper: {@link Bleed}.
 */
@Mixin(targets = "net.mcreator.sharks.potion.BleedingMobEffect", remap = false)
public abstract class BleedingEffectMixin {
    @Inject(method = "m_6742_(Lnet/minecraft/world/entity/LivingEntity;I)V", at = @At("HEAD"), remap = false)
    private void bons$sharksBleedingTick(LivingEntity entity, int amplifier, CallbackInfo ci) {
        Bleed.onActiveTick(entity);
    }
}
