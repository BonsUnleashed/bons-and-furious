package bons.furious.mixin.biomancy_coo49;

import bons.furious.patch.biomancy_coo49.SpatialMemo;
import com.github.elenterius.spatialdb.SDBI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * biomancy_spatial_query_memo, Biomancy 2.9.8.1-alpha.0 (the tested build; server): the ResourceLocation.toString() in
 * SDBI.getLevelKey answers from SpatialMemo. Offered to Mixin by BiomancyPlugin only when the guard matched 2.9.8.1.
 */
@Mixin(value = SDBI.class, remap = false)
public abstract class SdbiLevelKeyMixin {
    @WrapOperation(method = "getLevelKey", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;toString()Ljava/lang/String;"),
            require = 1, allow = 1)
    private String bons$keptLevelKey(ResourceLocation location, Operation<String> original) {
        return SpatialMemo.levelKey(location, original);
    }
}
