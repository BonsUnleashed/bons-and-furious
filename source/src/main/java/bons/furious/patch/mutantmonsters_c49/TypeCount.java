package bons.furious.patch.mutantmonsters_c49;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * mutantmonsters_spawn_limit_index (since 1.0.49): per EntityLookup (created on the first limited spawn of that level), the
 * entities of byId grouped by entity type, for the types a limited spawn asked about, plus every entity whose class has
 * its own getType (those are handed to every query; Mutant Monsters' own filter then tests their type).
 *
 * Kept in step by EntityLookupTypeCountMixin, which reads byId before and after each add and remove: the entity that left
 * the id is taken out, the entity that now holds it is put in. A query first compares the number of entries it has seen
 * with byId's size and rebuilds everything from byId when they differ (something changed byId another way). A type asked
 * about for the first time is filled from byId once. Ours; no Minecraft or Mutant Monsters code.
 */
public final class TypeCount {
    private static final java.lang.invoke.MethodType GET_TYPE = java.lang.invoke.MethodType.methodType(EntityType.class);
    /**
     * True for an entity class that answers getType itself: resolved from the class, the method is declared below Entity
     * (a mixin-added override included), or the class cannot be asked, which counts as "its own" (the careful side: such an entity
     * is handed to every query and Mutant Monsters' own filter tests its type). Asked through the JVM's resolution of that one
     * method (bons.furious.guard.Declared), not Class.getDeclaredMethods: the listing resolves the types of every method the class
     * declares, and an entity class with a method naming a class the game cannot load (a client-only class on a dedicated server, a
     * class of a mod that is not installed) made it throw out of the entity tracker's add / remove, which stops the server (bug
     * sweep 2026-11-12).
     */
    private static final ClassValue<Boolean> OWN_TYPE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            return !bons.furious.guard.Declared.inherits(c, Entity.class, "getType", GET_TYPE);
        }
    };

    private final Reference2ObjectOpenHashMap<EntityType<?>, ReferenceOpenHashSet<Object>> byType = new Reference2ObjectOpenHashMap<>();
    private final ReferenceOpenHashSet<Object> own = new ReferenceOpenHashSet<>();
    private int seen;
    private boolean built;

    static boolean ownType(Object o) {
        return !(o instanceof Entity) || OWN_TYPE.get(o.getClass());
    }

    /** byId now maps the id to `after` where it mapped `before` (either may be null). */
    public void changed(Object before, Object after) {
        if (!this.built) return;
        if (before == after) return;
        if (before != null) {
            this.seen--;
            if (ownType(before)) this.own.remove(before);
            else {
                ReferenceOpenHashSet<Object> set = this.byType.get(((Entity) before).getType());
                if (set != null) set.remove(before);
            }
        }
        if (after != null) {
            this.seen++;
            if (ownType(after)) this.own.add(after);
            else {
                ReferenceOpenHashSet<Object> set = this.byType.get(((Entity) after).getType());
                if (set != null) set.add(after);
            }
        }
    }

    private void rebuild(Int2ObjectMap<?> byId) {
        this.own.clear();
        for (ReferenceOpenHashSet<Object> set : this.byType.values()) set.clear();
        for (Object o : byId.values()) {
            if (ownType(o)) this.own.add(o);
            else {
                ReferenceOpenHashSet<Object> set = this.byType.get(((Entity) o).getType());
                if (set != null) set.add(o);
            }
        }
        this.seen = byId.size();
        this.built = true;
    }

    /** The entities a limited spawn of this type must count: every entity of byId of that type, plus the own-type ones. */
    public Iterable<Object> query(EntityType<?> type, Int2ObjectMap<?> byId) {
        if (!this.built || this.seen != byId.size()) rebuild(byId);
        ReferenceOpenHashSet<Object> set = this.byType.get(type);
        if (set == null) {
            set = new ReferenceOpenHashSet<>();
            for (Object o : byId.values()) if (!ownType(o) && ((Entity) o).getType() == type) set.add(o);
            this.byType.put(type, set);
        }
        ReferenceOpenHashSet<Object> typed = set, others = this.own;
        if (others.isEmpty()) return typed;
        return () -> new Iterator<>() {
            final Iterator<Object> a = typed.iterator(), b = others.iterator();

            @Override
            public boolean hasNext() {
                return this.a.hasNext() || this.b.hasNext();
            }

            @Override
            public Object next() {
                if (this.a.hasNext()) return this.a.next();
                if (this.b.hasNext()) return this.b.next();
                throw new NoSuchElementException();
            }
        };
    }

    /** Harness view. */
    public int sizeOf(EntityType<?> type) {
        ReferenceOpenHashSet<Object> set = this.byType.get(type);
        return set == null ? -1 : set.size();
    }
}
