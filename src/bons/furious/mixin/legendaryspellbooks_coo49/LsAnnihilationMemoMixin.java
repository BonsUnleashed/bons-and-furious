package bons.furious.mixin.legendaryspellbooks_coo49;

import bons.furious.patch.legendaryspellbooks_coo49.LsAnnihilationMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * legendary_spellbooks_annihilation_bonus_memo (Legendary Spellbooks 0.4.1, LGPL-3.0; server levels): a MixinExtras
 * @WrapMethod on AnnihilationAttributeBonusEvent.onLivingTick skips the runs that would read exactly what the entity's last
 * full run read and write nothing; the three @WrapOperations on its getAttribute (m_21051_) calls keep, unchanged, the
 * instances a full run looks up. See LsAnnihilationMemo. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "dev.higurashi.legendary_spellbooks.common.event.AnnihilationAttributeBonusEvent", remap = false)
public abstract class LsAnnihilationMemoMixin {
    @WrapMethod(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V")
    private static void bons$legendaryspellbooksCoo49AnnihilationTick(LivingEvent.LivingTickEvent event, Operation<Void> original) {
        LsAnnihilationMemo.tick(event, original);
    }

    @WrapOperation(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21051_(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;", ordinal = 0),
            require = 1, allow = 1)
    private static AttributeInstance bons$legendaryspellbooksCoo49FireLookup(LivingEntity entity, Attribute attribute, Operation<AttributeInstance> original) {
        return LsAnnihilationMemo.lookup(entity, attribute, original, 0);
    }

    @WrapOperation(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21051_(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;", ordinal = 1),
            require = 1, allow = 1)
    private static AttributeInstance bons$legendaryspellbooksCoo49EnderLookup(LivingEntity entity, Attribute attribute, Operation<AttributeInstance> original) {
        return LsAnnihilationMemo.lookup(entity, attribute, original, 1);
    }

    @WrapOperation(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21051_(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;", ordinal = 2),
            require = 1, allow = 1)
    private static AttributeInstance bons$legendaryspellbooksCoo49AnnihilationLookup(LivingEntity entity, Attribute attribute, Operation<AttributeInstance> original) {
        return LsAnnihilationMemo.lookup(entity, attribute, original, 2);
    }
}
