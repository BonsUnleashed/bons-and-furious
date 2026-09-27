package org.valkyrienskies.mod.common.util;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Pair;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3ic;
import org.valkyrienskies.core.internal.VsiCore;
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType;
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.entity.handling.VSEntityHandler;
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor;

/**
 * Fifth sweep hot paths. Every method reproduces the exact result of the VS 2.4.11
 * code it replaces; see the sweep report for the equivalence argument of each one.
 * No state survives a call except the entity-type handler map, whose value is a pure
 * function of the entity type/class and is what VS already memoized.
 */
public final class AcVsSweep5 {
    private AcVsSweep5() {}

    // ---- MixinServerLevel.postTick: newly ticketed chunks -------------------------------

    /**
     * Chunks that VS would load this tick: a direct ticket, a visible holder, a complete
     * ticking future and not yet known. The original scanned every visible holder; only
     * ticketed ones can pass, so iterating the ticket keys yields the same set.
     */
    public static List<LevelChunk> freshTicketedChunks(Long2ObjectOpenHashMap<?> tickets, Map<ChunkPos, ?> known,
        ChunkMapAccessor chunkMap) {
        if (tickets.isEmpty()) return Collections.emptyList();
        AcVsKnownChunks fast = known instanceof AcVsKnownChunks k ? k : null;
        List<LevelChunk> fresh = null;
        for (LongIterator it = tickets.keySet().iterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            if (fast != null ? fast.containsLong(pos) : known.containsKey(new ChunkPos(pos))) continue;
            ChunkHolder holder = chunkMap.callGetVisibleChunkIfPresent(pos);
            if (holder == null) continue;
            Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> state = holder.m_140026_().getNow(ChunkHolder.f_139997_);
            Optional<LevelChunk> chunk = state.left();
            if (chunk.isPresent()) {
                if (fresh == null) fresh = new ArrayList<>();
                fresh.add(chunk.get());
            }
        }
        return fresh == null ? Collections.emptyList() : fresh;
    }

    /** The original unload loop, iterating primitive keys instead of HashMap entries. */
    public static void unloadStale(Long2ObjectOpenHashMap<?> tickets, ChunkMapAccessor chunkMap,
        Map<ChunkPos, List<Vector3ic>> known, Long2LongOpenHashMap toUnload, List<VsiTerrainUpdate> updates) {
        if (known instanceof AcVsKnownChunks fast) {
            for (ObjectIterator<Long2ObjectMap.Entry<List<Vector3ic>>> it = fast.map.long2ObjectEntrySet().fastIterator();
                 it.hasNext(); ) {
                Long2ObjectMap.Entry<List<Vector3ic>> entry = it.next();
                long pos = entry.getLongKey();
                if (!tickets.containsKey(pos) || chunkMap.callGetVisibleChunkIfPresent(pos) == null) {
                    long waited = toUnload.getOrDefault(pos, 0L);
                    if (waited > 100L) {
                        deleteSections(entry.getValue(), updates);
                        it.remove();
                        toUnload.remove(pos);
                    } else {
                        toUnload.put(pos, waited + 1L);
                    }
                }
            }
            return;
        }
        for (Iterator<Map.Entry<ChunkPos, List<Vector3ic>>> it = known.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ChunkPos, List<Vector3ic>> entry = it.next();
            long pos = entry.getKey().m_45588_();
            if (!tickets.containsKey(pos) || chunkMap.callGetVisibleChunkIfPresent(pos) == null) {
                long waited = toUnload.getOrDefault(pos, 0L);
                if (waited > 100L) {
                    deleteSections(entry.getValue(), updates);
                    it.remove();
                    toUnload.remove(pos);
                } else {
                    toUnload.put(pos, waited + 1L);
                }
            }
        }
    }

    private static void deleteSections(List<Vector3ic> sections, List<VsiTerrainUpdate> updates) {
        for (Vector3ic section : sections) {
            updates.add(ValkyrienSkiesMod.getVsCore().newDeleteTerrainUpdate(section.x(), section.y(), section.z()));
        }
    }

    // ---- VSEntityManager.getDefaultHandler ------------------------------------------------

    private static final ConcurrentHashMap<EntityType<?>, VSEntityHandler> DEFAULT_HANDLERS = new ConcurrentHashMap<>();

    public static VSEntityHandler cachedDefaultHandler(EntityType<?> type) {
        return DEFAULT_HANDLERS.get(type);
    }

    /** First stored value wins, as with the Guava cache's single load per key. */
    public static VSEntityHandler storeDefaultHandler(EntityType<?> type, VSEntityHandler handler) {
        VSEntityHandler prior = DEFAULT_HANDLERS.putIfAbsent(type, handler);
        return prior != null ? prior : handler;
    }

    // ---- MixinGameRenderer.preRender ------------------------------------------------------

    /**
     * True when the per-frame interpolation body cannot change the entity: no vehicle
     * means getShipMountedToData returns null, and no last ship means no drag branch.
     */
    public static boolean skipRenderInterpolation(Entity entity) {
        return entity.m_20202_() == null
            && entity instanceof IEntityDraggingInformationProvider provider
            && provider.getDraggingInformation().getLastShipStoodOn() == null;
    }

    // ---- MixinChunkMap spawn-distance wrappers --------------------------------------------

    /**
     * The wrappers convert the chunk's middle block (x*16+8, 63, z*16+8) to world space and
     * back. Without a ship at that chunk the conversion is the identity on (x, z).
     */
    public static boolean spawnChunkOnShip(Level level, ChunkPos pos) {
        return VSGameUtilsKt.getShipManagingPos(level, pos.f_45578_, pos.f_45579_) != null;
    }

    // ---- VSGameUtilsKt.toDenseVoxelUpdate ------------------------------------------------

    public static VsiTerrainUpdate denseVoxelUpdate(LevelChunkSection section, Vector3ic chunkPos) {
        VsiTerrainUpdate.Builder update =
            ValkyrienSkiesMod.getVsCore().newDenseTerrainUpdateBuilder(chunkPos.x(), chunkPos.y(), chunkPos.z());
        fillDense(section, update);
        return update.build();
    }

    private static final int DENSE_SLOTS = 256, DENSE_MAX = 192;
    private static final ThreadLocal<Object[]> DENSE_KEYS = ThreadLocal.withInitial(() -> new Object[DENSE_SLOTS]);
    private static final ThreadLocal<VsiBlockType[]> DENSE_TYPES = ThreadLocal.withInitial(() -> new VsiBlockType[DENSE_SLOTS]);

    /**
     * Same x/y/z visiting order and addBlock calls as the original. Within one section each
     * distinct block state is resolved through BlockStateInfo once: the previous state is
     * checked first, then a small identity table cleared per section. The resolved type for a
     * state is fixed for the whole call, so reuse cannot change any addBlock argument.
     */
    public static void fillDense(LevelChunkSection section, VsiTerrainUpdate.Builder update) {
        BlockStateInfo.Cache info = BlockStateInfo.INSTANCE.getCache();
        Object[] keys = DENSE_KEYS.get();
        VsiBlockType[] types = DENSE_TYPES.get();
        Arrays.fill(keys, null);
        int size = 0;
        BlockState last = null;
        VsiBlockType lastType = null;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = section.m_62982_(x, y, z);
                    VsiBlockType type;
                    if (state == last && state != null) {
                        type = lastType;
                    } else {
                        type = null;
                        int slot = -1;
                        if (state != null) {
                            int i = System.identityHashCode(state) & (DENSE_SLOTS - 1);
                            for (Object k; (k = keys[i]) != null; i = (i + 1) & (DENSE_SLOTS - 1)) {
                                if (k == state) {
                                    type = types[i];
                                    break;
                                }
                            }
                            if (type == null) slot = i;
                        }
                        if (type == null) {
                            type = resolve(info, state);
                            if (slot >= 0 && size < DENSE_MAX) {
                                keys[slot] = state;
                                types[slot] = type;
                                size++;
                            }
                        }
                        last = state;
                        lastType = type;
                    }
                    update.addBlock(x, y, z, type);
                }
            }
        }
    }

    /** The original per-block expression: info.get(state)?.second ?: vsCore.blockTypes.air */
    private static VsiBlockType resolve(BlockStateInfo.Cache info, BlockState state) {
        Pair<Double, VsiBlockType> resolved = info.get(state);
        VsiBlockType type = resolved != null ? resolved.getSecond() : null;
        if (type == null) {
            VsiCore core = ValkyrienSkiesMod.getVsCore();
            type = core.getBlockTypes().getAir();
        }
        return type;
    }
}
