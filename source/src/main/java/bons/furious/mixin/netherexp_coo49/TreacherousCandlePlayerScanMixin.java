package bons.furious.mixin.netherexp_coo49;

import bons.furious.patch.netherexp_coo49.CandlePlayerScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * netherexp_candle_player_scan_lean (Jaden's Nether Expansion, CC BY-NC-ND 4.0: no code carried; 1.21.1 tested build: Jaden's
 * Nether Expansion 2.4.1; server): the Level.getEntitiesOfClass(ServerPlayer, box) in
 * CiergeOfTreacheryBlockEntity.getPlayersInRadius answers from CandlePlayerScan. See CandlePlayerScan.
 *
 * Ported to 1.21.1: 2.3.5's treacherous candle block entity is the Cierge of Treachery in 2.4.1; getPlayersInRadius is the
 * same search (new AABB(pos).inflate(16)) and its list still goes only to updateBossBarPlayers.
 */
@Mixin(targets = "net.jadenxgamer.netherexp.core.block.entity.CiergeOfTreacheryBlockEntity", remap = false)
public abstract class TreacherousCandlePlayerScanMixin {
    @WrapOperation(method = "getPlayersInRadius(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List<?> bons$playersFromList(Level level, Class<?> type, AABB box, Operation<List<?>> original) {
        return CandlePlayerScan.players(level, type, box, original);
    }
}
