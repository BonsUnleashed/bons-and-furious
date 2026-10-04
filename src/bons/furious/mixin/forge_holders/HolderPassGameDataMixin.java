package bons.furious.mixin.forge_holders;

import bons.furious.patch.forge_holders.HolderPassIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * forge_object_holder_pass_index (Forge 47.4.16, both sides).
 *
 * postRegisterEvents calls ObjectHolderRegistry.applyObjectHolders(registryKey.location()::equals) after the register
 * event of each registry. The call is unchanged; around it the pass is announced to HolderPassIndex (that predicate
 * object and the registry name it compares with), so applyObjectHolders' holder loop can call only the holders that can
 * act for this registry. The snapshot is dropped when postRegisterEvents returns. With the runtime switch off nothing is
 * announced and applyObjectHolders runs its own loop. Forge is LGPL-2.1; no Forge code is carried.
 */
@Mixin(value = GameData.class, remap = false)
public abstract class HolderPassGameDataMixin {
    @WrapOperation(method = "postRegisterEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/ObjectHolderRegistry;applyObjectHolders(Ljava/util/function/Predicate;)V"))
    private static void bons$announcePass(Predicate<ResourceLocation> filter, Operation<Void> original, @Local ResourceKey<?> registryKey) {
        if (!HolderPassIndex.enabled) {
            original.call(filter);
            return;
        }
        HolderPassIndex.begin(filter, registryKey.m_135782_());
        try {
            original.call(filter);
        } finally {
            HolderPassIndex.end();
        }
    }

    @Inject(method = "postRegisterEvents", at = @At("RETURN"))
    private static void bons$dropSnapshot(CallbackInfo ci) {
        HolderPassIndex.release();
    }
}
