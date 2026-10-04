package bons.furious.mixin.netherexp_c2;

import bons.furious.patch.netherexp_c2.AntidoteGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jadenxgamer.netherexp.registry.item.custom.AntidoteItem;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * netherexp_antidote_effect_guard (Jaden's Nether Expansion 2.3.5, CC-BY-NC-SA-4.0; both sides; a fix): the two failure
 * points of AntidoteItem.getAntidoteEffect answer "no effect" (null) instead of throwing (see {@link AntidoteGuard}).
 */
@Mixin(value = AntidoteItem.class, remap = false)
public abstract class AntidoteEffectGuardMixin {
    @WrapOperation(method = "getAntidoteEffect", require = 1, allow = 1, at = @At(value = "NEW",
            target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation bons$parseEffectId(String id, Operation<ResourceLocation> original) {
        return AntidoteGuard.parse(id, original);
    }

    @WrapOperation(method = "getAntidoteEffect", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Ljava/util/Objects;requireNonNull(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$unknownEffect(Object effect, Operation<Object> original) {
        return AntidoteGuard.require(effect, original);
    }
}
