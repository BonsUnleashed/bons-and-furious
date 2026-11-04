package bons.furious.mixin.betterspellcasting_coo49;

import bons.furious.patch.betterspellcasting_coo49.BscSpells;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * better_spellcasting_queued_spell_expiry (Better Spellcasting 1.2.0, LGPL-2.1-or-later; server; gameplay fix, off by
 * default): MixinExtras @WrapOperation on the pending.remove(player uuid) call in AttackSpellHandler.onLivingHurt (the
 * damage event that fires an armed on-hit weapon spell): the entry is removed as shipped, and when the swing that armed
 * it is older than the window (its combo state's lastSwingTick, the attacker's game time) the wrapper answers null, so
 * the handler returns without casting. See BscSpells. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.kayn.better_spellcasting.event.AttackSpellHandler", remap = false)
public abstract class BscQueuedSpellExpiryMixin {
    @Shadow(remap = false)
    @Final
    @SuppressWarnings("rawtypes")
    private static ConcurrentHashMap serverCombo;

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onLivingHurt(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentHashMap;remove(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1, allow = 1)
    private static Object bons$betterspellcastingCoo49QueuedSpell(ConcurrentHashMap pending, Object key, Operation<Object> original, LivingHurtEvent event) {
        return BscSpells.queued(original.call(pending, key), key, event, serverCombo);
    }
}
