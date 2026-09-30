package bons.furious.mixin.valkyrienskies_core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.impl.shadow.AcVsTerrainUpdates;
import org.valkyrienskies.core.impl.shadow.Ip;
import org.valkyrienskies.core.util.datastructures.BlockPos2ObjectOpenHashMap;

/**
 * valkyrien_terrain_snapshot_order (Valkyrien Skies 2.4.11, VS core VSPhysicsPipelineStage, obfuscated as FN).
 *
 * Bug fix: ships could fall through freshly loaded terrain. The physics pipeline bakes terrain updates per chunk, and a
 * sparse update (a block change) that reached a chunk before the chunk's initial snapshot was baked onto an empty
 * chunk, so the native side believed the chunk was loaded and mostly air. Before applyGameFrame (FN.b) walks a game
 * frame's terrain updates, AcVsTerrainUpdates drops sparse updates for chunks of ground bodies whose full snapshot has
 * not been submitted yet (the snapshot already contains those changes) and records submitted snapshots and deletions.
 */
@Mixin(targets = "org.valkyrienskies.core.impl.shadow.FN", remap = false)
public abstract class TerrainSnapshotOrderMixin {
    /** The ground body id of each dimension (dimension id to body id), filled by the pipeline. */
    @Shadow
    @Final
    public Map<String, Long> n;

    /** Which chunks of which ground body have had a full snapshot submitted; one ledger per pipeline stage. */
    @Unique
    private final AcVsTerrainUpdates ac$terrainSnapshots = new AcVsTerrainUpdates();

    /** The game frame's terrain updates (VSGameFrame field d), filtered in place before the stage iterates them. */
    @SuppressWarnings("unchecked")
    @ModifyExpressionValue(method = "b(Lorg/valkyrienskies/core/impl/shadow/FG;)V",
            at = @At(value = "FIELD", target = "Lorg/valkyrienskies/core/impl/shadow/FG;d:Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;",
                    opcode = Opcodes.GETFIELD))
    private Long2ObjectMap<? extends BlockPos2ObjectOpenHashMap<Ip>> bons$dropDeltasBeforeSnapshot(
            Long2ObjectMap<? extends BlockPos2ObjectOpenHashMap<Ip>> terrainUpdates) {
        this.ac$terrainSnapshots.filter((Long2ObjectMap<BlockPos2ObjectOpenHashMap<Ip>>) terrainUpdates, this.n);
        return terrainUpdates;
    }
}
