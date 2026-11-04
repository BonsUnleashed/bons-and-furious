package bons.furious.mixin.mekaweapons_tana_coo49;

import bons.furious.patch.mekaweapons_tana_coo49.TanaSweep;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mekanism.api.providers.IModuleDataProvider;
import meranha.mekaweapons.MekaWeapons;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mekaweapons_tana_sweep_full_swing (gameplay fix, off by default; Mekanism Weapons 4.2.1): in the Meka-Tana branch of
 * MekaWeapons.MekaWeaponsAttackEvent, the isModuleEnabled(stack, SWEEPING_UNIT) test answers false unless the attacker's
 * getAttackStrengthScale(0.5f) > 0.9f, so the listener returns before its sweep loop on spam clicks (TanaSweep). It is the
 * only isModuleEnabled call in that listener. COO 5.8 hooks the same method (mekaweapons.MixinMekaTanaSweepCooldown, option
 * mekaweapons.tanaSweepNeedsFullSwing).
 */
@Mixin(value = MekaWeapons.class, remap = false)
public abstract class MekaTanaSweepMixin {
    @WrapOperation(method = "MekaWeaponsAttackEvent", at = @At(value = "INVOKE",
            target = "Lmeranha/mekaweapons/MekaWeaponsUtils;isModuleEnabled(Lnet/minecraft/world/item/ItemStack;Lmekanism/api/providers/IModuleDataProvider;)Z"),
            require = 1, allow = 1)
    private boolean bons$sweepNeedsFullSwing(ItemStack stack, IModuleDataProvider<?> module, Operation<Boolean> original,
                                             @Local(argsOnly = true) AttackEntityEvent event) {
        boolean on = original.call(stack, module);
        return on && TanaSweep.enabled ? TanaSweep.charged(event.getEntity().m_36403_(0.5f)) : on;
    }
}
