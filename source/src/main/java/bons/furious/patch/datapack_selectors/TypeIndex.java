package bons.furious.patch.datapack_selectors;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * vanilla_selector_type_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides; built only for an EntityLookup that a
 * type-first entity selector scans, i.e. a server level's). A mirror of EntityLookup.byId grouped by entity type.
 *
 * EntityLookup.byId is an Int2ObjectLinkedOpenHashMap: its iteration order is the order in which the ids now present were
 * put as new keys (put of a new key appends, put of a present key keeps the slot, remove unlinks, rehash keeps the order).
 * The index gives every new key a sequence number from a counter and keeps one list per entity type, in sequence order,
 * plus one list ("own") for entities whose class has its own isAlive, getHealth or getType (those must always reach the
 * selector's chain, see SelectorPrefilter). A scan for a type-first selector walks only the lists whose type the first
 * option accepts plus the own list, merged by sequence number: the entities of byId's iteration that the scan's type test
 * lets through, in byId's order. Nothing is cached about the entities themselves; the type test is evaluated per type at
 * scan time (current tags).
 *
 * EntityLookupMixin keeps the index in step from outside add and remove (it reads byId before and after each call): a new
 * key appends, a removed key unlinks; anything else (a present id given another entity, a size change that does not match)
 * marks the index dirty, and the next scan rebuilds it from byId in byId's order.
 *
 * Ported to 1.21.1: unchanged (EntityLookup's byId, add, remove and getEntities are the same code as on 1.20.1).
 */
public final class TypeIndex {
    static final class Node {
        final Object entity;
        final int id;
        final long seq;
        Node prev, next;
        Bucket bucket;
        boolean live = true;

        Node(Object entity, int id, long seq) {
            this.entity = entity;
            this.id = id;
            this.seq = seq;
        }
    }

    static final class Bucket {
        final EntityType<?> type;   // null for the own list
        Node head, tail;
        int size;

        Bucket(EntityType<?> type) {
            this.type = type;
        }
    }

    private final Int2ObjectOpenHashMap<Node> nodes = new Int2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<EntityType<?>, Bucket> byType = new Reference2ObjectOpenHashMap<>();
    private final java.util.ArrayList<Bucket> typeBuckets = new java.util.ArrayList<>();   // byType's values, walked without an iterator
    private final Bucket own = new Bucket(null);
    private long nextSeq;
    private boolean dirty;

    public static TypeIndex build(Int2ObjectMap<?> byId) {
        TypeIndex index = new TypeIndex();
        index.fill(byId);
        return index;
    }

    public void markDirty() {
        this.dirty = true;
    }

    /** Before a scan: rebuild from byId when the index lost step with it. */
    public void refresh(Int2ObjectMap<?> byId) {
        if (this.dirty || this.nodes.size() != byId.size()) {
            this.nodes.clear();
            this.byType.clear();
            this.typeBuckets.clear();
            this.own.head = this.own.tail = null;
            this.own.size = 0;
            this.dirty = false;
            fill(byId);
        }
    }

    private void fill(Int2ObjectMap<?> byId) {
        for (Int2ObjectMap.Entry<?> e : byId.int2ObjectEntrySet()) {
            append(e.getIntKey(), e.getValue());
        }
    }

    /** byId got a new key (appended at the end of its order). */
    public void append(int id, Object entity) {
        Node node = new Node(entity, id, this.nextSeq++);
        Node old = this.nodes.put(id, node);
        if (old != null) {
            unlink(old);
            this.dirty = true;
        }
        Bucket bucket;
        if (entity instanceof Entity e && !SelectorPrefilter.ownChecks(e.getClass())) {
            bucket = this.byType.get(e.getType());
            if (bucket == null) {
                bucket = new Bucket(e.getType());
                this.byType.put(e.getType(), bucket);
                this.typeBuckets.add(bucket);
            }
        } else {
            bucket = this.own;
        }
        node.bucket = bucket;
        node.prev = bucket.tail;
        if (bucket.tail == null) bucket.head = node;
        else bucket.tail.next = node;
        bucket.tail = node;
        bucket.size++;
    }

    /** byId lost the key. */
    public void remove(int id) {
        Node node = this.nodes.remove(id);
        if (node == null) {
            this.dirty = true;
            return;
        }
        unlink(node);
    }

    private static void unlink(Node node) {
        Bucket b = node.bucket;
        if (node.prev == null) b.head = node.next;
        else node.prev.next = node.next;
        if (node.next == null) b.tail = node.prev;
        else node.next.prev = node.prev;
        b.size--;
        node.live = false;   // a cursor standing on it moves on through its next pointer, which stays as it was
    }

    /**
     * Hands the consumer, in byId's order, every entity of the own list and of the lists whose type the filter accepts;
     * stops when the consumer says abort (as EntityLookup.getEntities does).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void forEach(SelectorPrefilter.TypeFirstOption filter, AbortableIterationConsumer consumer) {
        java.util.ArrayList<Bucket> types = this.typeBuckets;
        int count = types.size();
        // the list cursors live in a reused array (a fresh one if a consumer starts another walk of this index)
        boolean reuse = !this.walking;
        Node[] heap = reuse && this.scratch.length > count ? this.scratch : new Node[count + 1];
        int size = 0;
        if (this.own.size > 0) heap[size++] = this.own.head;
        for (int i = 0; i < count; i++) {
            Bucket b = types.get(i);
            if (b.size > 0 && filter.matchesType(b.type)) heap[size++] = b.head;
        }
        if (size == 0) return;
        if (size == 1) {
            for (Node n = heap[0]; n != null; n = n.next) {
                if (n.live && consumer.accept(n.entity).shouldAbort()) break;
            }
            heap[0] = null;
            return;
        }
        int used = size;
        if (reuse) {
            this.scratch = heap;
            this.walking = true;
        }
        try {
            // k-way merge on the sequence numbers (binary min-heap of list cursors)
            for (int i = size / 2 - 1; i >= 0; i--) siftDown(heap, size, i);
            while (size > 0) {
                Node n = heap[0];
                if (n.live && consumer.accept(n.entity).shouldAbort()) return;
                Node next = n.next;
                while (next != null && !next.live) next = next.next;
                if (next != null) {
                    heap[0] = next;
                } else {
                    heap[0] = heap[--size];
                }
                if (size > 0) siftDown(heap, size, 0);
            }
        } finally {
            java.util.Arrays.fill(heap, 0, used, null);   // no entity stays reachable from the scratch array
            if (reuse) this.walking = false;
        }
    }

    private Node[] scratch = new Node[32];
    private boolean walking;

    private static void siftDown(Node[] heap, int size, int i) {
        Node x = heap[i];
        while (true) {
            int c = 2 * i + 1;
            if (c >= size) break;
            if (c + 1 < size && heap[c + 1].seq < heap[c].seq) c++;
            if (heap[c].seq >= x.seq) break;
            heap[i] = heap[c];
            i = c;
        }
        heap[i] = x;
    }
}
