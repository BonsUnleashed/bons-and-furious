package bons.furious.patch.mahoutsukai_coo49;

import bons.furious.mixin.mahoutsukai_coo49.MahouGetterAdapterAccessor;
import bons.furious.mixin.mahoutsukai_coo49.MahouLevelEntitiesInvoker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch mahoutsukai_all_entities_memo (Mahou Tsukai 1.20.1-v1.34.81, All Rights Reserved; client; exact
 * variant, off by default since the 1.0.49 speed gate: the add / remove / setId bookkeeping costs those calls 0.86-0.95
 * and about 2 ns per id change). Ours; no Mahou Tsukai code is carried. Idea: Collections Of Optimizations (mahoutsukai.memoAllEntities), idea
 * text only; COO keeps the set for a tick, this keeps it exactly as long as the client's entity list is unchanged.
 *
 * ClientProxy.getAllEntities(ClientLevel) builds new HashSet<>(stream(level.getEntities().getAll()).collect(toSet())): two
 * hash sets of every entity of the client level. RenderUtils.entityRenders (every frame, from the world-render event) and
 * RenderSpatialDisorientationMahoujinEntity.spatialRender call it and only test the result for null, ask its size and
 * iterate it (bytecode; the set never leaves those methods). Both builds depend only on getAll()'s elements in their order
 * and on each element's hashCode (Entity.hashCode = the entity id).
 *
 * The memo (MahouAllEntitiesRenderMixin / MahouAllEntitiesSpatialMixin: MixinExtras @WrapOperation on those two calls):
 * the set built for the level's EntityLookup is handed out again while (1) that lookup saw no add or remove call since
 * (MahouEntityLookupMixin counts them; add and remove are the only writers of its maps, and getAll() iterates its
 * insertion-ordered id map), (2) no Entity.setId call happened anywhere since (MahouEntityIdMixin; an id change would move
 * an element to another bucket), and (3) every entity ever offered to that lookup's add has a class that hashes and
 * compares as Entity does (its id): a class with its own hashCode or equals could reorder or merge elements as its state
 * changes, so no set of such a lookup is ever kept. That rule costs the add path nothing while no such class exists:
 * Entity's constructor (MahouEntityIdMixin, at its RETURN) checks the new entity's class once per class (ClassValue) and
 * raises oddSeen for good at the first class with its own hashCode or equals; only from then on does each add check the
 * offered entity's class and mark its lookup. Every entity is constructed before it can be offered, so an add made while
 * oddSeen is down offers an entity whose class is plain. Under those conditions a fresh build would hold the same objects
 * in the same iteration order, so the callers draw exactly the same things. A build costs nothing beyond the original's
 * own work (no scan of the set). The one internal difference (exact variant): callers get
 * one set object per entity-list version instead of a new one per call; nothing they do can tell the two apart.
 *
 * It is used only when the level's entity getter is exactly the vanilla LevelEntityGetterAdapter over an exactly vanilla
 * EntityLookup, and those two classes carry no other mod's mixin (MixinMerged census, once); otherwise the original runs.
 * The set lives on the lookup, so it goes with its level. Runtime flag -Dbons_and_furious.mahoutsukaiAllEntitiesMemo=false;
 * SHADOW MODE (rigs): -Dbons_and_furious.mahoutsukaiAllEntitiesMemo.shadow=true always builds the set and checks that the
 * kept one would have held the same entities in the same order (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class MahouEntitySets {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "mahoutsukai_all_entities_memo";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mahoutsukaiAllEntitiesMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mahoutsukaiAllEntitiesMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Every Entity.setId call (any entity, any thread) moves this on. */
    static final AtomicInteger ID_CHANGES = new AtomicInteger();
    /** For probes and harnesses (plain fields): calls answered with the kept set, sets built. */
    public static long reused, built;
    private static volatile int clean;   // 0 = not checked yet, 1 = clean, 2 = another mod's mixin found
    private static volatile boolean announced;

    private MahouEntitySets() {
    }

    /** MahouEntityIdMixin: Entity.setId was called. */
    public static void idChanged() {
        ID_CHANGES.incrementAndGet();
    }

    /** Up for good once an entity whose class has its own hashCode or equals was constructed (any thread, any level). */
    private static volatile boolean oddSeen;

    /** MahouEntityIdMixin, at the RETURN of Entity's constructor: one class check per class until the first odd class. */
    public static void constructed(Entity entity) {
        if (!oddSeen && !PLAIN.get(entity.getClass())) {
            oddSeen = true;
            LOGGER.info("Bons and Furious: {}: {} has its own hashCode or equals; client entity lists that receive one keep no entity set from then on",
                    KEY, entity.getClass().getName());
        }
    }

    /** MahouEntityLookupMixin (every add): true once any entity class with its own hashCode or equals was constructed. */
    public static boolean oddSeen() {
        return oddSeen;
    }

    /** The call sites' wrapper: the kept set while the level's entity list is unchanged, else the original's new one. */
    @SuppressWarnings("unchecked")
    public static HashSet<Entity> allEntities(ClientLevel level, Operation<HashSet<Entity>> original) {
        if (!enabled || level == null) return original.call(level);
        EntityLookup<Entity> lookup = lookupOf(level);
        if (lookup == null) return original.call(level);
        MahouEntityLookupDuck d = (MahouEntityLookupDuck) (Object) lookup;
        int version = d.bons$mahoutsukaiCoo49Version();
        int ids = ID_CHANGES.get();
        HashSet<Entity> kept = d.bons$mahoutsukaiCoo49KeptSet();
        boolean odd = d.bons$mahoutsukaiCoo49Odd();
        boolean valid = !odd && kept != null && d.bons$mahoutsukaiCoo49KeptVersion() == version && d.bons$mahoutsukaiCoo49KeptIds() == ids;
        if (valid && !SHADOW) {
            reused++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (Mahou Tsukai's per-frame entity set is built again only when the client's entity list changes)", KEY);
            }
            return kept;
        }
        HashSet<Entity> fresh = original.call(level);
        built++;
        if (SHADOW && valid) compare(kept, fresh, level);
        // the counters are read before the build: an add during the build (none on this thread) leaves the set stale-marked.
        // A set is kept only while every entity ever offered to the lookup hashes and compares as Entity does (by id): then
        // its order and contents are a function of the lookup's insertion order and the ids alone
        d.bons$mahoutsukaiCoo49Keep(odd ? null : fresh, version, ids);
        return fresh;
    }

    /** Entity classes whose hashCode() and equals(Object) are Entity's own (the entity id). */
    static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("hashCode").getDeclaringClass() == Entity.class
                        && type.getMethod("equals", Object.class).getDeclaringClass() == Entity.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    /** MahouEntityLookupMixin (an add after oddSeen): does this entity's class hash and compare by the entity id? */
    public static boolean plain(Object entity) {
        return entity != null && PLAIN.get(entity.getClass());
    }

    private static void compare(HashSet<Entity> kept, HashSet<Entity> fresh, ClientLevel level) {
        SHADOW_CHECKS.incrementAndGet();
        boolean same = fresh != null && kept.size() == fresh.size();
        if (same) {
            Iterator<Entity> a = kept.iterator(), b = fresh.iterator();
            while (a.hasNext() && b.hasNext()) {
                if (a.next() != b.next()) {
                    same = false;
                    break;
                }
            }
            same &= !a.hasNext() && !b.hasNext();
        }
        if (!same) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the kept set of {} ({} entities) differs from a new build ({} entities)",
                    KEY, n, level, kept.size(), fresh == null ? -1 : fresh.size());
        }
    }

    /** The level's EntityLookup when its getter is the vanilla adapter over a vanilla lookup and both are as shipped, else null. */
    @SuppressWarnings("unchecked")
    static EntityLookup<Entity> lookupOf(ClientLevel level) {
        LevelEntityGetter<Entity> getter = ((MahouLevelEntitiesInvoker) level).bons$mahoutsukaiCoo49Entities();
        if (getter == null || getter.getClass() != LevelEntityGetterAdapter.class) return null;
        EntityLookup<?> lookup = ((MahouGetterAdapterAccessor) getter).bons$mahoutsukaiCoo49Lookup();
        if (lookup == null || lookup.getClass() != EntityLookup.class) return null;
        int c = clean;
        if (c == 0) {
            boolean ok = shipped(LevelEntityGetterAdapter.class) && shipped(EntityLookup.class);
            clean = c = ok ? 1 : 2;
            if (!ok) LOGGER.warn("Bons and Furious: {} stands aside: another mod's mixin changes the vanilla entity lookup or its getter (Mahou Tsukai builds its entity set as shipped)", KEY);
        }
        return c == 1 ? (EntityLookup<Entity>) lookup : null;
    }

    /** True when no method of the class was merged by a mixin other than ours. */
    static boolean shipped(Class<?> c) {
        try {
            for (Method m : c.getDeclaredMethods()) {
                MixinMerged merged = m.getAnnotation(MixinMerged.class);
                if (merged != null && !merged.mixin().startsWith("bons.furious.")) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
