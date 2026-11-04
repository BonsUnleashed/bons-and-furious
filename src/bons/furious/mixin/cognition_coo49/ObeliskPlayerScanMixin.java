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
 * cognition_player_scan_lean (Cognition 2.4.12, server; kind change, off by default; no Cognition code carried).
 * ExperienceObeliskEntity.checkAroundForMemorized's getEntitiesOfClass(Player.class, its radius box) is answered from the
 * level's player list (same per-player test, player-list order). See PlayerScan.
 */
@Mixin(targets = "com.cyanogen.experienceobelisk.block_entities.ExperienceObeliskEntity", remap = false)
public abstract class ObeliskPlayerScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "checkAroundForMemorized()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$cognitionObeliskPlayers(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return PlayerScan.players(level, searched, box, original);
    }
}
