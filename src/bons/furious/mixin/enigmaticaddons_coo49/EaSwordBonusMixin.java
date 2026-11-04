package bons.furious.mixin.enigmaticaddons_coo49;

import auviotre.enigmatic.addon.contents.items.AnnihilatingSword;
import bons.furious.patch.enigmaticaddons_coo49.SwordBonus;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticaddons_annihilating_sword_bonus_memo (Enigmatic Addons 1.2.7, server branch; All Rights Reserved: no code of
 * the mod is carried). In AnnihilatingSword.onTick (every living entity, every tick): MixinExtras @WrapOperation on the
 * getAttackSpeedBonus() call that feeds removeAttributeModifiers (the method's second getAttackSpeedBonus call): its first
 * result is kept and handed out again (SwordBonus.removal). The call that adds the bonus is left as shipped.
 */
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.AnnihilatingSword", remap = false)
public abstract class EaSwordBonusMixin {
    @WrapOperation(method = "onTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lauviotre/enigmatic/addon/contents/items/AnnihilatingSword;getAttackSpeedBonus()Lcom/google/common/collect/Multimap;", ordinal = 1),
            require = 1, allow = 1)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Multimap bons$enigmaticaddonsCoo49SwordBonus(AnnihilatingSword sword, Operation<Multimap> original) {
        return (Multimap) SwordBonus.removal(sword, (Operation) original);
    }
}
