package bons.furious.mixin.embeddium;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.util.concurrent.locks.StampedLock;
import org.embeddedt.embeddium.impl.render.vertex.serializers.VertexSerializerRegistryImpl;
import org.embeddedt.embeddium.api.vertex.serializer.VertexSerializer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * embeddium_serializer_lookup_snapshot (Embeddium 0.3.31+mc1.20.1, client).
 *
 * Every copy between two vertex formats (glyphs, entity cuboids, sprite expanders) looks up its serializer here, and
 * each lookup took and released the StampedLock read lock. Entries are only ever added (create() under the write lock,
 * and Oculus' four serializers written straight into the cache by its constructor mixin) and never replaced or
 * removed, so a hit can be answered from an immutable copy of the cache, published through a volatile field, without
 * the lock. A miss runs the original locked lookup, which also refreshes the copy when the cache has grown; the same
 * serializer object comes back either way.
 */
@Mixin(value = VertexSerializerRegistryImpl.class, remap = false)
public abstract class SerializerLookupSnapshotMixin {
    @Shadow
    @Final
    private Long2ReferenceMap<VertexSerializer> cache;

    @Shadow
    @Final
    private StampedLock lock;

    /** An immutable copy of the cache; never modified after it is published. */
    @Unique
    private volatile Long2ReferenceOpenHashMap<VertexSerializer> bons$snapshot;

    /**
     * @author BonsUnleashed
     * @reason Serve hits from an immutable snapshot; a miss runs the original locked lookup and refreshes the snapshot.
     */
    @Overwrite
    private VertexSerializer find(long identifier) {
        Long2ReferenceOpenHashMap<VertexSerializer> snapshot = this.bons$snapshot;
        if (snapshot != null) {
            VertexSerializer serializer = snapshot.get(identifier);
            if (serializer != null) {
                return serializer;
            }
        }
        long stamp = this.lock.readLock();
        try {
            VertexSerializer serializer = this.cache.get(identifier);
            if (snapshot == null || snapshot.size() != this.cache.size()) {
                this.bons$snapshot = new Long2ReferenceOpenHashMap<>(this.cache);
            }
            return serializer;
        } finally {
            this.lock.unlockRead(stamp);
        }
    }
}
