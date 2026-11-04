package bons.furious.mixin.netherexp_coo49;

import bons.furious.patch.netherexp_coo49.CandlePlayerScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.jadenxgamer.netherexp.registry.block.entity.TreacherousCandleBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * netherexp_candle_player_scan_lean (Jaden's Nether Expansion 2.3.5, server): the Level.getEntitiesOfClass(ServerPlayer,
 * box) in TreacherousCandleBlockEntity.getPlayersInRadius answers from CandlePlayerScan. See CandlePlayerScan.
 */
@Mixin(value = TreacherousCandleBlockEntity.class, remap = false)
public abstract class TreacherousCandlePlayerScanMixin {
    @WrapOperation(method = "getPlayersInRadius", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List<?> bons$playersFromList(Level level, Class<?> type, AABB box, Operation<List<?>> original) {
        return CandlePlayerScan.players(level, type, box, original);
    }
}
