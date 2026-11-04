package bons.furious.mixin.biomancy_coo49;

import bons.furious.patch.biomancy_coo49.SpatialMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * biomancy_spatial_query_memo, Biomancy 2.8.19.0 (not in this pack; string target, @Pseudo, offered by BiomancyPlugin only
 * when the guard matched 2.8.19.0; server): the ResourceLocation.toString() in SpatialShapeManager.getLevelKey answers from
 * SpatialMemo.
 */
@Pseudo
@Mixin(targets = "com.github.elenterius.biomancy.world.spatial.SpatialShapeManager", remap = false)
public abstract class SpatialManagerLevelKeyMixin {
    @WrapOperation(method = "getLevelKey", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;toString()Ljava/lang/String;"),
            require = 1, allow = 1)
    private static String bons$keptLevelKey(ResourceLocation location, Operation<String> original) {
        return SpatialMemo.levelKey(location, original);
    }
}
