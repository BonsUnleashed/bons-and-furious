package bons.furious.patch.datapack_selectors;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_selector_type_index (Minecraft 1.20.1 on Forge 47.4.16; server side, including the
 * integrated server). SRG member names.
 *
 * What it changes. An entity selector without a position box (`@e[type=#incendium:mobs_no_player, tag=!in.checked]`,
 * the form data-pack clock functions run several times a tick) is answered by EntitySelector.addEntities with
 * ServerLevel.getEntities(type test, predicate, list, limit): EntityLookup walks every loaded entity of the level in its
 * insertion order, asks the type test (EntityTypeTest.tryCast) first and hands each entity the test lets through to the
 * selector's predicate chain. For "@e" that chain is Entity::isAlive followed by one predicate per option, in the order
 * the options were written. Vanilla gives a selector a narrowing type test only for `type=<one id>` (the EntityType
 * itself); for a type tag or a negated type the test lets every entity through, so every loaded entity runs isAlive (on
 * a LivingEntity: a SynchedEntityData lookup) and the chain until the type predicate says no.
 *
 * When the FIRST option of an "@e" selector is type=#tag, type=!#tag or type=!id and no single entity type is set, that
 * option also becomes the scan's type test (TypeFirstOption), exactly the way vanilla uses the EntityType for
 * `type=<id>`, and the scan goes through the level's TypeIndex (EntityLookupMixin): only the entities of the types the
 * option accepts, plus those of classes with their own isAlive/getHealth/getType, are visited at all, in EntityLookup's
 * order. (Where the index is not reached, TypeFirstOption.tryCast filters each entity the same way.)
 *
 * Why the result is identical. Vanilla's chain for such a selector is isAlive && first && rest, evaluated left to right
 * with short circuits. For an entity where first is false, vanilla evaluates isAlive and first and rejects it; we skip
 * it. For an entity where first is true, the chain runs unchanged. The same entities therefore reach the list, in the
 * same EntityLookup order, and the limit (an ABORT once the list holds `limit` entries) cuts at the same entity; sort and
 * limit afterwards see the same list. This holds because:
 *  - the type predicates are pure and total: entity.getType().is(tag) != negated, Objects.equals(type,
 *    entity.getType()) != negated (EntitySelectorOptions); they depend only on the entity type and the tags bound at scan
 *    time, so grouping the entities by type cannot change which pass;
 *  - isAlive (and the type predicate) is skipped only for entity classes whose isAlive, getHealth and getType are
 *    vanilla's own (Entity.isAlive = !isRemoved(), a final field test; LivingEntity.isAlive = !isRemoved() &&
 *    getHealth() > 0, getHealth reads the synched health; getType returns the type field). Entities of any other class
 *    are always visited (ownChecks), so their own methods run exactly where vanilla runs them;
 *  - an option that comes after any other predicate is never used (the parser checks that its predicate is still the
 *    object "@e" set), so no other option's predicate is ever skipped.
 * The position-box path (dx/dy/dz, distance) is left alone: there Forge also visits multipart entities' parts.
 * Assumption shared with vanilla's own iteration: the chain does not add or remove entities of the scanned level.
 *
 * -Dbons_and_furious.selectorTypeIndex=false switches it off at run time (vanilla scan).
 * -Dbons_and_furious.selectorTypeIndex.shadow=true (verification runs only) runs every such scan the vanilla way and
 * checks that the filter would have let through every entity vanilla selected and that the index visits exactly the
 * entities the filter lets through, in EntityLookup's order (SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 mismatches
 * logged); SHADOW_SKIPPABLE counts the entities the index does not visit.
 */
public final class SelectorPrefilter {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean typeIndexEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.selectorTypeIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.selectorTypeIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    /** True for entity classes whose isAlive, getHealth or getType is not vanilla's: their entities are always let through. */
    private static final ClassValue<Boolean> OWN_CHECKS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Class<?> alive = type.getMethod("m_6084_").getDeclaringClass();
                boolean vanilla = (alive == Entity.class || alive == LivingEntity.class)
                        && type.getMethod("m_6095_").getDeclaringClass() == Entity.class;
                if (vanilla && LivingEntity.class.isAssignableFrom(type)) {
                    vanilla = type.getMethod("m_21223_").getDeclaringClass() == LivingEntity.class;
                }
                return !vanilla;
            } catch (Throwable t) {
                // 1.0.34: not only NoSuchMethodException. getMethod resolves the parameter types of every public method it
                // walks, and on a dedicated server a type that names a client-only class throws NoClassDefFoundError
                // (Small Ships' Paddleable.animatePaddle(..., ModelPart, ...)) or Forge's invalid-dist RuntimeException:
                // such a class is always visited, exactly as vanilla, instead of the error stopping the server
                return true;
            }
        }
    };

    private SelectorPrefilter() {
    }

    public static boolean ownChecks(Class<?> entityClass) {
        return OWN_CHECKS.get(entityClass);
    }

    /** EntitySelectorParser.getSelector: give the new selector its filter when the parse found a usable first option. */
    public static EntitySelector attach(EntitySelector selector, EntityTypeTest<Entity, Entity> filter, EntityType<?> singleType) {
        if (filter != null && singleType == null && selector instanceof PrefilteredSelector holder) {
            holder.bons$setScanPrefilter(filter);
        }
        return selector;
    }

    /** EntitySelector.addEntities: the whole-level ServerLevel.getEntities call (m_261178_). */
    public static void scan(EntityTypeTest<Entity, Entity> filter, ServerLevel level, EntityTypeTest<?, ?> test, Predicate<?> predicate,
                            List<?> out, int limit, Operation<Void> original) {
        if (!(filter instanceof TypeFirstOption) || !typeIndexEnabled) {
            original.call(level, test, predicate, out, limit);
            return;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_selector_type_index applies (entity selectors whose first option is a type tag or a negated type only visit the entities of the types it accepts){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            shadow(filter, level, test, predicate, out, limit, original);
            return;
        }
        original.call(level, filter, predicate, out, limit);
    }

    private static void shadow(EntityTypeTest<Entity, Entity> filter, ServerLevel level, EntityTypeTest<?, ?> test, Predicate<?> predicate,
                               List<?> out, int limit, Operation<Void> original) {
        int before = out.size();
        original.call(level, test, predicate, out, limit);
        List<Object> selected = new ArrayList<>(out.subList(before, out.size()));
        for (Object o : selected) {
            SHADOW_CHECKS.incrementAndGet();
            if (filter.m_141992_((Entity) o) == null) mismatch("the filter would have skipped " + o + ", which the selector selected");
        }
        List<Object> passing = new ArrayList<>();
        long skippable = 0;
        for (Entity e : level.m_8583_()) {
            if (filter.m_141992_(e) == null) skippable++;
            else passing.add(e);
        }
        SHADOW_SKIPPABLE.addAndGet(skippable);
        if (filter instanceof TypeFirstOption type) {
            IndexedLookup lookup = lookupOf(level);
            if (lookup != null) {
                List<Object> visited = new ArrayList<>();
                lookup.bons$visitIndexed(type, e -> {
                    visited.add(e);
                    return AbortableIterationConsumer.Continuation.CONTINUE;
                });
                SHADOW_CHECKS.incrementAndGet();
                if (!sameIdentityOrder(visited, passing)) {
                    mismatch("the type index visits " + visited.size() + " entities where the filter passes " + passing.size() + " (or in another order)");
                }
            }
        }
    }

    private static void mismatch(String what) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: selector filter shadow mismatch #{}: {}", m, what);
    }

    private static boolean sameIdentityOrder(List<Object> a, List<Object> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private static final Map<Class<?>, Field[]> LOOKUP_FIELDS = new IdentityHashMap<>();

    /** Shadow mode only: the level's EntityLookup (ServerLevel.entityManager.visibleEntityStorage), by reflection. */
    private static synchronized IndexedLookup lookupOf(ServerLevel level) {
        try {
            Field[] f = LOOKUP_FIELDS.get(ServerLevel.class);
            if (f == null) {
                Field manager = ServerLevel.class.getDeclaredField("f_143244_");
                manager.setAccessible(true);
                Field storage = manager.getType().getDeclaredField("f_157494_");
                storage.setAccessible(true);
                f = new Field[]{manager, storage};
                LOOKUP_FIELDS.put(ServerLevel.class, f);
            }
            Object lookup = f[1].get(f[0].get(level));
            return lookup instanceof IndexedLookup indexed ? indexed : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * A scan type test built around the selector's first option: lets an entity through when that option accepts it, or
     * when its class has its own isAlive/getHealth/getType. Base class Entity, as vanilla's ANY test. (TypeFirstOption's base.)
     */
    public static class FirstOption implements EntityTypeTest<Entity, Entity> {
        final Predicate<Entity> first;

        public FirstOption(Predicate<Entity> first) {
            this.first = first;
        }

        @Override
        public Entity m_141992_(Entity entity) {
            return this.first.test(entity) || OWN_CHECKS.get(entity.getClass()) ? entity : null;
        }

        @Override
        public Class<? extends Entity> m_142225_() {
            return Entity.class;
        }
    }

    /**
     * The scan's type test for type=#tag, type=!#tag and type=!id. tryCast is FirstOption's; matchesType states the same
     * test for a whole type, the way the option's own predicate states it for an entity (EntitySelectorOptions:
     * entity.getType().is(tag) != negated, Objects.equals(type, entity.getType()) != negated).
     */
    public static final class TypeFirstOption extends FirstOption {
        private final TagKey<EntityType<?>> tag;
        private final EntityType<?> single;
        private final boolean negated;

        public TypeFirstOption(Predicate<Entity> first, TagKey<EntityType<?>> tag, EntityType<?> single, boolean negated) {
            super(first);
            this.tag = tag;
            this.single = single;
            this.negated = negated;
        }

        public boolean matchesType(EntityType<?> type) {
            return (this.tag != null ? type.m_204039_(this.tag) : Objects.equals(this.single, type)) != this.negated;
        }
    }
}
