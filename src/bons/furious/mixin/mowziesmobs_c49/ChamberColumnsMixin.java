package bons.furious.mixin.mowziesmobs_c49;

import bons.furious.patch.mowziesmobs_c49.ChamberColumns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mowziesmobs_chamber_column_memo (Mowzie's Mobs 1.7.3 / 1.8.2; server world generation; since 1.0.49): both
 * ChunkGenerator.getBaseColumn calls of WroughtnautChamberStructure.tryWroughtChamber share one column per (x, z) within
 * the call (ChamberColumns). Our own logic only.
 */
@Mixin(targets = "com.bobmowzie.mowziesmobs.server.world.feature.structure.WroughtnautChamberStructure", remap = false)
public abstract class ChamberColumnsMixin {
    @WrapOperation(method = "tryWroughtChamber",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;m_214184_(IILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/NoiseColumn;", remap = false),
            require = 2, allow = 2, remap = false)
    private static NoiseColumn bons$sharedColumn(ChunkGenerator generator, int x, int z, LevelHeightAccessor height, RandomState state,
                                                 Operation<NoiseColumn> original, @Share("bons$chamberColumns") LocalRef<ChamberColumns.Memo> memo) {
        return ChamberColumns.column(generator, x, z, height, state, original, memo);
    }
}
