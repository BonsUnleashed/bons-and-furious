package bons.furious.mixin.effect_remove_skip;

import bons.furious.patch.effect_remove_skip.AbsentEffectRemove;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * forge_absent_effect_remove_skip (Forge 47.4.28, both sides, since 1.0.49; change, off by default; SRG names).
 *
 * LivingEntity.removeEffect(MobEffect) (m_21195_) is wrapped as a whole (MixinExtras @WrapMethod): when the entity holds no
 * instance of the effect and its class inherits removeEffectNoUpdate, it returns false without posting
 * MobEffectEvent.Remove; otherwise Forge's method runs unchanged (see AbsentEffectRemove). No Minecraft or Forge code is
 * carried.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityAbsentEffectRemoveMixin {
    @Shadow
    @Final
    private Map<MobEffect, MobEffectInstance> f_20945_;

    @WrapMethod(method = "m_21195_")
    private boolean bons$absentEffectRemove(MobEffect effect, Operation<Boolean> original) {
        if (AbsentEffectRemove.enabled && !this.f_20945_.containsKey(effect) && AbsentEffectRemove.plain(this.getClass())) {
            AbsentEffectRemove.skippedOne();
            return false;
        }
        return original.call(effect);
    }
}
