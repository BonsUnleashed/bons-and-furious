package bons.furious.mixin.cognition_coo49;

import bons.furious.patch.cognition_coo49.PlayerScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cognition_player_scan_lean (Cognition, MIT; 1.21.1 tested build: Cognition 2.4.13 for NeoForge 1.21.1; both sides; kind
 * change, off by default; no Cognition code carried). ExperienceFountainEntity.tick's getEntitiesOfClass(Player.class, the
 * box above the fountain) is answered from the level's player list (same per-player test). See PlayerScan.
 *
 * Ported to 1.21.1: the static tick keeps its single Level.getEntitiesOfClass(Class, AABB) call, of which only isEmpty()
 * is read.
 */
@Mixin(targets = "com.cyanogen.cognition.block_entities.ExperienceFountainEntity", remap = false)
public abstract class FountainPlayerScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Ljava/lang/Object;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$cognitionFountainPlayers(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return PlayerScan.players(level, searched, box, original);
    }
}
