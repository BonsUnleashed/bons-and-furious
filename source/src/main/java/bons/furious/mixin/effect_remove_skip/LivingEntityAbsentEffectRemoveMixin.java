package bons.furious.mixin.effect_remove_skip;

import bons.furious.patch.effect_remove_skip.AbsentEffectRemove;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * forge_absent_effect_remove_skip (Minecraft 1.21.1 on NeoForge 21.1.252, both sides; change, off by default).
 *
 * LivingEntity.removeEffect(Holder) is wrapped as a whole (MixinExtras @WrapMethod): when the entity holds no instance of the
 * effect (activeEffects.containsKey, the test hasEffect makes) and its class inherits LivingEntity's removeEffectNoUpdate, it
 * returns false without EventHooks.onEffectRemoved posting MobEffectEvent.Remove; otherwise NeoForge's method runs
 * unchanged (see AbsentEffectRemove). No Minecraft or NeoForge code is carried.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityAbsentEffectRemoveMixin {
    @Shadow
    @Final
    private Map<Holder<MobEffect>, MobEffectInstance> activeEffects;

    @WrapMethod(method = "removeEffect(Lnet/minecraft/core/Holder;)Z")
    private boolean bons$absentEffectRemove(Holder<MobEffect> effect, Operation<Boolean> original) {
        if (AbsentEffectRemove.enabled && !this.activeEffects.containsKey(effect) && AbsentEffectRemove.plain(this.getClass())) {
            AbsentEffectRemove.skippedOne();
            return false;
        }
        return original.call(effect);
    }
}
