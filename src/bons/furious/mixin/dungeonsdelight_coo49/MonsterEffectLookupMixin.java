package bons.furious.mixin.dungeonsdelight_coo49;

import bons.furious.patch.dungeonsdelight_coo49.MonsterEffectLookup;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.yirmiri.dungeonsdelight.common.effect.MonsterEffect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * dungeonsdelight_monster_effect_lookup (Dungeons Delight 1.2.10 / 1.3.1, both sides): HEAD of MonsterEffect.applyEffectTick
 * returns when the holder's activeEffects map has no key equal to the normal variant (then the original loop finds nothing
 * and changes nothing; see MonsterEffectLookup); otherwise the original runs unchanged. TenacityEffect's override calls this
 * method last (super), so its own heal still runs first. COO 5.8 hooks the same method (dungeonsdelight.MixinDdMonsterEffectSwap,
 * option dungeonsdelight.leanMonsterEffectSwap); a HEAD injection composes with another mod's injection or overwrite.
 */
@Mixin(value = MonsterEffect.class, remap = false)
public abstract class MonsterEffectLookupMixin {
    @Shadow
    @Final
    private MobEffect normalVariant;

    @Inject(method = "m_6742_", at = @At("HEAD"), cancellable = true)
    private void bons$normalVariantAbsent(LivingEntity living, int amplifier, CallbackInfo ci) {
        if (living != null && MonsterEffectLookup.absent(living, ((LivingEntityEffectsAccessor) living).bons$activeEffectMap(), this.normalVariant)) ci.cancel();
    }
}
