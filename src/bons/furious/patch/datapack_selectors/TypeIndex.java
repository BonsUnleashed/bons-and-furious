package bons.furious.patch.datapack_selectors;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * vanilla_selector_type_index (Minecraft 1.20.1, both sides; built only for an EntityLookup that a type-first entity
 * selector scans, i.e. a server level's). A mirror of EntityLookup.byId grouped by entity type.
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
 * Since 1.0.36 (vanilla_selector_tag_index) the index also keeps, per tag a tag scan asked for (at most MAX_TAG_LISTS), the
 * entities holding that tag, keyed by sequence number (byId order), and a list of the entities every tag scan must visit
 * ("always": classes with their own isAlive/getHealth/getType/getTags, or an entity whose tag set is not a TrackedTags of
 * its own that this index could bind). A bound TrackedTags reports each change (tagAdded / tagRemoved); append binds,
 * unlink and rebuild unbind. A tag list is built on first use from the bound entities' sets and dropped with a rebuild.
 */
public final class TypeIndex {
    static final class Node {
        final Object entity;
        final int id;
        final long seq;
        Node prev, next;
        Bucket bucket;
        boolean live = true;
        // since 1.0.36 (vanilla_selector_tag_index)
        Node aprev, anext;      // links in the always-visited list of tag scans
        boolean tagAlways;
        TrackedTags tags;       // the bound tag set (null when tagAlways)

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

    /** Most tag lists one index keeps (a tag scan for a further tag takes the inner test's own path). */
    static final int MAX_TAG_LISTS = 256;

    private final Int2ObjectOpenHashMap<Node> nodes = new Int2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<EntityType<?>, Bucket> byType = new Reference2ObjectOpenHashMap<>();
    private final java.util.ArrayList<Bucket> typeBuckets = new java.util.ArrayList<>();   // byType's values, walked without an iterator
    private final Bucket own = new Bucket(null);
    private final Object2ObjectOpenHashMap<String, Long2ObjectAVLTreeMap<Node>> byTag = new Object2ObjectOpenHashMap<>();
    private Node alwaysHead, alwaysTail;
    private int alwaysSize;
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
            for (Node n : this.nodes.values()) unbind(n);
            this.nodes.clear();
            this.byType.clear();
            this.typeBuckets.clear();
            this.own.head = this.own.tail = null;
            this.own.size = 0;
            this.byTag.clear();
            this.alwaysHead = this.alwaysTail = null;
            this.alwaysSize = 0;
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
            bucket = this.byType.get(e.m_6095_());
            if (bucket == null) {
                bucket = new Bucket(e.m_6095_());
                this.byType.put(e.m_6095_(), bucket);
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
        bind(node);
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

    private void unlink(Node node) {
        Bucket b = node.bucket;
        if (node.prev == null) b.head = node.next;
        else node.prev.next = node.next;
        if (node.next == null) b.tail = node.prev;
        else node.next.prev = node.prev;
        b.size--;
        node.live = false;   // a cursor standing on it moves on through its next pointer, which stays as it was
        if (node.tagAlways) {
            if (node.aprev == null) this.alwaysHead = node.anext;
            else node.aprev.anext = node.anext;
            if (node.anext == null) this.alwaysTail = node.aprev;
            else node.anext.aprev = node.aprev;
            this.alwaysSize--;
        } else if (node.tags != null) {
            if (!this.byTag.isEmpty()) {
                for (String tag : node.tags) {
                    Long2ObjectAVLTreeMap<Node> list = this.byTag.get(tag);
                    if (list != null) list.remove(node.seq);
                }
            }
            unbind(node);
        }
    }

    /**
     * 1.0.36: the tag side of a new node. A plain entity whose tag set is its own unbound TrackedTags is bound (its changes
     * are reported from now on) and filed in the tag lists it belongs to; any other node is always visited by tag scans.
     */
    private void bind(Node node) {
        TrackedTags tracked = null;
        if (node.entity instanceof Entity e && !SelectorPrefilter.ownChecks(e.getClass()) && !TagIndex.ownTags(e.getClass())) {
            Set<String> set = e.m_19880_();
            if (set instanceof TrackedTags t && t.owner == e && t.index == null) tracked = t;
        }
        if (tracked == null) {
            node.tagAlways = true;
            node.aprev = this.alwaysTail;
            if (this.alwaysTail == null) this.alwaysHead = node;
            else this.alwaysTail.anext = node;
            this.alwaysTail = node;
            this.alwaysSize++;
            return;
        }
        tracked.index = this;
        tracked.node = node;
        node.tags = tracked;
        if (!this.byTag.isEmpty()) {
            for (String tag : tracked) {
                Long2ObjectAVLTreeMap<Node> list = this.byTag.get(tag);
                if (list != null) list.put(node.seq, node);
            }
        }
    }

    private static void unbind(Node node) {
        TrackedTags t = node.tags;
        if (t != null && t.node == node) {
            t.index = null;
            t.node = null;
        }
        node.tags = null;
    }

    /** A bound TrackedTags gained a tag. */
    void tagAdded(Node node, String tag) {
        if (node != null && node.live && !this.byTag.isEmpty()) {
            Long2ObjectAVLTreeMap<Node> list = this.byTag.get(tag);
            if (list != null) list.put(node.seq, node);
        }
    }

    /** A bound TrackedTags lost a tag. */
    void tagRemoved(Node node, String tag) {
        if (node != null && node.live && !this.byTag.isEmpty()) {
            Long2ObjectAVLTreeMap<Node> list = this.byTag.get(tag);
            if (list != null) list.remove(node.seq);
        }
    }

    /** The list of the bound entities holding the tag, built on first use (null when MAX_TAG_LISTS are kept already). */
    private Long2ObjectAVLTreeMap<Node> tagList(String tag) {
        Long2ObjectAVLTreeMap<Node> list = this.byTag.get(tag);
        if (list == null) {
            if (this.byTag.size() >= MAX_TAG_LISTS) return null;
            list = new Long2ObjectAVLTreeMap<>();
            for (Node n : this.nodes.values()) {
                if (n.tags != null && n.tags.contains(tag)) list.put(n.seq, n);
            }
            this.byTag.put(tag, list);
        }
        return list;
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
                if (n.live && consumer.m_260972_(n.entity).m_261146_()) break;
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
                if (n.live && consumer.m_260972_(n.entity).m_261146_()) return;
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

    /**
     * Since 1.0.36 (vanilla_selector_single_type): hands the consumer, in byId's order, the entities filed under the test's
     * type (their tryCast is known to pass: the type is final and their class's getType is Entity's) and the entities of the
     * own list for which the test's tryCast passes; stops when the consumer says abort.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void forEachSingle(SingleTypeTest test, AbortableIterationConsumer consumer) {
        Bucket typed = this.byType.get(test.type);
        Node a = this.own.size > 0 ? this.own.head : null, b = typed != null && typed.size > 0 ? typed.head : null;
        while (a != null || b != null) {
            while (a != null && !a.live) a = a.next;
            while (b != null && !b.live) b = b.next;
            if (a == null && b == null) return;
            if (b == null || a != null && a.seq < b.seq) {
                Node n = a;
                a = a.next;
                Entity e = test.m_141992_((Entity) n.entity);
                if (e != null && consumer.m_260972_(e).m_261146_()) return;
            } else {
                Node n = b;
                b = b.next;
                if (consumer.m_260972_(n.entity).m_261146_()) return;
            }
        }
    }

    /**
     * Since 1.0.36 (vanilla_selector_tag_index): answers a TagScanTest. Picks the smallest tag list among the selector's
     * usable tags; when the inner test is a type list (TypeFirstOption, SingleTypeTest) and that list, with the own list, is
     * smaller than the tag list with the always list, the type walk runs instead. Otherwise hands the consumer, in byId's
     * order, the entities of the tag list and of the always list for which the inner test's tryCast passes; stops when the
     * consumer says abort. Returns false (nothing done) when no tag list can be kept: the caller then runs the inner test's
     * own path.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean forEachTagged(TagIndex.TagScanTest test, AbortableIterationConsumer consumer, Int2ObjectMap<?> byId) {
        Long2ObjectAVLTreeMap<Node> best = null;
        String bestTag = null;
        for (String tag : test.tags) {
            Long2ObjectAVLTreeMap<Node> list = tagList(tag);
            if (list != null && (best == null || list.size() < best.size())) {
                best = list;
                bestTag = tag;
            }
        }
        if (best == null) return false;
        EntityTypeTest inner = test.inner;
        int tagCount = best.size() + this.alwaysSize;
        if (tagCount > 32) {
            if (inner instanceof SelectorPrefilter.TypeFirstOption type) {
                int typeCount = this.own.size;
                for (int i = 0, n = this.typeBuckets.size(); i < n; i++) {
                    Bucket b = this.typeBuckets.get(i);
                    if (b.size > 0 && type.matchesType(b.type)) typeCount += b.size;
                }
                if (typeCount < tagCount) {
                    TagIndex.TYPE_WALKS.incrementAndGet();
                    forEach(type, consumer);
                    return true;
                }
            } else if (inner instanceof SingleTypeTest single) {
                Bucket b = this.byType.get(single.type);
                if (this.own.size + (b == null ? 0 : b.size) < tagCount) {
                    TagIndex.TYPE_WALKS.incrementAndGet();
                    forEachSingle(single, consumer);
                    return true;
                }
            }
        }
        TagIndex.TAG_WALKS.incrementAndGet();
        // a TypeFirstOption stands in for the selector's own test, vanilla's ANY (an identity tryCast): its predicate is
        // in the chain already, so the walk hands the entities on as ANY would, without asking the option again
        boolean identity = inner instanceof SelectorPrefilter.TypeFirstOption;
        int k = best.size();
        boolean reuse = !this.walking;
        Node[] snap = reuse && this.scratch.length >= k ? this.scratch : new Node[Math.max(k, 1)];
        int i = 0;
        for (Node n : best.values()) snap[i++] = n;
        if (reuse) {
            this.scratch = snap;
            this.walking = true;
        }
        try {
            if (TagIndex.SHADOW) shadow(bestTag, snap, k, byId);
            Node a = this.alwaysHead;
            int j = 0;
            while (true) {
                while (a != null && !a.live) a = a.anext;
                while (j < k && !snap[j].live) j++;
                Node n;
                if (a == null) {
                    if (j >= k) return true;
                    n = snap[j++];
                } else if (j >= k || a.seq < snap[j].seq) {
                    n = a;
                    a = a.anext;
                } else {
                    n = snap[j++];
                }
                Object cast = identity ? n.entity : inner.m_141992_((Entity) n.entity);
                if (cast != null && consumer.m_260972_(cast).m_261146_()) return true;
            }
        } finally {
            java.util.Arrays.fill(snap, 0, k, null);   // no entity stays reachable from the scratch array
            if (reuse) this.walking = false;
        }
    }

    /**
     * Shadow mode: the tag walk's entities (always list merged with the snapshot of the tag list) against an independent
     * recount over byId: an entity of a class with its own isAlive/getHealth/getType/getTags, or whose tag set is not a
     * TrackedTags bound to this index for it, is always visited; any other is visited when its set holds the tag.
     */
    private void shadow(String tag, Node[] snap, int k, Int2ObjectMap<?> byId) {
        List<Object> expect = new ArrayList<>();
        for (Object o : byId.values()) {
            if (o instanceof Entity e && !SelectorPrefilter.ownChecks(e.getClass()) && !TagIndex.ownTags(e.getClass())) {
                Set<String> set = e.m_19880_();
                Node n = this.nodes.get(e.m_19879_());
                boolean bound = set instanceof TrackedTags t && t.index == this && n != null && t.node == n && n.entity == e;
                if (bound && !set.contains(tag)) continue;   // a bound plain entity without the tag: never visited
            }
            expect.add(o);
        }
        List<Object> walk = new ArrayList<>();
        Node a = this.alwaysHead;
        int j = 0;
        while (true) {
            while (a != null && !a.live) a = a.anext;
            while (j < k && !snap[j].live) j++;
            if (a == null && j >= k) break;
            if (a != null && (j >= k || a.seq < snap[j].seq)) {
                walk.add(a.entity);
                a = a.anext;
            } else {
                walk.add(snap[j++].entity);
            }
        }
        TagIndex.SHADOW_CHECKS.incrementAndGet();
        boolean same = expect.size() == walk.size();
        for (int i = 0; same && i < walk.size(); i++) same = expect.get(i) == walk.get(i);
        if (!same) TagIndex.mismatch("tag " + tag + ": the walk visits " + walk.size() + " entities, the recount over byId " + expect.size());
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
