package bons.furious.mixin.valkyrienskies_mod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.AcVsSweep6;

/**
 * valkyrien_standing_probe (Valkyrien Skies 2.4.11).
 *
 * VS's entity_collision MixinEntity calls getPosStandingOnFromShips from getOnPos and
 * getBlockPosBelowThatAffectsMyMovement, several times per moving entity and tick, and it queried the ships around the
 * probe point even when there are none. It now returns null at once when no ship's world box meets the unit box around
 * the probe (AcVsSweep6.noShipAroundStandingPos); otherwise it is VS's code unchanged. VS merges this private helper
 * into Entity under its own name, and this higher-priority mixin (1500 against 1000) declares the same method, which
 * Mixin's merge puts in place of VS's copy; VS's handlers keep calling it by name.
 */
@Mixin(value = Entity.class, priority = 1500, remap = false)
public abstract class EntityStandingProbeMixin {
    @Shadow
    private Level f_19853_;

    private BlockPos getPosStandingOnFromShips(Vector3dc blockPosInGlobal) {
        if (AcVsSweep6.noShipAroundStandingPos(this.f_19853_, blockPosInGlobal)) {
            return null;
        }
        // VS declares "final double radius = 0.5" and was compiled with local-variable debug info, which keeps the
        // store; this non-final local plus the constant below compiles to the same instructions without that flag.
        double radius = 0.5;
        AABBd testAABB = new AABBd(blockPosInGlobal.x() - 0.5, blockPosInGlobal.y() - 0.5, blockPosInGlobal.z() - 0.5,
                blockPosInGlobal.x() + 0.5, blockPosInGlobal.y() + 0.5, blockPosInGlobal.z() + 0.5);
        Iterable<Ship> intersectingShips = VSGameUtilsKt.getShipsIntersecting(this.f_19853_, testAABB);
        for (Ship ship : intersectingShips) {
            Vector3dc blockPosInLocal = ship.getTransform().getWorldToShip().transformPosition(blockPosInGlobal, new Vector3d());
            BlockPos blockPos = BlockPos.m_274561_(blockPosInLocal.x(), blockPosInLocal.y(), blockPosInLocal.z());
            BlockState blockState = this.f_19853_.m_8055_(blockPos);
            if (!blockState.m_60795_()) {
                return blockPos;
            }
            Vector3dc blockPosInLocal2 = ship.getTransform().getWorldToShip()
                    .transformPosition(new Vector3d(blockPosInGlobal.x(), blockPosInGlobal.y() - 1.0, blockPosInGlobal.z()));
            BlockPos blockPos2 = BlockPos.m_274561_(blockPosInLocal2.x(), blockPosInLocal2.y(), blockPosInLocal2.z());
            BlockState blockState2 = this.f_19853_.m_8055_(blockPos2);
            if (!blockState2.m_60795_()) {
                return blockPos2;
            }
        }
        return null;
    }
}
