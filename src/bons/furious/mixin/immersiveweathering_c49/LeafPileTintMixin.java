package bons.furious.mixin.immersiveweathering_c49;

import bons.furious.patch.immersiveweathering_c49.LeafTint;
import com.ordana.immersive_weathering.ImmersiveWeatheringClient;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.mehvahdjukaar.moonlight.api.set.leaves.LeavesType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * immersiveweathering_leaf_pile_tint_memo (Immersive Weathering 1.20.1-2.0.5, LGPLv3; client side): a cancellable HEAD
 * inject on ImmersiveWeatheringClient.getLeafTypeColor answers from LeafTint (the same leaves colour lookup, the mixed
 * colour from a table filled by Immersive Weathering's own expression).
 */
@Mixin(value = ImmersiveWeatheringClient.class, remap = false)
public abstract class LeafPileTintMixin {
    @Inject(method = "getLeafTypeColor(Lnet/mehvahdjukaar/moonlight/api/platform/ClientHelper$BlockColorEvent;Lnet/mehvahdjukaar/moonlight/api/set/leaves/LeavesType;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;I)I",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$iwC49LeafTint(ClientHelper.BlockColorEvent event, LeavesType type, BlockState state, BlockAndTintGetter level,
                                           BlockPos pos, int i, CallbackInfoReturnable<Integer> cir) {
        if (LeafTint.enabled) cir.setReturnValue(LeafTint.color(event, type, state, level, pos, i));
    }
}
