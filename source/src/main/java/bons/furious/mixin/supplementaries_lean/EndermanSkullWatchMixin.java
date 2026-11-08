package bons.furious.mixin.supplementaries_lean;

import bons.furious.patch.supplementaries_lean.SkullWatch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * supplementaries_skull_watch_precheck (Supplementaries 1.21.1-4.0.2, server side incl. the integrated server):
 * EndermanSkullBlockTile.isBeingWatched, its player.pick(64, 1, false) (Entity.pick, owner Player in the bytecode).
 * SkullWatch answers with a miss at the ray's end when the walked segment cannot touch the skull's cell and every chunk
 * on the way is loaded (the caller then moves on to the next player, as it does for any hit elsewhere); otherwise the real
 * pick runs. The players box, the fake enderman's lookAt and the mask test before it run unchanged, in order. No
 * Supplementaries code is carried; the wrapper decides only whether the mod's unchanged call runs.
 */
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.block.tiles.EndermanSkullBlockTile", remap = false)
public abstract class EndermanSkullWatchMixin {
    @WrapOperation(method = "isBeingWatched", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private static HitResult bons$watchRay(Player player, double distance, float partialTicks, boolean fluids, Operation<HitResult> original,
                                           @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return SkullWatch.pick(player, distance, partialTicks, fluids, original, level, pos);
    }
}
