package bons.furious.mixin.skyarena_coo49;

import bons.furious.patch.skyarena_coo49.SkyarenaSpawnScan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * skyarena_spawn_scan_lean (Sky Arena 1.3.6, MIT; server: the altar's ticker and its use run only on server levels).
 *
 * AltarBlockEntity.findValidSpawnPositions sweeps every column of the square of radius mobSpawnRadius around the altar
 * (circle test first) and asks the level for up to eight blocks per column, building a new BlockPos for the column
 * (center.offset) and one more for every neighbour it asks about (above, above(2), north, south, east, west, below; in
 * the water-and-air branch each neighbour twice, for the state and for its collision shape). It runs once per stray mob
 * per tick during a battle and once at the wave start.
 *
 * What changes: @Inject at HEAD (cancellable) runs the same sweep with one MutableBlockPos: the same squares in the same
 * order, the same circle test, the same block questions in the same order through the same level methods (isEmptyBlock,
 * getBlockState + getCollisionShape(level, pos).isEmpty()), stopping at the same first failing test, then the same player
 * distance test (player.blockPosition().distSqr(column) against spawnDistanceFromPlayer squared), and every valid column
 * is added as an immutable copy. The three config fields are read exactly where the original reads them (loop bounds,
 * circle test, distance test). Why identical: the level sees the same calls with equal positions in the same order (so
 * the same chunk loads, tickets and caches), the list holds equal positions in the same order, and its callers
 * (updateSummonedMobs, AltarBlock.use, the auto wave) only read isEmpty, size and get(random). The only internal
 * difference: the column at the altar itself (offset 0, 0) is a copy instead of the center object; it is never valid
 * (the altar block is neither air nor collision-free).
 *
 * Shadow mode (-Dbons_and_furious.skyarenaSpawnScanLean.shadow=true): the original runs too and the lists are compared at
 * RETURN (SkyarenaSpawnScan.SHADOW_CHECKS / SHADOW_MISMATCHES). Our own logic only.
 */
@Mixin(targets = "net.jrdemiurge.skyarena.block.entity.AltarBlockEntity", remap = false)
public abstract class AltarSpawnScanLeanMixin {
    @Shadow
    private int mobSpawnRadius;
    @Shadow
    private int spawnDistanceFromPlayer;
    @Shadow
    private boolean allowWaterAndAirSpawn;

    @Inject(method = "findValidSpawnPositions(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$skyarenaCoo49Lean(Level level, BlockPos center, Player player, CallbackInfoReturnable<List<BlockPos>> cir) {
        if (!SkyarenaSpawnScan.enabled) return;
        List<BlockPos> ours = this.bons$skyarenaCoo49Sweep(level, center, player);
        if (SkyarenaSpawnScan.SHADOW) {
            SkyarenaSpawnScan.keep(ours);
            return;
        }
        SkyarenaSpawnScan.announce();
        cir.setReturnValue(ours);
    }

    @Inject(method = "findValidSpawnPositions(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;",
            at = @At("RETURN"), require = 1)
    private void bons$skyarenaCoo49LeanShadow(Level level, BlockPos center, Player player, CallbackInfoReturnable<List<BlockPos>> cir) {
        if (SkyarenaSpawnScan.SHADOW) SkyarenaSpawnScan.compare(cir.getReturnValue());
    }

    /** The original's sweep with one MutableBlockPos (see the class comment). */
    @Unique
    private List<BlockPos> bons$skyarenaCoo49Sweep(Level level, BlockPos center, Player player) {
        List<BlockPos> valid = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cx = center.m_123341_(), cy = center.m_123342_(), cz = center.m_123343_();
        if (!this.allowWaterAndAirSpawn) {
            for (int x = -this.mobSpawnRadius; x <= this.mobSpawnRadius; ++x) {
                for (int z = -this.mobSpawnRadius; z <= this.mobSpawnRadius; ++z) {
                    int px = cx + x, pz = cz + z;
                    if (x * x + z * z <= this.mobSpawnRadius * this.mobSpawnRadius
                            && level.m_46859_(m.m_122178_(px, cy, pz))
                            && level.m_46859_(m.m_122178_(px, cy + 1, pz))
                            && level.m_46859_(m.m_122178_(px, cy + 2, pz))
                            && level.m_46859_(m.m_122178_(px, cy, pz - 1))
                            && level.m_46859_(m.m_122178_(px, cy, pz + 1))
                            && level.m_46859_(m.m_122178_(px + 1, cy, pz))
                            && level.m_46859_(m.m_122178_(px - 1, cy, pz))
                            && !level.m_46859_(m.m_122178_(px, cy - 1, pz))
                            && player.m_20183_().m_123331_(m.m_122178_(px, cy, pz)) > (double) (this.spawnDistanceFromPlayer * this.spawnDistanceFromPlayer)) {
                        valid.add(m.m_7949_());
                    }
                }
            }
            return valid;
        }
        for (int x = -this.mobSpawnRadius; x <= this.mobSpawnRadius; ++x) {
            for (int z = -this.mobSpawnRadius; z <= this.mobSpawnRadius; ++z) {
                int px = cx + x, pz = cz + z;
                if (x * x + z * z <= this.mobSpawnRadius * this.mobSpawnRadius
                        && SkyarenaSpawnScan.open(level, m.m_122178_(px, cy, pz))
                        && SkyarenaSpawnScan.open(level, m.m_122178_(px, cy + 1, pz))
                        && SkyarenaSpawnScan.open(level, m.m_122178_(px, cy + 2, pz))
                        && SkyarenaSpawnScan.open(level, m.m_122178_(px, cy, pz - 1))
                        && SkyarenaSpawnScan.open(level, m.m_122178_(px, cy, pz + 1))
                        && SkyarenaSpawnScan.open(level, m.m_122178_(px + 1, cy, pz))
                        && SkyarenaSpawnScan.open(level, m.m_122178_(px - 1, cy, pz))
                        && player.m_20183_().m_123331_(m.m_122178_(px, cy, pz)) > (double) (this.spawnDistanceFromPlayer * this.spawnDistanceFromPlayer)) {
                    valid.add(m.m_7949_());
                }
            }
        }
        return valid;
    }
}
