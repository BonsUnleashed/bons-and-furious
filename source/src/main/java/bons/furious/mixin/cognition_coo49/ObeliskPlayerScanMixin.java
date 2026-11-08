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
 * cognition_player_scan_lean (Cognition, MIT; 1.21.1 tested build: Cognition 2.4.13 for NeoForge 1.21.1; server; kind
 * change, off by default; no Cognition code carried). ExperienceObeliskEntity.checkAroundForMemorized's
 * getEntitiesOfClass(Player.class, its radius box) is answered from the level's player list (same per-player test,
 * player-list order). See PlayerScan.
 *
 * Ported to 1.21.1: checkAroundForMemorized (called by the server tick while the obelisk has space, acting every 20th game
 * tick) keeps its single Level.getEntitiesOfClass(Class, AABB) call and serves the first listed player who is alive, has
 * a linked memory tablet and experience to recover.
 */
@Mixin(targets = "com.cyanogen.cognition.block_entities.ExperienceObeliskEntity", remap = false)
public abstract class ObeliskPlayerScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "checkAroundForMemorized()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$cognitionObeliskPlayers(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return PlayerScan.players(level, searched, box, original);
    }
}
