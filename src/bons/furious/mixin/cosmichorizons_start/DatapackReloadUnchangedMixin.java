package bons.furious.mixin.cosmichorizons_start;

import bons.furious.patch.cosmichorizons_start.CosmosStart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.lointain.cosmos.network.CosmosModVariables;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.LevelAccessor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cosmichorizons_reload_unchanged_skip (Cosmic Horizons 0.0.7.3; both sides: the server and the integrated server).
 *
 * Three wrappers in TestRestartProcedure.DatapackReloadProcedure.reloadJsonObjects, around the three steps it runs for
 * every loaded dimension: the write of WorldVariables.datapack, syncData(level) and InitialiseregistryProcedure.execute
 * (level). While the server is starting, a dimension whose datapack list already equals the new list skips all three
 * (CosmosStart explains why that leaves its variables as they are, and checks it on the first dimensions of every
 * start). No Cosmic Horizons code is carried.
 */
@Mixin(targets = "net.lointain.cosmos.procedures.TestRestartProcedure$DatapackReloadProcedure", remap = false)
public abstract class DatapackReloadUnchangedMixin {
    @WrapOperation(method = "reloadJsonObjects", require = 1, allow = 1, at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/lointain/cosmos/network/CosmosModVariables$WorldVariables;datapack:Lnet/minecraft/nbt/ListTag;"))
    private static void bons$sameData(CosmosModVariables.WorldVariables vars, ListTag list, Operation<Void> original) {
        CosmosStart.assign(vars, list, original);
    }

    @WrapOperation(method = "reloadJsonObjects", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/lointain/cosmos/network/CosmosModVariables$WorldVariables;syncData(Lnet/minecraft/world/level/LevelAccessor;)V"))
    private static void bons$sameDataSync(CosmosModVariables.WorldVariables vars, LevelAccessor level, Operation<Void> original) {
        CosmosStart.sync(vars, level, original);
    }

    @WrapOperation(method = "reloadJsonObjects", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/lointain/cosmos/procedures/InitialiseregistryProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;)V"))
    private static void bons$sameDataInit(LevelAccessor level, Operation<Void> original) {
        CosmosStart.init(level, original);
    }
}
