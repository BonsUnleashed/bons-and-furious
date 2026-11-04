package bons.furious.mixin.fluidium_coo49;

import bons.furious.patch.fluidium_coo49.ClaimMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.kall.fluidium.common.integration.IChecker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fluidium_claim_lookup_memo (Fluidium 1.20.1-1.4.0 with FTB Chunks 2001.3.8, server; exact; no Fluidium code carried).
 * ClaimManager.isClaimed's call of the FTB Chunks checker (the first IChecker.isClaimed call; Open Parties and Claims' second
 * call is untouched) is answered from ClaimMemo's per-chunk table (4,096 entries) while the same FTB manager holds the same
 * claims.
 */
@Mixin(targets = "me.kall.fluidium.common.integration.ClaimManager", remap = false)
public abstract class ClaimLookupMemoMixin {
    @WrapOperation(method = "isClaimed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE", target = "Lme/kall/fluidium/common/integration/IChecker;isClaimed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
                    ordinal = 0), require = 1, allow = 1)
    private static boolean bons$fluidiumClaimMemo(IChecker checker, Level level, BlockPos pos, Operation<Boolean> original) {
        return ClaimMemo.claimed(checker, level, pos, original);
    }
}
