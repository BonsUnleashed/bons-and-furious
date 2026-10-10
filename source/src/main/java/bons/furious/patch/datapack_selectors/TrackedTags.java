package bons.furious.patch.datapack_selectors;

import java.util.Iterator;

/**
 * vanilla_selector_tag_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides; installed by EntityTagsMixin at Entity
 * construction): the set behind Entity.tags (tags, final, written only by Entity's constructor). It IS a java.util.HashSet
 * built by the same no-argument constructor vanilla's Sets.newHashSet() uses, so contents, iteration order (NBT "Tags"
 * order, /tag list order), equals/hashCode and every read behave exactly as vanilla's set.
 *
 * The only addition: while the entity is in a level whose TypeIndex keeps tag lists (bound by TypeIndex.append), every
 * change of the set is reported to that index after it happened. HashSet's own mutators are add, remove, clear and its
 * iterator's remove; removeIf, removeAll, retainAll and addAll are inherited (Collection / AbstractSet /
 * AbstractCollection) and run through those four, so every mutating path of the Set interface is seen: Entity.addTag /
 * removeTag, Entity.load (clear + add) and any mod holding getTags(). TagIndex checks at start-up that the running JDK's
 * HashSet still declares none of the inherited ones itself (otherwise the key stands down). A clone is detached.
 * Carries no Minecraft code.
 *
 * Ported to 1.21.1: unchanged (Entity.addTag / removeTag / load use the tags set the same way).
 */
public final class TrackedTags extends java.util.HashSet<String> {
    private static final long serialVersionUID = 1L;
    /** The entity whose tags field holds this set (the constructor's receiver). */
    final transient Object owner;
    /** The index and node this set reports to while its entity is in that index's lookup (TypeIndex.bind / unbind). */
    transient TypeIndex index;
    transient TypeIndex.Node node;

    public TrackedTags(Object owner) {
        super();
        this.owner = owner;
    }

    @Override
    public boolean add(String tag) {
        boolean changed = super.add(tag);
        TypeIndex i = this.index;
        if (changed && i != null) i.tagAdded(this.node, tag);
        return changed;
    }

    @Override
    public boolean remove(Object tag) {
        boolean changed = super.remove(tag);
        TypeIndex i = this.index;
        if (changed && i != null && tag instanceof String s) i.tagRemoved(this.node, s);
        return changed;
    }

    @Override
    public void clear() {
        TypeIndex i = this.index;
        if (i != null && !isEmpty()) {
            for (Iterator<String> it = super.iterator(); it.hasNext(); ) i.tagRemoved(this.node, it.next());
        }
        super.clear();
    }

    @Override
    public Iterator<String> iterator() {
        Iterator<String> it = super.iterator();
        return new Iterator<>() {
            private String last;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public String next() {
                return this.last = it.next();
            }

            @Override
            public void remove() {
                it.remove();
                TypeIndex i = TrackedTags.this.index;
                if (i != null) i.tagRemoved(TrackedTags.this.node, this.last);
            }
        };
    }

    /** A copy keeps HashSet's layout (super.clone) but is detached: it belongs to no index and reports nothing. */
    @Override
    public Object clone() {
        TrackedTags copy = (TrackedTags) super.clone();
        copy.index = null;
        copy.node = null;
        return copy;
    }
}
