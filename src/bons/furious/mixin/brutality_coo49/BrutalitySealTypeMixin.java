package bons.furious.mixin.brutality_coo49;

import bons.furious.patch.brutality_coo49.BrutalitySeal;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.goo.brutality.util.SealUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * brutality_seal_type_no_throw (Brutality 0.8.5, MIT; both sides; since 1.0.49): MixinExtras @WrapOperation on the
 * SEAL_TYPE.valueOf call of EntitySealTypeCap.deserializeNBT(CompoundTag): the empty name (the only name the capability ever
 * reads, its save key differs from its load key) answers NONE without the IllegalArgumentException the original builds and
 * catches; the try block then sets NONE exactly as the catch block would. Helper BrutalitySeal.
 */
@Mixin(targets = "net.goo.brutality.entity.capabilities.EntityCapabilities$EntitySealTypeCap", remap = false)
public abstract class BrutalitySealTypeMixin {
    @WrapOperation(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE", target = "Lnet/goo/brutality/util/SealUtils$SEAL_TYPE;valueOf(Ljava/lang/String;)Lnet/goo/brutality/util/SealUtils$SEAL_TYPE;"),
            require = 1, allow = 1)
    private SealUtils.SEAL_TYPE bons$brutalityCoo49SealType(String name, Operation<SealUtils.SEAL_TYPE> original) {
        return BrutalitySeal.valueOf(name, original);
    }
}
