package bons.furious.mixin.l2library_c2;

import bons.furious.patch.l2library_c2.EffectIconFastPath;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.xkmc.l2library.capability.entity.GeneralCapabilityHolder;
import dev.xkmc.l2library.init.events.ClientEffectRenderEvents;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * l2library_effect_icon_fast_path (L2 Library 2.5.1-slim, LGPL-2.1, Jar-in-Jar in Ars Delight 1.2.2; client).
 *
 * The capability check that opens ClientEffectRenderEvents.onLivingRenderEvents (called for every rendered living entity,
 * GeckoLib entities included) goes through EffectIconFastPath: the same lookup, and a return right there when the
 * entity's client effect map is empty (see EffectIconFastPath for the proof and the one documented internal difference).
 */
@Mixin(value = ClientEffectRenderEvents.class, remap = false)
public abstract class EffectIconFastPathMixin {
    @WrapOperation(method = "onLivingRenderEvents", at = @At(value = "INVOKE",
            target = "Ldev/xkmc/l2library/capability/entity/GeneralCapabilityHolder;isProper(Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Z"))
    private static boolean bons$properWithEffects(GeneralCapabilityHolder<?, ?> holder, ICapabilityProvider entity, Operation<Boolean> original) {
        return EffectIconFastPath.proper(holder, entity, original);
    }
}
