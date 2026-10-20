package bons.furious.mixin.ldlib_metadata;

import bons.furious.patch.ldlib_metadata.LdlibMetadata;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib.client.model.custommodel.LDLMetadataSection;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ldlib_metadata_no_throw (LDLib 1.0.52.a, client only, since 1.0.45): MixinExtras @WrapOperation on the second
 * Optional.get() call (ordinal 1, the metadata section) in LDLMetadataSection.getMetadata(ResourceLocation). An empty
 * Optional yields LDLMetadataSection.MISSING (the value getMetadata keeps when the stock get() throws into its catch
 * block) without the NoSuchElementException; everything else goes to Optional.get. Why it is identical: see
 * LdlibMetadata. require = 0. LDLib is LGPL-3.0; no LDLib code is carried.
 */
@Mixin(value = LDLMetadataSection.class, remap = false)
public abstract class LDLMetadataSectionMixin {
    @WrapOperation(method = "getMetadata(Lnet/minecraft/resources/ResourceLocation;)Lcom/lowdragmc/lowdraglib/client/model/custommodel/LDLMetadataSection;",
            require = 0, at = @At(value = "INVOKE", target = "Ljava/util/Optional;get()Ljava/lang/Object;", ordinal = 1))
    private static Object bons$sectionOrMissing(Optional<?> section, Operation<Object> original) {
        return LdlibMetadata.section(section, original);
    }
}
