package bons.furious.patch.iceandfire_dens_c49;

import com.github.alexthe666.iceandfire.world.gen.WorldGenDragonCave;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * iceandfire_structure_dens (since 1.0.49): one dragon cave, generated once by Ice and Fire's own generator against a
 * DenLevel view and kept per chunk, so every chunk of the cave writes exactly its part. The last few caves stay cached (a
 * cave's chunks are decorated close together); a cave asked for again after it left the cache is generated again from the
 * same seed, and since the view does not read the world it is the same cave.
 */
final class DenResult {
    private static final int CACHED = 4;
    private static final Object LOCK = new Object();
    private static final Map<Key, DenResult> CACHE = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, DenResult> eldest) {
            return size() > CACHED;
        }
    };

    record Key(ResourceKey<Level> dimension, DenKind kind, long seed, long origin) {
    }

    /** One chunk's writes: packed positions, final states and Ice and Fire's flags, in index order. */
    final Long2ObjectMap<int[]> byChunk = new Long2ObjectOpenHashMap<>();
    final long[] positions;
    final BlockState[] states;
    final byte[] flags;
    final Long2ObjectMap<DenLevel.Chest> chests;
    final List<Entity> entities;

    private DenResult(DenLevel view) {
        int n = view.states.size();
        positions = new long[n];
        states = new BlockState[n];
        flags = new byte[n];
        Long2ObjectMap<IntArrayList> lists = new Long2ObjectOpenHashMap<>();
        int i = 0;
        for (Long2ObjectMap.Entry<BlockState> e : view.states.long2ObjectEntrySet()) {
            long pos = e.getLongKey();
            positions[i] = pos;
            states[i] = e.getValue();
            flags[i] = view.flags.get(pos);
            lists.computeIfAbsent(ChunkPos.m_45589_(BlockPos.m_121983_(pos) >> 4, BlockPos.m_122015_(pos) >> 4), k -> new IntArrayList()).add(i);
            i++;
        }
        for (Long2ObjectMap.Entry<IntArrayList> e : lists.long2ObjectEntrySet()) byChunk.put(e.getLongKey(), e.getValue().toIntArray());
        chests = new Long2ObjectOpenHashMap<>(view.chests);
        entities = new ArrayList<>(view.entities);
    }

    static DenResult get(DenPiece piece, WorldGenLevel level, ChunkGenerator generator) {
        Key key = new Key(level.m_6018_().m_46472_(), piece.kind, piece.seed, piece.origin.m_121878_());
        synchronized (LOCK) {
            DenResult r = CACHE.get(key);
            if (r == null) {
                r = compute(piece.kind, piece.seed, piece.origin, level, generator);
                CACHE.put(key, r);
            }
            return r;
        }
    }

    /** Harness only: forget every cached den. */
    static void clearCache() {
        synchronized (LOCK) {
            CACHE.clear();
        }
    }

    /** Generates the whole cave once, with Ice and Fire's generator and the cave's own random source. */
    static DenResult compute(DenKind kind, long seed, BlockPos origin, WorldGenLevel level, ChunkGenerator generator) {
        DenLevel view = DenLevel.over(level);
        RandomSource random = new LegacyRandomSource(seed);
        WorldGenDragonCave feature = (WorldGenDragonCave) kind.feature();
        // Ice and Fire's draws in its order: gender, the depth (already applied to the origin), age, radius
        boolean male = random.m_188499_();
        random.m_188503_(30);
        int age = 75 + random.m_188503_(50);
        int radius = (int) ((float) age * 0.2f) + random.m_188503_(4);
        synchronized (feature) {
            feature.isMale = male;   // read by its setGoldPile
            feature.generateCave(view.view, radius, 3, origin, random);
        }
        int variant = random.m_188503_(4);
        float yaw = random.m_188501_() * 360.0f;
        view.view.m_7967_(Dens.dragons.make(kind, level.m_6018_(), male, age, variant,
                origin.m_123341_() + 0.5, origin.m_123342_() + 0.5, origin.m_123343_() + 0.5, yaw, origin));
        return new DenResult(view);
    }

    /**
     * Writes this cave's part inside the chunk box: every block (a block entity or an unbreakable block the chunk holds
     * there stays: Ice and Fire's own shell and hollow leave them, and the cave was generated without seeing them), then
     * each chest's loot table, then the entities standing in it.
     */
    void commit(WorldGenLevel level, BoundingBox box) {
        int[] here = byChunk.get(ChunkPos.m_45589_(box.m_162395_() >> 4, box.m_162398_() >> 4));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (here != null) {
            for (int i : here) {
                long pos = positions[i];
                p.m_122178_(BlockPos.m_121983_(pos), BlockPos.m_122008_(pos), BlockPos.m_122015_(pos));
                if (!box.m_71051_(p)) continue;
                BlockState old = level.m_8055_(p);
                if (old.m_60734_() instanceof BaseEntityBlock || old.m_60800_(level, p) < 0.0f) continue;
                level.m_7731_(p, states[i], flags[i]);
                DenLevel.Chest chest = chests.get(pos);
                if (chest != null && states[i].m_60734_() instanceof ChestBlock && chest.table() != null
                        && level.m_7702_(p) instanceof RandomizableContainerBlockEntity container) {
                    container.m_59626_(chest.table(), chest.seed());
                }
            }
        }
        for (Entity e : entities) {
            if (box.m_71051_(e.m_20183_())) level.m_7967_(e);
        }
    }

    /** Every write's position (harness: all of them must lie inside the piece's box). */
    long[] positions() {
        return positions;
    }

    ResourceLocation tableAt(long pos) {
        DenLevel.Chest c = chests.get(pos);
        return c == null ? null : c.table();
    }
}
