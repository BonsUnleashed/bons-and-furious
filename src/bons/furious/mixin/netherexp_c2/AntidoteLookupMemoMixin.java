package bons.furious.mixin.netherexp_c2;

import bons.furious.patch.netherexp_c2.AntidoteGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jadenxgamer.netherexp.registry.item.custom.AntidoteItem;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * netherexp_antidote_effect_guard (Jaden's Nether Expansion 2.3.5, both sides), since 1.0.49: the mob-effect registry lookup
 * of AntidoteItem.getAntidoteEffect answers a found effect from a bounded memo (AntidoteGuard.lookup). Our own logic only.
 */
@Mixin(value = AntidoteItem.class, remap = false)
public abstract class AntidoteLookupMemoMixin {
    @WrapOperation(method = "getAntidoteEffect", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Registry;m_7745_(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;"))
    private static Object bons$antidoteEffect(Registry<?> registry, ResourceLocation id, Operation<Object> original) {
        return AntidoteGuard.lookup(registry, id, original);
    }
}