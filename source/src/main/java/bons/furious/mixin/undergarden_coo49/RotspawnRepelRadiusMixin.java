package bons.furious.mixin.undergarden_coo49;

import bons.furious.patch.undergarden_coo49.UndergardenScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quek.undergarden.entity.monster.rotspawn.RotspawnMonster;

/**
 * undergarden_rotspawn_repel_player_radius (The Undergarden 0.9.6 for NeoForge 1.21.1, trade-off, off by default; both
 * sides): the BlockPos.findClosestMatch call in RotspawnMonster.aiStep, the every-40-ticks search of 9,801 positions for
 * hoglin repellents, answers empty without searching while no player is within R blocks of the rotspawn. What differs:
 * rotspawns with no player near do not flee repellents. See UndergardenScans.repelSearch.
 */
@Mixin(value = RotspawnMonster.class, remap = false)
public abstract class RotspawnRepelRadiusMixin {
    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;findClosestMatch(Lnet/minecraft/core/BlockPos;IILjava/util/function/Predicate;)Ljava/util/Optional;"),
            require = 1, allow = 1)
    private Optional<BlockPos> bons$undergardenCoo49RepelRadius(BlockPos center, int width, int height, Predicate<BlockPos> test,
                                                               Operation<Optional<BlockPos>> original) {
        return UndergardenScans.repelSearch((Entity) (Object) this, center, width, height, test, original);
    }
}
