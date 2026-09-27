package org.valkyrienskies.mod.common.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3ic;

/**
 * Replaces the HashMap behind MixinServerLevel.vs$knownChunks. Keys are stored as
 * ChunkPos.toLong(), which is a bijection of ChunkPos equality, so the Map view keeps
 * the original contract while the per-tick scan can test primitive keys.
 */
public final class AcVsKnownChunks extends AbstractMap<ChunkPos, List<Vector3ic>> {
    final Long2ObjectOpenHashMap<List<Vector3ic>> map = new Long2ObjectOpenHashMap<>();

    public boolean containsLong(long pos) {
        return map.containsKey(pos);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof ChunkPos pos && map.containsKey(pos.m_45588_());
    }

    @Override
    public List<Vector3ic> get(Object key) {
        return key instanceof ChunkPos pos ? map.get(pos.m_45588_()) : null;
    }

    @Override
    public List<Vector3ic> put(ChunkPos key, List<Vector3ic> value) {
        return map.put(key.m_45588_(), value);
    }

    @Override
    public List<Vector3ic> remove(Object key) {
        return key instanceof ChunkPos pos ? map.remove(pos.m_45588_()) : null;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<Map.Entry<ChunkPos, List<Vector3ic>>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return map.size();
            }

            @Override
            public Iterator<Map.Entry<ChunkPos, List<Vector3ic>>> iterator() {
                ObjectIterator<Long2ObjectMap.Entry<List<Vector3ic>>> it = map.long2ObjectEntrySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Map.Entry<ChunkPos, List<Vector3ic>> next() {
                        Long2ObjectMap.Entry<List<Vector3ic>> e = it.next();
                        return new AbstractMap.SimpleImmutableEntry<>(new ChunkPos(e.getLongKey()), e.getValue());
                    }

                    @Override
                    public void remove() {
                        it.remove();
                    }
                };
            }
        };
    }
}
