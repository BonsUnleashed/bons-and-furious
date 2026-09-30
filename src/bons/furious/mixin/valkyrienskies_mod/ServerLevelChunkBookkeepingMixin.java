package bons.furious.mixin.valkyrienskies_mod;

import com.bawnorton.mixinsquared.TargetHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.impl.config.VSCoreConfig;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.AcVsKnownChunks;
import org.valkyrienskies.mod.common.util.AcVsSweep5;
import org.valkyrienskies.mod.common.util.DragInfoReporter;
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor;
import org.valkyrienskies.mod.mixin.accessors.server.level.DistanceManagerAccessor;

/**
 * valkyrien_chunk_bookkeeping (Valkyrien Skies 2.4.11).
 *
 * At the end of every level tick, VS's server.world.MixinServerLevel.postTick scanned every visible chunk holder for
 * chunks to load into the ship world and walked a HashMap of known chunks to count down unloads. The 1.0.19 version
 * of that handler, reproduced here: only the chunks with a ticket are checked for loading
 * (AcVsSweep5.freshTicketedChunks, the only ones the full scan could accept), the known chunks live in a map keyed by
 * the chunk's long (AcVsKnownChunks, installed in place of VS's HashMap when the level is constructed), and the unload
 * countdown runs over those primitive keys (AcVsSweep5.unloadStale). The rest of the handler is VS's.
 *
 * The replacement is a HEAD injection into VS's handler that runs the whole 1.0.19 body and then returns (MixinSquared);
 * its CallbackInfo is one allocation per level and tick.
 */
@Mixin(value = ServerLevel.class, priority = 1500, remap = false)
public abstract class ServerLevelChunkBookkeepingMixin {
    @Shadow
    @Final
    private ServerChunkCache f_8547_;

    /**
     * VS's MixinServerLevel declares this field as {@code new HashMap<>()}. Declaring the same field here, in a mixin
     * of higher priority, adds no second field (Mixin keeps VS's, merged first) but adds this initializer, which Mixin
     * places in every constructor directly after VS's store to the same field. The field is final and the JVM allows
     * final-field stores only in the constructor, so the value has to be replaced there; wrapping VS's store moves it
     * out of the constructor, and an injector on {@code new HashMap()} may only return a HashMap.
     */
    private final Map<ChunkPos, List<Vector3ic>> vs$knownChunks = new AcVsKnownChunks();

    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.server.world.MixinServerLevel", name = "postTick")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void bons$postTick(BooleanSupplier shouldKeepTicking, CallbackInfo tick, CallbackInfo ci) {
        ServerLevelChunkBookkeepingAccess vs = (ServerLevelChunkBookkeepingAccess) this;
        ServerLevel self = ServerLevel.class.cast(this);
        VsiServerShipWorld shipObjectWorld = VSGameUtilsKt.getShipObjectWorld(self);
        ChunkMapAccessor chunkMapAccessor = (ChunkMapAccessor) this.f_8547_.f_8325_;
        ArrayList<VsiTerrainUpdate> voxelShapeUpdates = new ArrayList<>();
        DistanceManagerAccessor distanceManagerAccessor = (DistanceManagerAccessor) this.f_8547_.f_8325_.m_143145_();
        List<LevelChunk> freshChunks = AcVsSweep5.freshTicketedChunks(distanceManagerAccessor.getTickets(), vs.bons$knownChunks(), chunkMapAccessor);
        for (int i = 0; i < freshChunks.size(); ++i) {
            vs.bons$loadChunk(freshChunks.get(i), voxelShapeUpdates);
        }
        AcVsSweep5.unloadStale(distanceManagerAccessor.getTickets(), chunkMapAccessor, vs.bons$knownChunks(), vs.bons$chunksToUnload(), voxelShapeUpdates);
        shipObjectWorld.addTerrainUpdates(VSGameUtilsKt.getDimensionId(self), voxelShapeUpdates);
        if (VSCoreConfig.SERVER.getSp().getEnableSplitting()) {
            ValkyrienSkiesMod.splitHandler.tick(ServerLevel.class.cast(this));
        }
        DragInfoReporter.INSTANCE.tick((ServerLevel) (Object) this);
        ci.cancel();
    }
}
